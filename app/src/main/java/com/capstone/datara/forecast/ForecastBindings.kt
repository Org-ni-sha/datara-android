package com.capstone.datara.forecast

import android.content.Context
import android.util.Log
import com.capstone.datara.data.local.dao.ForecastDao
import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.local.dao.UserModelStateDao
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.UserModelState
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.QuotaCycleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** [ForecastStore] over Room: `usage_samples`, `user_model_state` and `forecasts`. */
class RoomForecastStore @Inject constructor(
    private val telemetry: TelemetryDao,
    private val modelStates: UserModelStateDao,
    private val forecasts: ForecastDao
) : ForecastStore {
    override suspend fun samplesEndingIn(from: Long, until: Long): List<UsageSample> =
        telemetry.samplesEndingIn(from, until).map { it.toDomain() }

    override suspend fun modelState(userId: String): UserModelState? = modelStates.get(userId)
    override suspend fun saveModelState(state: UserModelState) = modelStates.upsert(state)
    override suspend fun forecastAt(cycleId: String, predictedAt: Long): Forecast? = forecasts.at(cycleId, predictedAt)
    override suspend fun insertForecast(forecast: Forecast): Boolean = forecasts.insert(forecast) != -1L
}

/** D3's cycle manager as the engine's cycle source. */
class QuotaCycleStateSource @Inject constructor(manager: QuotaCycleManager) : CycleStateSource {
    override val state: StateFlow<CycleState> = manager.state
}

/** The bundled model from `assets/models/global_xgboost.onnx`, loaded once. */
@Singleton
class AssetNextHourModelProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NextHourModelProvider {
    private val lock = Mutex()
    private var loaded: NextHourModel? = null
    private var failed = false

    override suspend fun model(): NextHourModel? = lock.withLock {
        if (loaded == null && !failed) {
            loaded = runCatching {
                OnnxNextHourModel(context.assets.open(OnnxNextHourModel.ASSET_PATH).use { it.readBytes() })
            }.onFailure {
                failed = true
                Log.e("ForecastModel", "Could not load ${OnnxNextHourModel.ASSET_PATH}", it)
            }.getOrNull()
        }
        loaded
    }
}
