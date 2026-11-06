package com.capstone.datara.forecast

import com.capstone.datara.data.local.entity.ActiveModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * The per-user local layer (ROADMAP Decision 2, option a): a correction on top of the Global
 * model, learned on the phone from each hour's prediction error. No on-device XGBoost training:
 * ONNX Runtime only runs the global trees.
 *
 * Since feature contract v4 the Global model predicts the *mean* bytes of an hour (Tweedie), so
 * the correction is multiplicative: a running ratio of actual to predicted usage, that is, how much
 * more or less than the global model this person uses. It keeps exponentially weighted means of
 * the actual and the globally predicted bytes, per 6-hour block of the local day (night, morning,
 * afternoon, evening) and overall. local = global × actual / predicted, using a block's own ratio
 * once it has [MIN_BLOCK_HOURS] hours, otherwise the overall one, clamped to
 * [MIN_FACTOR]..[MAX_FACTOR].
 *
 * Stored as JSON in `user_model_state.parameters`, together with the prediction made for the
 * current hour, which the next hourly run compares with what was measured. Parameters of an
 * earlier [VERSION] (the log-space correction of contracts v1 to v3) are discarded.
 */
@Serializable
data class LocalLayerParameters(
    val version: Int = VERSION,
    @SerialName("overall_actual") val overallActual: Double = 0.0,
    @SerialName("overall_predicted") val overallPredicted: Double = 0.0,
    @SerialName("overall_hours") val overallHours: Int = 0,
    @SerialName("block_actual") val blockActual: List<Double> = List(BLOCKS) { 0.0 },
    @SerialName("block_predicted") val blockPredicted: List<Double> = List(BLOCKS) { 0.0 },
    @SerialName("block_hours") val blockHours: List<Int> = List(BLOCKS) { 0 },
    /** The hour [pendingGlobalBytes] predicts; learned from once that hour has been measured. */
    @SerialName("pending_hour") val pendingHour: Long? = null,
    @SerialName("pending_local_hour") val pendingLocalHour: Int? = null,
    @SerialName("pending_global_bytes") val pendingGlobalBytes: Double? = null,
    /** Null while the local layer is not ready (its prediction was not made). */
    @SerialName("pending_local_bytes") val pendingLocalBytes: Double? = null,
    /** The last hour learned from, so an hour is never learned twice. */
    @SerialName("last_learned_hour") val lastLearnedHour: Long? = null
) {
    /** The local layer predicts once it has learned from this many hours (shadow until it switches). */
    val isReady: Boolean get() = overallHours >= MIN_HOURS_TO_PREDICT

    /** Multiplier on the global bytes for a forecast hour whose local hour of day is [localHour]. */
    fun factor(localHour: Int): Double {
        val b = block(localHour)
        return if (blockHours[b] >= MIN_BLOCK_HOURS) ratio(blockActual[b], blockPredicted[b])
        else ratio(overallActual, overallPredicted)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val VERSION = 2
        const val BLOCKS = 4
        const val ALPHA = 0.05
        const val MIN_BLOCK_HOURS = 12
        const val MIN_HOURS_TO_PREDICT = 24
        const val MIN_FACTOR = 0.2
        const val MAX_FACTOR = 5.0

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun block(localHour: Int): Int = localHour / 6

        fun ratio(actual: Double, predicted: Double): Double =
            if (predicted <= 0.0) 1.0 else (actual / predicted).coerceIn(MIN_FACTOR, MAX_FACTOR)

        fun fromJson(text: String?): LocalLayerParameters =
            if (text.isNullOrBlank() || text == "{}") LocalLayerParameters()
            else runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { it.version == VERSION } ?: LocalLayerParameters()
    }
}

/** Recent error of each model and the hours learned, as in `user_model_state`. */
data class LayerScores(val hoursSeen: Int, val globalRecentMae: Double?, val localRecentMae: Double?)

object LocalLayer {

    /** About a week of hourly errors: the window of "recent" MAE. */
    const val MAE_ALPHA = 1.0 / 168

    /**
     * Learns from the pending prediction once its hour has been measured ([actualBytes], null when
     * the hour was not observed: then the prediction is dropped unlearned).
     */
    fun learn(
        params: LocalLayerParameters,
        scores: LayerScores,
        actualBytes: Double?
    ): Pair<LocalLayerParameters, LayerScores> {
        val hour = params.pendingHour ?: return params to scores
        val global = params.pendingGlobalBytes
        val cleared = params.copy(pendingHour = null, pendingLocalHour = null, pendingGlobalBytes = null,
            pendingLocalBytes = null, lastLearnedHour = hour)
        if (actualBytes == null || global == null) return cleared to scores

        val b = LocalLayerParameters.block(params.pendingLocalHour ?: 0)
        val blockActual = params.blockActual.toMutableList()
        val blockPredicted = params.blockPredicted.toMutableList()
        val blockHours = params.blockHours.toMutableList()
        blockActual[b] = ewma(blockActual[b], actualBytes, blockHours[b])
        blockPredicted[b] = ewma(blockPredicted[b], global, blockHours[b])
        blockHours[b] += 1
        val learned = cleared.copy(
            overallActual = ewma(params.overallActual, actualBytes, params.overallHours),
            overallPredicted = ewma(params.overallPredicted, global, params.overallHours),
            overallHours = params.overallHours + 1,
            blockActual = blockActual,
            blockPredicted = blockPredicted,
            blockHours = blockHours
        )
        val localError = params.pendingLocalBytes?.let { abs(actualBytes - it) }
        val newScores = LayerScores(
            hoursSeen = scores.hoursSeen + 1,
            globalRecentMae = maeEwma(scores.globalRecentMae, abs(actualBytes - global)),
            localRecentMae = localError?.let { maeEwma(scores.localRecentMae, it) } ?: scores.localRecentMae
        )
        return learned to newScores
    }

    private fun ewma(mean: Double, value: Double, count: Int): Double =
        if (count == 0) value else mean + LocalLayerParameters.ALPHA * (value - mean)

    private fun maeEwma(mae: Double?, error: Double): Double =
        if (mae == null) error else mae + MAE_ALPHA * (error - mae)
}

/**
 * The reversible GLOBAL ↔ LOCAL switch (ROADMAP D6). LOCAL needs at least [MIN_HOURS_FOR_LOCAL]
 * learned hours (7 days of hourly comparisons) and a recent local MAE at least [ENTER_MARGIN]
 * below the global one; it goes back to GLOBAL as soon as local is no better. Between the two
 * thresholds the current mode is kept, so the switch does not flap.
 */
object ModelSwitch {
    const val MIN_HOURS_FOR_LOCAL = 7 * 24
    const val ENTER_MARGIN = 0.05

    fun decide(current: String, scores: LayerScores, localReady: Boolean): String {
        val global = scores.globalRecentMae
        val local = scores.localRecentMae
        if (!localReady || scores.hoursSeen < MIN_HOURS_FOR_LOCAL || global == null || local == null) return ActiveModel.GLOBAL
        return when (current) {
            ActiveModel.LOCAL -> if (local >= global) ActiveModel.GLOBAL else ActiveModel.LOCAL
            else -> if (local < global * (1 - ENTER_MARGIN)) ActiveModel.LOCAL else ActiveModel.GLOBAL
        }
    }
}
