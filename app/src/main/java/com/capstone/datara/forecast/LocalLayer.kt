package com.capstone.datara.forecast

import com.capstone.datara.data.local.entity.ActiveModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.ln1p

/**
 * The per-user local layer (ROADMAP Decision 2, option a): a residual correction on top of the
 * Global model, learned on the phone from each hour's prediction error. No on-device XGBoost
 * training: ONNX Runtime only runs the global trees.
 *
 * The correction is an exponentially weighted mean of the log residual
 * `log1p(actual) - global_log`, kept per 6-hour block of the local day (night, morning, afternoon,
 * evening) and overall. local_log = global_log + correction, where a block's own mean is used once
 * it has [MIN_BLOCK_HOURS] hours, otherwise the overall mean.
 *
 * Stored as JSON in `user_model_state.parameters`, together with the prediction made for the
 * current hour, which the next hourly run compares with what was measured.
 */
@Serializable
data class LocalLayerParameters(
    val version: Int = 1,
    @SerialName("overall_bias") val overallBias: Double = 0.0,
    @SerialName("overall_hours") val overallHours: Int = 0,
    @SerialName("block_bias") val blockBias: List<Double> = List(BLOCKS) { 0.0 },
    @SerialName("block_hours") val blockHours: List<Int> = List(BLOCKS) { 0 },
    /** The hour [pendingGlobalLog] predicts; learned from once that hour has been measured. */
    @SerialName("pending_hour") val pendingHour: Long? = null,
    @SerialName("pending_local_hour") val pendingLocalHour: Int? = null,
    @SerialName("pending_global_log") val pendingGlobalLog: Double? = null,
    /** Null while the local layer is not ready (its prediction was not made). */
    @SerialName("pending_local_log") val pendingLocalLog: Double? = null,
    /** The last hour learned from, so an hour is never learned twice. */
    @SerialName("last_learned_hour") val lastLearnedHour: Long? = null
) {
    /** The local layer predicts once it has learned from this many hours (shadow until it switches). */
    val isReady: Boolean get() = overallHours >= MIN_HOURS_TO_PREDICT

    /** Correction (log space) for a forecast hour whose local hour of day is [localHour]. */
    fun correction(localHour: Int): Double {
        val b = block(localHour)
        return if (blockHours[b] >= MIN_BLOCK_HOURS) blockBias[b] else overallBias
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val BLOCKS = 4
        const val ALPHA = 0.05
        const val MIN_BLOCK_HOURS = 12
        const val MIN_HOURS_TO_PREDICT = 24

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun block(localHour: Int): Int = localHour / 6

        fun fromJson(text: String?): LocalLayerParameters =
            if (text.isNullOrBlank() || text == "{}") LocalLayerParameters()
            else runCatching { json.decodeFromString(serializer(), text) }.getOrElse { LocalLayerParameters() }
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
        val globalLog = params.pendingGlobalLog
        val cleared = params.copy(pendingHour = null, pendingLocalHour = null, pendingGlobalLog = null,
            pendingLocalLog = null, lastLearnedHour = hour)
        if (actualBytes == null || globalLog == null) return cleared to scores

        val residual = ln1p(actualBytes) - globalLog
        val b = LocalLayerParameters.block(params.pendingLocalHour ?: 0)
        val blockBias = params.blockBias.toMutableList()
        val blockHours = params.blockHours.toMutableList()
        blockBias[b] = ewma(blockBias[b], residual, blockHours[b])
        blockHours[b] += 1
        val learned = cleared.copy(
            overallBias = ewma(params.overallBias, residual, params.overallHours),
            overallHours = params.overallHours + 1,
            blockBias = blockBias,
            blockHours = blockHours
        )
        val globalError = abs(actualBytes - PetProjector.predictedBytes(globalLog))
        val localError = params.pendingLocalLog?.let { abs(actualBytes - PetProjector.predictedBytes(it)) }
        val newScores = LayerScores(
            hoursSeen = scores.hoursSeen + 1,
            globalRecentMae = maeEwma(scores.globalRecentMae, globalError),
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
