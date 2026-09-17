# DATAra — End-to-End Capstone Master Roadmap & Build Order

> **Capstone Project:** Probable Exhaustion Time (PET) Prediction for Prepaid Mobile Data  
> **Institution:** University of Science and Technology of Southern Philippines (USTP) – Cagayan de Oro City  
> **Scope:** Full Capstone Ecosystem — ML Training Pipeline (`datara-ml`), Dataset Harvesting (`data-harvester`), Backend (`Supabase`), and Android Client (`datara-android`).

---

## 1. System Ecosystem & Architecture

The project consists of three interconnected deliverables supporting the dual-layer XGBoost prediction architecture:

```
┌───────────────────────────┐      ┌───────────────────────────┐
│     data-harvester        │      │        datara-ml          │
│ (Standalone Android App)  │      │   (Python ML Pipeline)    │
│  • Pilot usage collection │      │  • Cleaning & Preprocess  │
│  • Pseudonymized telemetry│─────►│  • XGBoost Training       │
│  • Ground truth dataset   │ CSV  │  • Metric Tuning (MAE/R²) │
└───────────────────────────┘      │  • ONNX Export (.onnx)    │
                                   └─────────────┬─────────────┘
                                                 │
                    global_xgboost.onnx          │
       ┌─────────────────────────────────────────┴──┐
       │ v1: bundled in app/src/main/assets/models/ │
       │ v2+: uploaded to Supabase Storage,         │
       │      downloaded by client for model updates│
       └─────────────────────────┬──────────────────┘
                                 ▼
┌───────────────────────────┐      ┌───────────────────────────┐
│      Supabase Cloud       │◄────►│      datara-android       │
│ • Postgres global schema  │      │   (Production Client)     │
│ • Auth & User profiles    │      │  • Live Telemetry Engine  │
│ • Usage log sync          │      │  • Global ONNX Inference  │
│ • Model file storage      │      │  • Local Personalization  │
└───────────────────────────┘      │  • PET Hero Dashboard     │
                                   └───────────────────────────┘
```

**Critical path (the ordering that actually constrains the schedule):**

```
A2 (ethics) → A3 (pilot) → A4 (export) → B1 → B2 → B3 → B4 → B5 → D6 → E3
```

Everything in Track D except D6 can proceed in parallel with Tracks A and B. **D6 cannot start until B5 delivers `global_xgboost.onnx`**, and B5 cannot start until the pilot data exists. Each phase below carries a *Blocked by* tag where it depends on another track.

---

## 2. Open Decisions — resolve before building the affected phase

These two items are specified in ways that cannot be built as written. Both change what gets claimed in the manuscript, so they are decisions, not implementation details.

### ⚠️ DECISION 1 — Durable polling interval (affects D4, and the thesis text)

The roadmap previously specified a **10-minute WorkManager worker**. WorkManager's `PeriodicWorkRequest` enforces a hard floor of **15 minutes** (`MIN_PERIODIC_INTERVAL_MILLIS`); a shorter interval is silently clamped, so the app would not do what the manuscript describes. Pick one:

| Option | Trade-off |
|---|---|
| **(a) 15-minute periodic worker** | Simplest, uses WorkManager as designed, survives process death. Requires updating the interval everywhere in the manuscript. |
| **(b) Self-rescheduling `OneTimeWorkRequest` chain, 10-min `initialDelay`** | Keeps the 10-minute figure. Loses periodic-work guarantees and drifts under Doze/battery optimization. |
| **(c) Do the `NetworkStatsManager` read inside the 30-sec foreground service** | Keeps exact 10-minute cadence while the service is alive; drops WorkManager from D4 entirely. Nothing runs if the service is killed. |

D4 below is written against **(a) 15 minutes** as the provisional default. Change it if you pick otherwise — and note the interval is also recorded in [`AGENTS.md`](AGENTS.md).

### ⚠️ DECISION 2 — What "Local model" actually means (affects D6, E3, and Chapter 3)

The roadmap previously specified a local XGBoost model "retrained or fine-tuned on-device." **ONNX Runtime performs inference only** for tree ensembles — there is no on-device XGBoost training or fine-tuning path, so D6 as originally written is not buildable. Separately, ~7 days of a single user's telemetry is far too little to fit an XGBoost ensemble without severe overfitting.

Keep this distinction explicit in all documentation: **on-device _inference_ is non-negotiable; on-device _training_ was an assumption, not a capability.**

| Option | Trade-off |
|---|---|
| **(a) Residual correction on top of the global model** *(recommended)* | Global ONNX output + a small per-user correction (running bias plus an online linear fit on a few features), trained in plain Kotlin. No new dependency, fully offline, genuinely personalized, easy to defend to a panel. Less "a second XGBoost model" than the original framing. |
| **(b) Cloud retraining, on-device inference** | Per-user model retrained in `datara-ml`, exported to ONNX, stored in Supabase Storage, downloaded by the client. True per-user XGBoost. Does **not** violate the no-`/predict` rule (inference stays local) but gives up offline-only for the retraining step. |
| **(c) On-device lightweight learner** | A small incremental regressor (SGD/ridge) trained locally that replaces the global model once it outperforms it. Fully on-device training, but no longer XGBoost at the local layer. |

D6 below is written against **(a)**. E3's comparison is valid under any option.

---

## 3. Cross-Ecosystem Roadmap (By Track & Phase)

### Track A: Dataset Harvesting & Preprocessing (`data-harvester`)
*Goal: Acquire realistic, panel-defensible Philippine prepaid mobile usage data.*

- [x] **Phase A1 — Harvester Baseline:** Standalone logger capturing mobile Rx/Tx bytes, timestamps, signal strength, and carrier info.
- [ ] **Phase A2 — Research Ethics & Informed Consent** *(must complete before any respondent data is collected)*
  - Written informed consent form: what is collected, retention period, withdrawal process, contact person.
  - Data-handling statement aligned with the project's RA 10173 claims (storage location, who has access, deletion timeline).
  - Confirm whether USTP requires ethics-committee or adviser sign-off for human-subject data collection, and obtain it.
  - Keep signed consent records as a defense appendix.
- [ ] **Phase A3 — Pilot Data Gathering:** Run pilot collection across volunteer respondents (prepaid users on Smart, Globe, DITO).
  - **Sizing — decide before launch:** target respondent count, and days of collection per respondent. Note the downstream floor: D6's local-model switch needs **≥ 7 days per user**, so 14–30 days per respondent is the realistic minimum.
  - **Measurement-validity check (do not skip):** at fixed intervals, record the carrier-reported balance (`*143#` / carrier app) alongside the app's measured consumption for the same window. Device-side byte counters do **not** equal carrier accounting — zero-rated apps, promo-specific inclusions, header overhead, and carrier rounding all cause drift. Log the observed drift; it becomes the error floor for every PET claim and the panel will ask about it.
  - Record device model, Android version, and carrier per respondent so B1 can check for device-specific measurement bias.
- [ ] **Phase A4 — Dataset Anonymization & Export:** Strip device/user identifiers so the exported dataset is genuinely anonymized (not merely pseudonymized), and format raw logs into standardized CSV (`usage_date`, `time_interval`, `consumed_mb`, `signal_strength`, `carrier`, `session_duration`).

---

### Track B: Machine Learning Pipeline (`datara-ml`)
*Goal: Train, optimize, validate, and export the baseline Global XGBoost model to ONNX.*

*Blocked by: A4.*

- [ ] **Phase B1 — Exploratory Data Analysis (EDA) & Cleaning:**
  - Inspect distributions, handle missing intervals, detect spikes/outliers.
  - Analyze diurnal (day/night) usage patterns and weekend vs. weekday variations.
  - Check for device- or carrier-specific measurement bias using the metadata from A3.
- [ ] **Phase B2 — Feature Engineering:**
  - `rolling_consumption_rate`: 1-hour, 3-hour, and 24-hour moving averages.
    - **Resolution constraint:** `NetworkStatsManager` is coarsely bucketed (roughly 2-hour granularity for detailed queries), so on-device features are running totals, not true 10/15-minute resolution. Engineer features the client can actually reproduce at inference time — a feature the app cannot compute is useless no matter how well it trains.
  - `time_features`: hour of day (cyclical sine/cosine), day of week, is_weekend.
  - `network_features`: signal strength (dBm/bars), network type (LTE/5G).
  - Target variable definition: Exhaustion timeframe / Consumption rate to compute PET.
- [ ] **Phase B3 — Global XGBoost Model Training & Hyperparameter Tuning:**
  - Algorithm: `XGBRegressor` / `XGBoost`.
  - Objective: Minimize exhaustion error margin across generalized prepaid users.
  - Hyperparameter optimization via Optuna or GridSearchCV (`learning_rate`, `max_depth`, `n_estimators`, `subsample`, `reg_lambda`).
  - Split by **respondent**, not by row — random row splits leak the same user into train and test and will inflate every metric.
- [ ] **Phase B4 — Model Evaluation & Thesis Metrics:**
  - Compute panel-required evaluation metrics:
    - **MAE** (Mean Absolute Error) in minutes/hours.
    - **RMSE** (Root Mean Squared Error).
    - **R²** (Coefficient of Determination).
  - **Acceptance target — decide before B3 runs, not after seeing results:** state the MAE the model must beat to be considered successful (e.g. "MAE ≤ 2 hours on a 24-hour prediction horizon") and the naive baseline it must outperform (e.g. constant average consumption rate). Committing to this in advance is the difference between an evaluation and a rationalization, and panels probe for it.
  - Generate feature importance plots (SHAP values, gain) for thesis documentation.
- [ ] **Phase B5 — Model Export to ONNX (`.onnx`):**
  - Convert trained XGBoost model using `onnxmltools` / `skl2onnx`.
  - Validate inference parity between Python XGBoost and ONNX Runtime.
  - Export final asset: `global_xgboost.onnx`. Record the exact input tensor shape, feature order, and dtype — D6 must reproduce them precisely.

---

### Track C: Backend & Cloud Infrastructure (`Supabase`)
*Goal: Centralized authentication, synced Postgres schema, and backup logging.*

- [x] **Phase C1 — Auth Setup:** Supabase Auth with Email/Password.
- [ ] **Phase C2 — Database Schema Definition:** *(SQL written in [`supabase/`](supabase/); tick this once it is applied to the live project and RLS is verified with two accounts.)*
  - Tables: `users`, `devices`, `providers`, `promos`, `data_usage`, `notifications`, `associations`.
  - Enforce Row Level Security (RLS) policies per `user_id`. No table ships without its policy.
  - Align schema types 1:1 with Android Room entities. Convention: Postgres is `snake_case` plural (`data_usage`), Room entities are `PascalCase` singular (`DataUsage`) — the mapping is naming-only, types must match exactly.
- [ ] **Phase C3 — Telemetry Sync:**
  - Batched background sync of usage records from the Android client.
  - **Implementation note:** this is a batched Postgrest insert from `supabase-kt`, not a custom HTTP endpoint. Only introduce a Supabase Edge Function if server-side validation or aggregation is genuinely needed — otherwise there is no endpoint to build.
  - Records are scoped to `user_id` under RLS, which makes synced data **pseudonymized, not anonymized**. Use the accurate term in the manuscript; overstating this is an easy point to be challenged on. (Only the A4 CSV export is anonymized.)

---

### Track D: Android Client App (`datara-android`)
*Goal: User-facing application with real-time telemetry, dual-layer on-device inference, and PET dashboard.*

#### Phase D1: Authentication & App Shell *(Status: Completed / Minor Polish)*
- [x] Supabase Auth (`auth-kt`) with Ktor client.
- [x] Dark navy UI (`#0C101A` / `#141B2B`) for `LoginScreen` and `RegisterScreen`.
- [x] `AuthRepository`, `AuthViewModel`, and Hilt injection.
- [ ] Session auto-login persistence on launch (`sessionStatus` check in `MainActivity`).
- [ ] Initial user record creation in Supabase on sign-up. *(Handled server-side by the `on_auth_user_created` trigger in C2 — the client only needs to write `name` after sign-up.)*

#### Phase D2: Room Local Database & Hardware Detection
- [ ] Add Room dependencies and KSP compiler (`ksp`, never `kapt` — matches the existing Hilt setup).
- [ ] Create 7 Room entities mirroring Supabase schema (`User`, `Device`, `Provider`, `Promo`, `DataUsage`, `Notification`, `Association`). *(Coordinate with C2 so the two do not drift.)*
- [ ] Implement `DataraDatabase` and DAOs.
- [ ] Auto-detect SIM/Carrier via `TelephonyManager` (*strictly no SMS reading, no manual carrier entry*).
  - Try `getSimOperatorName()` / `getSimOperator()` first — these need **no permission**. Only request `READ_PHONE_STATE` if a specific value genuinely requires it; it is a dangerous permission and needs a justification you can defend.
- [ ] Extract hardware metadata (`Build.MODEL`, `Build.VERSION.SDK_INT`).

#### Phase D3: Active Promo & Data Baseline Setup
- [ ] Prepaid promo catalog filtered by detected carrier (Smart, Globe, DITO).
- [ ] Custom manual promo entry dialog (`data_amount_gb`, `validity_days`, `expiry_date`).
- [ ] Calculate baseline remaining data (`data_remaining_gb`) as promo allowance minus measured consumption.
  - **Known limitation to surface in the UI and the manuscript:** this is an *estimate* derived from device-side measurement, not the carrier's true balance (see A3's validity check). Consider letting the user correct the baseline against their carrier-reported balance, which both improves accuracy and is an honest answer to the panel's inevitable question.

#### Phase D4: Dual Background Telemetry Harvester
*See ⚠️ DECISION 1 — written below against the 15-minute option.*

- [ ] **Permissions — three different mechanisms, do not treat them as one list:**
  - `PACKAGE_USAGE_STATS` — **special access, not a runtime permission.** There is no permission dialog. Send the user to `Settings.ACTION_USAGE_ACCESS_SETTINGS` and verify the grant via `AppOpsManager` (`unsafeCheckOpNoThrow`). Needs its own onboarding screen explaining why. This is the most common first-run failure for `NetworkStatsManager` apps.
  - `POST_NOTIFICATIONS` — **runtime permission on Android 13+.** Required for the foreground-service notification here and for all of D8. Request it before starting the service.
  - `FOREGROUND_SERVICE` — normal install-time permission, never requested at runtime. With `targetSdk 37` you additionally need a **typed** service: the `FOREGROUND_SERVICE_DATA_SYNC` permission (or `_SPECIAL_USE` with a written justification) plus `android:foregroundServiceType` on the `<service>` element. Untyped foreground services are rejected at this target level.
  - `READ_PHONE_STATE` — runtime permission; only if D2 proves it is actually needed.
- [ ] **30-Second Foreground Service:**
  - Measure real-time delta and instantaneous speed (KB/s, MB/s) using `TrafficStats`.
  - **Handle reboots:** `TrafficStats` counters are device-wide and reset to zero on reboot. Persist the last-seen counter and, when the new reading is lower than the stored one, treat it as a reboot and re-baseline instead of recording a large negative delta.
  - Persistent notification copy should explain the tracking honestly (users see it constantly, and the panel will too).
- [ ] **15-Minute WorkManager Worker** *(interval per DECISION 1)*:
  - Measure durable bucketed cellular data using `NetworkStatsManager`.
  - Calculate remaining data balance against active promo.
  - Reconcile against the foreground service's `TrafficStats` figures; `NetworkStatsManager` is the source of truth for durable totals.
- [ ] **Signal Strength Monitor:**
  - `TelephonyCallback` on API 31+, `PhoneStateListener` below it (deprecated at 31). With `minSdk 26` **both** paths are required — pick by `Build.VERSION.SDK_INT`.
- [ ] Persist snapshots to local Room database and queue for cloud sync. *(Blocked by: D2, C3.)*
- [ ] Verify behavior under Doze and battery optimization on a real device, not just the emulator.

#### Phase D5: Core Dashboard & Floating Navigation
- [ ] Fixed floating pill bottom nav (`Home`, `History`, `Settings`).
- [ ] Replace the currently-empty `"dashboard"` composable in `NavGraph.kt` — post-login navigation lands on a blank screen until this phase ships.
- [ ] **PET Hero Card (Centerpiece):** Prominent hours/days remaining countdown and predicted exhaustion timestamp.
  - Define the pre-prediction state: what the card shows before D6 exists, and before a new user has enough history for any prediction at all.
- [ ] **Data Balance Gauge:** Remaining vs. consumed progress bar with status colors (`DataraNeonGreen`, `DataraPrimaryBlue`, `DataraError`).
- [ ] **Live Network Strip:** Carrier badge, live speed, and signal bars.
- [ ] **Active Promo Card:** Plan name and expiration timer.

#### Phase D6: On-Device Machine Learning & Dual-Model Execution
*Blocked by: B5 (needs `global_xgboost.onnx`), D2, D4. See ⚠️ DECISION 2 — written below against the residual-correction option.*

- [ ] Add `com.microsoft.onnxruntime:onnxruntime-android`.
- [ ] Bundle `global_xgboost.onnx` (from B5) in `app/src/main/assets/models/`.
- [ ] Build on-device Feature Extractor from Room `DataUsage` entries.
  - Must reproduce B2's feature order, shapes, and dtypes **exactly**. Add a parity test against a known input/output pair captured from the Python pipeline — silent feature-order mismatch produces plausible-looking wrong predictions, which is the worst possible failure mode for a defense demo.
- [ ] **Dual-Model Conditional Switch:**
  - `ModelType.GLOBAL`: baseline pre-trained ONNX model, used for new users.
  - `ModelType.LOCAL`: global ONNX output plus a per-user residual correction, activated once the user has sufficient telemetry (≥ 7 days).
  - The switch is conditional and reversible — never hardcode one mode.
  - *Strict rule:* all **inference** is on-device via ONNX Runtime. No live `/predict` server call, ever.
- [ ] Output predicted PET timestamp and expose via `StateFlow` to the UI.
- [ ] Log each prediction with its inputs and the eventual actual exhaustion time — E3 cannot be done retroactively without this.

#### Phase D7: Usage History & Analytics (Vico Charts)
- [ ] Add Vico Compose charting library.
- [ ] Build `HistoryScreen`:
  - Time-series charts for hourly and daily data usage.
  - Peak usage hours breakdown.
  - Average daily consumption rate.
  - Filters: 24h, 7 days, full promo cycle.

#### Phase D8: Proactive Alerts & Notification System
*Blocked by: D4 (`POST_NOTIFICATIONS`), D6 (PET values to alert on).*

- [ ] High-priority notification channels for data exhaustion warnings.
- [ ] Triggers:
  - Critical PET window (e.g. predicted exhaustion in 4 hours).
  - Depletion milestones at **50%, 80%, and 90%** of promo data. (A 100% alert fires when the data is already gone — that is a post-mortem, not a warning.)
- [ ] Rate-limit alerts so a fluctuating prediction cannot notify repeatedly for the same threshold.
- [ ] In-app notification center and Room `NotificationEntity` history.

#### Phase D9: Profile, Settings & Developer ML Diagnostics Screen
- [ ] Profile management (name, optional secondary phone number).
- [ ] Settings (notification toggles, manual cloud sync, sign out).
  - The app is **dark-only** by decision (see [`AGENTS.md`](AGENTS.md)) — no theme toggle here unless that decision is revisited.
- [ ] **Developer ML Diagnostics Screen (Hidden):**
  - Gated behind secret gesture (e.g. 5 taps on app version text).
  - Displays: active model mode (Global vs. Local), MAE, RMSE, R², input tensors, inference latency.

---

### Track E: Integration, Evaluation & Thesis Defense Deliverables
*Goal: Academic validation and presentation readiness for the USTP BSIT panel.*

- [ ] **Phase E1 — Offline & Connectivity-Loss Resilience Testing.** Two distinct scenarios — the old single "airplane mode" test was incoherent, since airplane mode disables cellular and there is consequently no mobile data to track:
  - **No-network inference:** with the network fully off, confirm PET inference still runs on stored telemetry and the dashboard renders a prediction.
  - **Cellular up, Supabase unreachable:** with mobile data live but sync failing, confirm tracking continues, snapshots persist to Room, the sync queue accumulates, and it drains correctly once connectivity returns.
- [ ] **Phase E2 — Battery & Overhead Benchmarking:** Profile foreground service and background worker energy usage using Android Studio Energy Profiler. Report measured drain over a fixed window (e.g. %/hour over 8 hours) — panels ask for a number, not a claim of efficiency.
- [ ] **Phase E3 — Global vs. Local Accuracy Comparison:** Compare PET error (actual vs. predicted exhaustion) under Global vs. Local personalization, using the prediction log from D6.
  - **State the win condition in advance:** the improvement in MAE that makes the personalization layer worth having (e.g. "Local must reduce MAE by ≥ 15% over Global"). Report it honestly if the local layer does not help — a negative result with a clear explanation defends far better than a metric chosen after the fact.
  - Report measured-vs-carrier drift from A3 alongside these figures, so model error and measurement error are not conflated.
- [ ] **Phase E4 — Automated Test Coverage Audit** *(write tests continuously per phase; this phase is the final gap audit, not the first time tests are written — see the testing expectations in [`AGENTS.md`](AGENTS.md))*:
  - JUnit 4 tests for ViewModel state transitions, dual-model switch logic, feature-extraction parity, and Room queries.
  - Compose UI tests for Auth, Dashboard, and History flows.
- [ ] **Phase E5 — Capstone Manuscript & Presentation Assets:**
  - Export final evaluation tables (MAE, RMSE, R²).
  - Screen recordings and interactive demo scripts.
  - Build a signed demo APK and verify it on the actual defense device.

---

## 4. Contingencies

Worth deciding now, while there is still time to change course:

- **Pilot yields too little usable data for a defensible global model (A3 → B3).** Fallback: train on a reduced feature set, or ship a transparent heuristic baseline (rolling average consumption rate) as the prediction engine and reframe the XGBoost layer as evaluated-but-not-shipped. Decide the minimum viable dataset size at A3 so this trigger is objective rather than a judgment call under deadline.
- **Measured-vs-carrier drift is too large for credible PET claims (A3).** Fallback: reframe PET as a prediction over *device-measured* consumption, state the drift explicitly as a limitation, and add the user-correctable baseline from D3.
- **Track B slips and D6 cannot land.** D1–D5 and D7–D9 form a complete, demonstrable app without the ML layer. Keep the prediction interface behind an abstraction from D5 onward so a heuristic can be swapped in for the ONNX model without touching UI code.
- **Local personalization shows no improvement (E3).** This is a legitimate finding, not a failure — report it with the dual-layer architecture intact and the negative result explained.
