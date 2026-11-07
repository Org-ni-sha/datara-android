package com.capstone.datara.ui.diagnostics

import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.UserModelState
import com.capstone.datara.forecast.FeatureContract
import com.capstone.datara.forecast.ForecastStatus
import com.capstone.datara.forecast.InferenceTiming
import com.capstone.datara.forecast.LocalLayerParameters
import com.capstone.datara.forecast.ModelSwitch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** The bundled Global model as loaded by ONNX Runtime. */
data class ModelInfo(val version: String, val outputTransform: String, val outputScale: Double, val contractVersion: Int)

/** Everything the diagnostics screen reads, collected at [now]. Raw values; [DiagnosticsMapper] formats them. */
data class DiagnosticsSnapshot(
    val now: Long,
    val zone: ZoneId,
    val signedIn: Boolean,
    val modelState: UserModelState?,
    /** This device's forecasts, newest first (at most 24). */
    val forecasts: List<Forecast>,
    val model: ModelInfo?,
    val timing: InferenceTiming?,
    val forecastStatus: ForecastStatus,
    val lastSampleEnd: Long?,
    val lastReadingUntil: Long?,
    val samplesLast24h: Int,
    val measuredHoursLast24h: Int,
    val serviceRunning: Boolean
)

data class DiagnosticsRow(val label: String, val value: String, val warning: Boolean = false)
data class DiagnosticsSection(val title: String, val rows: List<DiagnosticsRow>)
data class ForecastTableRow(val hour: String, val active: String, val globalMb: String, val localMb: String, val remainingMb: String)

data class DiagnosticsUiState(
    val loading: Boolean = true,
    /** [ActiveModel.GLOBAL] or [ActiveModel.LOCAL], the model whose PET the user is shown. */
    val activeModel: String = ActiveModel.GLOBAL,
    /** One sentence on why that model is active (the D6 switch rule). */
    val reason: String = "",
    /** Hours learned out of [ModelSwitch.MIN_HOURS_FOR_LOCAL], 0..1. */
    val learnedProgress: Float = 0f,
    val learnedLabel: String = "",
    val sections: List<DiagnosticsSection> = emptyList(),
    val table: List<ForecastTableRow> = emptyList(),
    val updatedAt: String = ""
)

/** Pure snapshot → screen text, unit-tested. Never shows a user or device UUID. */
object DiagnosticsMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun map(s: DiagnosticsSnapshot): DiagnosticsUiState {
        val state = s.modelState
        val params = LocalLayerParameters.fromJson(state?.parameters)
        val hours = state?.hoursSeen ?: 0
        val needed = ModelSwitch.MIN_HOURS_FOR_LOCAL
        val active = state?.activeModel ?: ActiveModel.GLOBAL
        val latest = s.forecasts.firstOrNull()
        return DiagnosticsUiState(
            loading = false,
            activeModel = active,
            reason = reason(active, hours, state?.globalRecentMae, state?.localRecentMae, params.isReady, s.signedIn),
            learnedProgress = (hours.toFloat() / needed).coerceIn(0f, 1f),
            learnedLabel = "$hours of $needed hours learned",
            sections = listOf(
                switchSection(state, params, hours),
                latestSection(latest, s),
                inputsSection(latest),
                modelSection(s),
                telemetrySection(s)
            ),
            table = s.forecasts.take(24).map { f ->
                ForecastTableRow(time(f.predictedAt, s.zone, "MMM d HH:mm"), f.activeModel.uppercase(), mb(f.globalNextHourBytes),
                    f.localNextHourBytes?.let(::mb) ?: "—", mb(f.remainingBytes))
            },
            updatedAt = time(s.now, s.zone, "HH:mm:ss")
        )
    }

    fun reason(active: String, hours: Int, globalMae: Double?, localMae: Double?, localReady: Boolean, signedIn: Boolean): String {
        val needed = ModelSwitch.MIN_HOURS_FOR_LOCAL
        val margin = pct(ModelSwitch.ENTER_MARGIN)
        return when {
            !signedIn -> "Signed out: no model state to show."
            hours < needed -> "Global: the local layer has learned $hours of $needed hours (needs 7 days before it can take over)."
            !localReady -> "Global: the local layer has no usage ratios yet."
            globalMae == null || localMae == null -> "Global: no recent error for both models yet."
            active == ActiveModel.LOCAL ->
                "Local: its recent MAE ${mb(localMae)} MB is below Global's ${mb(globalMae)} MB; it stays while lower."
            else -> {
                val gain = if (globalMae > 0) 1 - localMae / globalMae else 0.0
                "Global: Local's recent MAE is ${relative(gain)} than Global's; switching needs at least $margin lower."
            }
        }
    }

    private fun switchSection(state: UserModelState?, params: LocalLayerParameters, hours: Int): DiagnosticsSection {
        val g = state?.globalRecentMae
        val l = state?.localRecentMae
        val gain = if (g != null && l != null && g > 0) relative(1 - l / g) else "—"
        return DiagnosticsSection("Model switch (Global vs Local)", listOf(
            DiagnosticsRow("Active model", (state?.activeModel ?: ActiveModel.GLOBAL).uppercase()),
            DiagnosticsRow("Hours learned", "$hours / ${ModelSwitch.MIN_HOURS_FOR_LOCAL}", hours < ModelSwitch.MIN_HOURS_FOR_LOCAL),
            DiagnosticsRow("Global recent MAE", g?.let { "${mb(it)} MB/h" } ?: "—"),
            DiagnosticsRow("Local recent MAE", l?.let { "${mb(it)} MB/h" } ?: "—"),
            DiagnosticsRow("Local vs Global", gain),
            DiagnosticsRow("Switch rule", "≥ ${ModelSwitch.MIN_HOURS_FOR_LOCAL} h and Local MAE ≥ ${pct(ModelSwitch.ENTER_MARGIN)} lower; back to Global when not lower"),
            DiagnosticsRow("Local layer ready", if (params.isReady) "Yes (${params.overallHours} h of ratios)" else "No (${params.overallHours} / ${LocalLayerParameters.MIN_HOURS_TO_PREDICT} h)", !params.isReady)
        ))
    }

    private fun latestSection(f: Forecast?, s: DiagnosticsSnapshot): DiagnosticsSection {
        val status = when (val st = s.forecastStatus) {
            is ForecastStatus.Ready -> "Ready"
            is ForecastStatus.Collecting -> "Collecting (${st.observedHours} / ${st.neededHours} measured hours)"
            is ForecastStatus.Unavailable -> "Unavailable: ${st.reason}"
            ForecastStatus.NoActiveCycle -> "No active quota"
            ForecastStatus.Pending -> "Not run yet"
        }
        if (f == null) return DiagnosticsSection("Latest forecast", listOf(
            DiagnosticsRow("Engine status", status, s.forecastStatus !is ForecastStatus.Ready),
            DiagnosticsRow("Forecast rows", "None logged yet", true)))
        return DiagnosticsSection("Latest forecast", listOf(
            DiagnosticsRow("Engine status", status, s.forecastStatus !is ForecastStatus.Ready),
            DiagnosticsRow("Predicted for hour", time(f.predictedAt, s.zone, "MMM d, HH:mm") + " (${ago(s.now - f.predictedAt)})"),
            DiagnosticsRow("Row active model", f.activeModel.uppercase()),
            DiagnosticsRow("Remaining quota", "${mb(f.remainingBytes)} MB"),
            DiagnosticsRow("Global next hour", "${mb(f.globalNextHourBytes)} MB"),
            DiagnosticsRow("Global reach time", reach(f.globalReachAt, s.zone)),
            DiagnosticsRow("Local next hour", f.localNextHourBytes?.let { "${mb(it)} MB" } ?: "— (local layer not ready)"),
            DiagnosticsRow("Local reach time", if (f.localNextHourBytes == null) "—" else reach(f.localReachAt, s.zone)),
            DiagnosticsRow("Model version", f.modelVersion),
            DiagnosticsRow("Contract version", contractOf(f.inputs)?.toString() ?: "—")
        ))
    }

    private fun inputsSection(f: Forecast?): DiagnosticsSection {
        val values = parseInputs(f?.inputs)
        return DiagnosticsSection("Input features (latest forecast)", FeatureContract.FEATURE_NAMES.map { name ->
            val v = values[name]
            DiagnosticsRow(name, when {
                f == null -> "—"
                v == null -> "missing (NaN)"
                else -> String.format(Locale.US, "%.4f", v)
            }, f != null && v == null)
        })
    }

    private fun modelSection(s: DiagnosticsSnapshot): DiagnosticsSection {
        val m = s.model
        val t = s.timing
        return DiagnosticsSection("Bundled model and inference", listOf(
            DiagnosticsRow("Model file", m?.version ?: "Could not load", m == null),
            DiagnosticsRow("Output transform", m?.let { "${it.outputTransform} × ${trim(it.outputScale)}" } ?: "—"),
            DiagnosticsRow("Model contract / app contract", "${m?.contractVersion ?: "—"} / ${FeatureContract.CONTRACT_VERSION}"),
            DiagnosticsRow("Runs on", "ONNX Runtime, on this phone (no network)"),
            DiagnosticsRow("One prediction", t?.let { "${trim(it.singleMicros / 1000.0)} ms" } ?: "Not measured since app start"),
            DiagnosticsRow("PET projection", t?.projectionMillis?.let { "$it ms for ${t.projectedHours} hourly predictions" } ?: "—"),
            DiagnosticsRow("Measured", t?.let { ago(s.now - it.measuredAt) } ?: "—")
        ))
    }

    private fun telemetrySection(s: DiagnosticsSnapshot): DiagnosticsSection = DiagnosticsSection("Telemetry health", listOf(
        DiagnosticsRow("Tracking service", if (s.serviceRunning) "Running" else "Stopped", !s.serviceRunning),
        DiagnosticsRow("Last 30 s sample", s.lastSampleEnd?.let { ago(s.now - it) } ?: "None", stale(s.lastSampleEnd, s.now, 2 * 60_000L)),
        DiagnosticsRow("Last 10 min NSM reading", s.lastReadingUntil?.let { ago(s.now - it) } ?: "None", stale(s.lastReadingUntil, s.now, 25 * 60_000L)),
        DiagnosticsRow("Samples, last 24 h", "${s.samplesLast24h} of ~2,880"),
        DiagnosticsRow("Measured hours, last 24 h", "${s.measuredHoursLast24h} / 24 (forecast needs ${FeatureContract.MIN_OBSERVED_LAST_24H})",
            s.measuredHoursLast24h < FeatureContract.MIN_OBSERVED_LAST_24H)
    ))

    /** Feature name → value from `forecasts.inputs`; missing (JSON null) features are absent. */
    fun parseInputs(inputs: String?): Map<String, Double> {
        val obj = inputs?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return emptyMap()
        return obj.mapNotNull { (k, v) -> if (v is JsonPrimitive && v !is JsonNull) v.doubleOrNull?.let { k to it } else null }.toMap()
    }

    private fun contractOf(inputs: String?): Int? = parseInputs(inputs)["contract_version"]?.toInt()

    fun mb(bytes: Long): String = mb(bytes.toDouble())
    fun mb(bytes: Double): String = String.format(Locale.US, "%,.2f", bytes / 1_000_000.0)

    fun ago(ms: Long): String {
        val m = ms / 60_000
        return when {
            ms < 0 -> "in the future"
            m < 1 -> "${ms / 1000} s ago"
            m < 60 -> "$m min ago"
            m < 48 * 60 -> "${m / 60} h ${m % 60} min ago"
            else -> "${m / (24 * 60)} days ago"
        }
    }

    private fun reach(at: Long?, zone: ZoneId) = at?.let { time(it, zone, "MMM d, HH:mm") } ?: "Beyond 30 days"
    private fun stale(at: Long?, now: Long, limit: Long) = at == null || now - at > limit
    private fun time(at: Long, zone: ZoneId, pattern: String) =
        DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(at).atZone(zone))
    private fun pct(x: Double) = "${trim(x * 100)}%"
    /** A Local-vs-Global gain (1 − local/global) as "12% lower" or "3% higher". */
    fun relative(gain: Double) = "${trim(abs(gain) * 100)}% " + if (gain >= 0) "lower" else "higher"
    private fun trim(x: Double) = String.format(Locale.US, "%.2f", x).trimEnd('0').trimEnd('.')
}
