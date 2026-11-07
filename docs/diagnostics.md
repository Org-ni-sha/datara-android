# ML diagnostics and Settings status (D9)

## Hidden diagnostics screen

**Opening it:** Settings → About DATAra → tap **Version** 7 times (`DIAGNOSTICS_TAPS`). The screen has no navigation-bar entry and no visible hint. Back returns to Settings. This keeps model metrics out of the main Settings menu (AGENTS.md scope rule).

It is read-only and shows real on-device data. It refreshes every 15 s and has a Refresh button.

| Section | Source |
|---|---|
| Active model and why | `user_model_state`: `active_model`, `hours_seen` vs `ModelSwitch.MIN_HOURS_FOR_LOCAL` (168), `global_recent_mae` / `local_recent_mae`, the 5% `ENTER_MARGIN`, and whether the local layer has learned ratios (`LocalLayerParameters.isReady`) |
| Latest forecast | newest `forecasts` row for this user and device: both models' next-hour bytes and reach times, remaining bytes, `model_version`, and `contract_version` from `inputs` |
| Input features | the 12 contract features from `forecasts.inputs`. Missing (NaN) features are shown in red |
| Bundled model and inference | `OnnxNextHourModel` metadata (`model_version`, `output_transform`, `output_scale`), plus `ForecastEngine.lastTiming`: one ONNX prediction (µs precision) and the whole PET projection |
| Telemetry health | whether the service is running, the last 30 s sample, the last 10 min NetworkStatsManager reading, samples in the last 24 h, and measured hours in the last 24 h (computed the same way the forecast computes them) |
| Last forecasts | up to 24 rows: hour, active model, Global and Local next-hour MB, remaining MB |

Notes:
- Latency is measured in this process only, so it shows "Not measured since app start" until the engine has run once. The projection is timed only when a new hour's row is written; when the row already exists the projection is skipped, so that cell shows "—".
- No user or device UUID is shown. `DiagnosticsMapperTest.noUuidIsEverShown` checks this.
- Code: `ui/diagnostics/` (`DiagnosticsMapper` is pure and unit-tested, `DiagnosticsRepository` reads Room and the engine, and `DiagnosticsScreen` draws the result).

## Settings status

- **Tracking status** shows real checks, refreshed on every resume: tracking service running (`TelemetryController.isRunning`), Usage Access, notifications and battery-optimization exemption (`TelemetryAccess`).
- **Cloud sync** says "Not synced yet". The simulated sync states and the Preview options menu were removed, since C3 is not built.
- Persisted preferences: the profile name (`public.users`) and the notification switches (`notification_preferences`, D8). No other Settings preference exists.
