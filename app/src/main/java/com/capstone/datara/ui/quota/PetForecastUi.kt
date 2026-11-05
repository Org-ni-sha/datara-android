package com.capstone.datara.ui.quota

import com.capstone.datara.forecast.FeatureContract
import com.capstone.datara.forecast.ForecastStatus
import kotlin.math.roundToLong

/** The live forecast text for a real, active cycle (D6). Samples never use this. */
data class PetForecastUi(
    /** Upper-case status label of the Home hero. */
    val label: String,
    val title: String,
    val body: String,
    /** The PET shown, or null (collecting, unavailable, beyond 30 days). */
    val reachAtMillis: Long? = null,
    val isAvailable: Boolean = false
) {
    companion object {
        /**
         * Text for [status] while [cycleId] is the active cycle. A forecast made for another cycle
         * (the previous one) is not shown.
         */
        fun from(status: ForecastStatus, cycleId: String, now: Long, formatDate: (Long) -> String): PetForecastUi = when {
            status is ForecastStatus.Ready && status.cycleId == cycleId -> ready(status, now, formatDate)
            status is ForecastStatus.Collecting -> PetForecastUi(
                "COLLECTING USAGE", "Collecting usage",
                "A forecast appears once ${status.neededHours} of the last 24 hours have measured usage " +
                    "(${status.observedHours} so far)."
            )
            status is ForecastStatus.Unavailable -> PetForecastUi("FORECAST UNAVAILABLE", "Forecast unavailable", status.reason)
            else -> PetForecastUi(
                "FORECASTING", "Preparing your forecast",
                "The forecast is calculated on this device from your measured usage, once an hour."
            )
        }

        private fun ready(status: ForecastStatus.Ready, now: Long, formatDate: (Long) -> String): PetForecastUi {
            val testModel = if (status.isPlaceholderModel) " Test model: not yet trained on study data." else ""
            val reach = status.reachAt ?: return PetForecastUi(
                "ESTIMATED TIME TO QUOTA", "Beyond 30 days",
                "At your recent pace you are not expected to reach this quota within 30 days.$testModel",
                isAvailable = true
            )
            return PetForecastUi(
                "ESTIMATED TIME TO QUOTA", relative(reach - now),
                "Estimated around ${formatDate(reach)}, from your measured usage. Your pace may change.$testModel",
                reachAtMillis = reach, isAvailable = true
            )
        }

        fun relative(millis: Long): String {
            val hour = FeatureContract.HOUR_MS
            return when {
                millis <= 0 -> "Any moment now"
                millis < hour -> "Less than an hour"
                millis < 48 * hour -> (millis.toDouble() / hour).roundToLong().let { if (it == 1L) "About 1 hour" else "About $it hours" }
                else -> "About ${(millis.toDouble() / (24 * hour)).roundToLong()} days"
            }
        }
    }
}
