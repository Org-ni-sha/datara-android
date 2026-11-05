# On-device forecast (D6)

DATAra predicts the next hour of mobile usage with the Global XGBoost model and projects it against the active cycle's remaining quota to get the PET (ROADMAP Decision 2). Everything runs on the phone with ONNX Runtime; there is no prediction server and no network call.

Code: `app/src/main/java/com/capstone/datara/forecast/`.

## The feature contract

The model's 12 inputs are fixed by **feature contract v1** in datara-ml (`docs/feature-contract.md`, reference implementation `datara_ml/features.py`):

| # | Feature | # | Feature |
|---|---|---|---|
| 0 | `hour_sin` | 6 | `log_avg_bytes_last_24h` |
| 1 | `hour_cos` | 7 | `log_bytes_same_hour_yesterday` |
| 2 | `day_of_week` (Mon = 0) | 8 | `log_bytes_same_hour_last_week` |
| 3 | `is_weekend` | 9 | `signal_dbm_last_hour` |
| 4 | `log_bytes_last_1h` | 10 | `radio_gen_last_hour` (2–5) |
| 5 | `log_avg_bytes_last_3h` | 11 | `cellular_share_last_hour` |

Input `features`, float32 `[1, 12]`; output `variable` = log1p(next-hour mobile bytes). Missing history is NaN, which XGBoost (and its ONNX export) treats as missing. Internet speed is not a feature: the harvester never recorded it.

| Class | Role |
|---|---|
| `FeatureContract` | names, order, constants (lookback 168 h, collecting threshold, 30-day horizon) |
| `HourlyUsage` | 30-second `usage_samples` → hourly stats, with B1's counting rule (`OK`/`LEGACY`, not `WIFI`/`NONE`) |
| `FeatureExtractor` | one feature row in contract order; time features in the phone's zone (participants: Asia/Manila, as in training) |
| `PetProjector` | hour-by-hour projection, predictions fed back as lags, linear interpolation inside the reaching hour, 30-day cap |
| `OnnxNextHourModel` | loads `assets/models/global_xgboost.onnx`, refuses a model whose `contract_version`, feature order or width differs |

`usage_samples` are used instead of `data_usage`: the 10-minute rows keep only the worst status and one network label, so the training data's per-reading rules can't be rebuilt from them. Samples are kept 14 days; the features read 8.

## The hourly job

`ForecastEngine.runIfDue()` runs after every 10-minute durable read in the telemetry service, and when the active cycle changes (it is started by `DataraApplication`). It does work once per hour, so there is no extra alarm or worker. Each run:

1. **Learns** from the hour it predicted last time, now measured: the log residual updates the local layer, and the absolute errors update each model's recent MAE.
2. **Collecting** if fewer than 6 of the last 24 hours have readings (the same rule the training set uses). No forecast is shown or logged.
3. **Predicts** the current hour with the Global model, plus the local correction once the local layer has 24 hours of learning (in shadow until it takes over).
4. **Switches** (`ModelSwitch`): LOCAL once it has learned 168 hours (7 days) and its recent MAE is at least 5% below global; back to GLOBAL as soon as it is no better. In between, the current mode stays, so it does not flap.
5. **Projects** the active cycle's remaining bytes (D3's `QuotaCycleManager`) with both models and logs one `forecasts` row per `(cycle_id, predicted_at)` hour: both next-hour predictions and PETs (null past 30 days), the active model, `model_version` and the feature values as `inputs`.
6. Saves `user_model_state` (only when it changed) and publishes `ForecastEngine.status`.

Inference runs on `Dispatchers.Default`. A projection is at most 720 model runs per model; tree inference takes microseconds, so a run costs milliseconds once per hour.

## The local layer

Decision 2's option (a): a residual correction on top of the global model, learned in plain Kotlin (`LocalLayer`). The correction is an exponentially weighted mean (α = 0.05) of `log1p(actual) − global_log`, per 6-hour block of the local day (night, morning, afternoon, evening) once a block has 12 hours, otherwise overall. It is stored as JSON in `user_model_state.parameters`, with the pending prediction for the current hour. Recent MAE is an exponentially weighted mean over about a week (α = 1/168), in bytes.

## What the user sees

`QuotaCycleViewModel.details` adds `PetForecastUi` to the real cycle's details while it is active. Home's hero and Quota Details show:

- **Estimated time to quota:** "About 12 hours" / "About 3 days" and the date.
- **Beyond 30 days** when the projection doesn't reach the quota within 30 days.
- **Collecting usage** with how many of the 6 needed hours are measured.
- **Forecast unavailable** if the model can't load.
- **Preparing your forecast** before the first run, or while the forecast still belongs to the previous cycle.

Forecasts from a model whose version starts with `dev-` add "Test model: not yet trained on study data."

## The bundled model

`v0-2026-11-06`, from datara-ml `train_v0.py`: an interim, untuned model trained on the harvester data collected so far (841 usable hours from 17 devices, 2026-10-28 to 11-05). With 5-fold cross-validation that holds out whole devices, its next-hour MAE is 65.0 MB, against 72.1 MB for "same as the last 24 h average" and 73.5 MB for "same as the last hour". Most devices had less than a week of data, so `log_bytes_same_hour_last_week` was missing in 96% of rows. The final model, trained on 14 days, replaces it as a file swap. Forecast rows record the version in `model_version`.

## Swapping in a trained model

The app has no model-specific code. For a model trained on contract v1 (see datara-ml `docs/feature-contract.md`, "Swapping in a trained model"):

1. In datara-ml, export it with `export_onnx(model, Path("artifacts/global_xgboost.onnx"), model_version="v0-…")`.
2. Regenerate the parity fixture against it: `python -m datara_ml.export_dev_model --fixture-only --model artifacts/global_xgboost.onnx --out artifacts`.
3. Copy `artifacts/global_xgboost.onnx` to `app/src/main/assets/models/global_xgboost.onnx` and `artifacts/golden_parity.json` to `app/src/test/resources/forecast/golden_parity.json`.
4. Run `gradlew test assembleDebug`. `FeatureParityTest` checks that the bundled model and fixture agree with the app.

A model for another contract version is refused at load ("Forecast unavailable"), never silently used.

## No third-party telemetry

The `onnxruntime-android` 1.31 AAR ships a Microsoft telemetry uploader (`ai.onnxruntime.TelemetryInitializer`) that starts with the app. DATAra removes its provider in `AndroidManifest.xml` (`tools:node="remove"`) and calls `OrtEnvironment.setTelemetry(false)` before creating a session, so the forecast sends nothing off the phone. Check the merged manifest still has no `TelemetryInitializer` after an ONNX Runtime upgrade.

## Tests

- `FeatureParityTest`: the golden fixture (features, ONNX output, PETs) against the bundled model, on the JVM ONNX Runtime.
- `FeatureExtractorTest`: contract rules, hourly aggregation, projection.
- `LocalLayerTest`: residual learning, JSON round trip, switch thresholds and hysteresis.
- `ForecastEngineTest`: the hourly job with in-memory storage — collecting, logging once per hour, beyond 30 days, learning without a cycle, shadow then LOCAL takeover, missing model.
- `PetForecastUiTest`: the wording and when a forecast is shown.

- `DeviceParityTest` (instrumented): the same fixture through the Android AAR on a phone. Verified on a POCO F3 (Android, arm64) on 2026-11-06.

Running `connectedDebugAndroidTest` **uninstalls the app afterwards**, wiping its local data (samples, cycles, sign-in). On a phone with real data, install the test APKs and use `adb shell am instrument -w -e class com.capstone.datara.forecast.DeviceParityTest com.capstone.datara.test/androidx.test.runner.AndroidJUnitRunner` instead.

Not covered: battery cost (E2).
