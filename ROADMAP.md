# DATAra — End-to-End Capstone Master Roadmap & Build Order

> **Original capstone framing:** Probable Exhaustion Time (PET) Prediction for Prepaid Mobile Data (manuscript alignment pending)
> **Institution:** University of Science and Technology of Southern Philippines (USTP) – Cagayan de Oro City  
> **Scope:** Full Capstone Ecosystem — ML Training Pipeline (`datara-ml`), Dataset Harvesting (`data-harvester`), Backend (`Supabase`), and Android Client (`datara-android`).

---

## Current direction and implementation status (2026-10-29)

User-defined usage-quota forecasting is the approved product direction. The user chooses an amount in MB or GB to track; PET means the estimated time when device-measured mobile consumption reaches that amount. It does not predict carrier balance depletion, promo expiry or loss of connectivity. Traffic covered by freebies or zero-rating still counts when recorded by the device; the app does not classify carrier billing buckets or reconcile a carrier balance. Quotas inform the user and never block or throttle traffic.

Authentication and the branded launch flow are implemented. Home, Create Quota, Quota Details, History, Notifications, Settings and Profile are implemented as UI previews using sample/session state. Telemetry (D4) and the Room database (D2) are built; quota persistence, inference, OS alert delivery and cloud sync remain pending. Completed UI checkboxes below do not mean their production integrations are complete.

The applied C2 schema still contains legacy promo tables. Quota schema design and new migrations are pending; this direction does not authorize deleting deployed tables. The manuscript, data labels and evaluation protocol must be aligned before research implementation proceeds. The original title above is historical, not a newly approved thesis title.

## 1. System Ecosystem & Architecture

The planned architecture connects three deliverables around a global XGBoost model and a local personalization layer. The diagram is a target architecture, not a list of implemented services:

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

The quota direction is approved. Polling and local personalization below remain open technical decisions; their provisional defaults are not implementation commitments.

### ⚠️ DECISION 1 — Durable polling interval (affects D4, and the thesis text)

The roadmap previously specified a **10-minute WorkManager worker**. WorkManager's `PeriodicWorkRequest` enforces a hard floor of **15 minutes** (`MIN_PERIODIC_INTERVAL_MILLIS`); a shorter interval is silently clamped, so the app would not do what the manuscript describes. Pick one:

| Option | Trade-off |
|---|---|
| **(a) 15-minute periodic worker** | Simplest, uses WorkManager as designed, survives process death. Requires updating the interval everywhere in the manuscript. |
| **(b) Self-rescheduling `OneTimeWorkRequest` chain, 10-min `initialDelay`** | Keeps the 10-minute figure. Loses periodic-work guarantees and drifts under Doze/battery optimization. |
| **(c) Do the `NetworkStatsManager` read inside the 30-sec foreground service** | Keeps exact 10-minute cadence while the service is alive; drops WorkManager from D4 entirely. Nothing runs if the service is killed. |

**Decided (2026-11-03): (c), matching the `data-harvester` that produces the training data.** The harvester (v1.6.5, `MeasurementRules`) samples `TrafficStats` every **30 s** (`COLLECTION_INTERVAL_MS = 30_000`) and `NetworkStatsManager` every **10 min** (`APP_COLLECTION_INTERVAL_MS = 600_000`), both inside its foreground service and also on every Wi-Fi ↔ mobile switch, with a 15-minute WorkManager **watchdog** that only restarts collection. The app must measure at the same granularity: features computed at inference time have to match the B2 features computed from harvester data, or forecasts degrade silently. WorkManager cannot run every 10 minutes, so the 10-minute read lives in the service; the watchdog covers process death. The interval is also recorded in [`AGENTS.md`](AGENTS.md).

### ⚠️ DECISION 2 — What "Local model" actually means (affects D6, E3, and Chapter 3)

The roadmap previously specified a local XGBoost model "retrained or fine-tuned on-device." **ONNX Runtime performs inference only** for tree ensembles — there is no on-device XGBoost training or fine-tuning path, so D6 as originally written is not buildable. Separately, ~7 days of a single user's telemetry is far too little to fit an XGBoost ensemble without severe overfitting.

Keep this distinction explicit in all documentation: **on-device _inference_ is non-negotiable; on-device _training_ was an assumption, not a capability.**

| Option | Trade-off |
|---|---|
| **(a) Residual correction on top of the global model** *(recommended)* | Global ONNX output + a small per-user correction (running bias plus an online linear fit on a few features), trained in plain Kotlin. No new dependency, fully offline, genuinely personalized, easy to defend to a panel. Less "a second XGBoost model" than the original framing. |
| **(b) Cloud retraining, on-device inference** | Per-user model retrained in `datara-ml`, exported to ONNX, stored in Supabase Storage, downloaded by the client. True per-user XGBoost. Does **not** violate the no-`/predict` rule (inference stays local) but gives up offline-only for the retraining step. |
| **(c) On-device lightweight learner** | A small incremental regressor (SGD/ridge) trained locally that replaces the global model once it outperforms it. Fully on-device training, but no longer XGBoost at the local layer. |

**Decided (2026-11-03): (a) residual correction, with a next-hour usage target.** The team chose (a), and the global model predicts usage rather than the PET itself:

- **Global model target:** mobile bytes used in the **next hour** for one device, from features available at the start of that hour. Training labels come straight from the harvester data (NetworkStatsManager windows per hour since contract v2), with no need to invent quotas.
- **Loss (decided 2026-11-06): Tweedie on raw bytes**, so the model predicts the *mean* usage of an hour. Squared error on log1p bytes predicted a typical hour and summed to only 43% of real usage, which made PETs late. Fallback: log1p with a smearing factor. The ONNX output becomes bytes through the model's `output_transform` metadata (feature contract v4).
- **PET is computed on the phone, not predicted:** starting from the remaining quota, the app predicts each following hour and subtracts it until the remainder reaches zero. That hour is the PET. The model never sees the quota, so any quota size works.
- **Why not predict the PET directly:** a PET label exists only when a cycle actually reaches its quota, which happens a few times per user at most. A next-hour target is checked against real usage every hour (about 168 times a week), which gives the local correction enough data to learn from.
- **Local layer:** a running ratio of actual to predicted usage per 6-hour block of the day, learned from those hourly differences in plain Kotlin and multiplied into the global prediction before projecting.
- **The thesis is still evaluated on PET** (predicted vs actual quota-reaching time); see B4 and E3.

#### Alternatives evaluated and rejected

Documented so the panel question "why not just train on-device?" has a researched answer rather than an improvised one.

| Alternative | Why rejected |
|---|---|
| **Treelite / Hummingbird** | Inference-optimization libraries, not training. They are alternatives to ONNX Runtime, not to on-device training, and would still need NDK cross-compilation to run on Android at all. |
| **TensorFlow Decision Forests / TFLite** | TFLite's on-device training API covers neural networks via signature-based transfer learning; it does not cover decision forests. TF-DF models train in Python via Yggdrasil Decision Forests. Would abandon XGBoost without delivering on-device training. |
| **Native XGBoost / LightGBM C API via Android NDK** | Technically possible — XGBoost does expose a C API — but there are no official Android builds or prebuilt AARs. Requires cross-compiling a large C++ library per ABI, a JNI bridge, and several MB of native libs in the APK. Multi-week build-engineering risk against a defense deadline, and it does not address the data problem below. |
| **River (online/incremental ML)** | Python-only, targeted at Linux edge nodes; there is no Kotlin/Android runtime. Shipping a Python runtime via Chaquopy to obtain it is disproportionate. **However, its underlying paradigm — incremental updates from streaming samples — is precisely what option (a) implements in ~50–80 lines of Kotlin with no dependency.** |

**The deeper reason none of these change the decision:** all four try to preserve the premise that the local layer must be XGBoost. But at 10-minute polling (Decision 1), 7 days of a single user's data is **~1,008 rows**. Fitting a gradient-boosted ensemble on ~1,000 rows with ~10 features overfits regardless of which runtime executes it. The constraint is statistical as well as technical, so a better runtime does not solve it.

The claim the thesis actually defends is *"per-user personalization improves PET accuracy over a global baseline"* — not *"the local model is specifically XGBoost."* Options (a) and (c) deliver that claim without the engineering risk.

**Check before finalizing:** if Chapter 3 commits in defended text to a *local XGBoost model* specifically, then option **(b)** is the honest route — it keeps real per-user XGBoost at the cost of offline-only retraining. What Chapter 3 already says is the deciding fact here, not the engineering.

---

### Pending alignment — quota storage and research contract

- [ ] Design quota/cycle persistence and its relationships to usage and notifications before C2 follow-up migrations and D2 entities. Preserve existing deployed data and RLS; do not assume the seven-table promo model is the final quota schema. *(Draft for the DB admin's review: [quota schema ERD](docs/erd-draft.md).)*
- [ ] Align manuscript objectives, diagrams, target labels and evaluation protocol with device-measured quota-reaching time. Define cycle baselines, observation gaps, quota crossings and cycles that end before reaching the quota. Do not score unobserved crossings as known outcomes.
- [ ] Keep changes to `data-harvester` and `datara-ml` as separately scoped work; this Android documentation update does not implement those changes.

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
  - **Measurement-validity check:** validate mobile-only cumulative usage over a defined cycle window, including counter resets, missing intervals and bucket resolution. Record quota-crossing observations and their timing uncertainty. Carrier balances are not ground truth for this target; freebie/zero-rated traffic is included in device-measured consumption. No carrier-balance reconciliation is required.
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
    - **Resolution constraint:** `NetworkStatsManager` is coarsely bucketed (roughly 2-hour granularity for detailed queries), so on-device features are running totals, not true 10-minute resolution. Engineer features the client can actually reproduce at inference time — a feature the app cannot compute is useless no matter how well it trains.
  - `time_features`: hour of day (cyclical sine/cosine), day of week, is_weekend.
  - `network_features`: signal strength (dBm/bars), network type (LTE/5G), and measured speed. Confirm the speed field, units and availability in the research contract before training; the earlier A4 export list is not a finalized quota dataset schema.
  - **Contract frozen (2026-11-06):** datara-ml `docs/feature-contract.md` (v1) fixes 12 features: hour sin/cos, day of week, is_weekend, last-1 h bytes, 3 h and 24 h average, same hour yesterday and last week (all log1p), last-hour signal median, radio generation and share of readings on cellular. **Measured speed is excluded:** the harvester has no speed column. Reference implementation `datara_ml/features.py`; the app reproduces it (D6 parity test).
  - **Contract v2 (2026-11-06):** the target and byte lags come from NetworkStatsManager windows (spread over clock hours; hours inside windows over 2 h are unobserved), not the 30-second rows, which capture only 77.8% of usage over hours both measured. See datara-ml `docs/measurement-gaps.md`.
  - **Target (decided, see Decision 2):** mobile bytes used in the **next hour** per device (hourly NetworkStatsManager totals since contract v2). Trained with the **Tweedie loss on raw bytes** (decided 2026-11-06), which handles the many zero hours and predicts the mean; log1p squared error predicted a typical hour and underestimated total usage by more than half. The PET is not a training label; the app derives it by projecting hourly predictions against the remaining quota.
  - Every feature must be computable on the phone from the same 30 s and 10 min readings at the start of the hour, using the same definitions as the app (lags such as last hour, last 24 h average, same hour yesterday).
- [ ] **Phase B3 — Global XGBoost Model Training & Hyperparameter Tuning:**
  - Algorithm: `XGBRegressor` / `XGBoost`.
  - Objective: minimize next-hour usage error across users (for example MAE on bytes). PET accuracy is measured in B4 by projection.
  - Hyperparameter optimization via Optuna or GridSearchCV (`learning_rate`, `max_depth`, `n_estimators`, `subsample`, `reg_lambda`).
  - Split by **respondent**, not by row — random row splits leak the same user into train and test and will inflate every metric.
- [ ] **Phase B4 — Model Evaluation & Thesis Metrics:**
  - Compute panel-required evaluation metrics:
    - **MAE** (Mean Absolute Error) in minutes/hours.
    - **RMSE** (Root Mean Squared Error).
    - **R²** (Coefficient of Determination).
  - **Acceptance target — decide before B3 runs, not after seeing results:** state the MAE the model must beat to be considered successful (e.g. "MAE ≤ 2 hours on a 24-hour prediction horizon") and the naive baseline it must outperform (e.g. constant average consumption rate). Committing to this in advance is the difference between an evaluation and a rationalization, and panels probe for it.
  - Report two levels of error: next-hour usage error (MAE/RMSE on bytes or MB) and **PET error** (MAE in hours), the thesis metric.
  - **PET evaluation without real quotas:** the harvester data has no user-set quotas, so evaluate PET by simulating cycles on held-out participants: pick a start time and a quota amount, run the same hour-by-hour projection the app uses, and compare the projected PET with the hour cumulative usage actually crossed the quota. Use several quota sizes, and do not score simulated cycles that never cross the quota within the data.
  - Generate feature importance plots (SHAP values, gain) for thesis documentation.
- [ ] **Phase B5 — Model Export to ONNX (`.onnx`):**
  - Convert trained XGBoost model using `onnxmltools` / `skl2onnx`.
  - Validate inference parity between Python XGBoost and ONNX Runtime.
  - Export final asset: `global_xgboost.onnx`. Record the exact input tensor shape, feature order, and dtype — D6 must reproduce them precisely.

---

### Track C: Backend & Cloud Infrastructure (`Supabase`)
*Goal: Centralized authentication, synced Postgres schema, and backup logging.*

- [x] **Phase C1 — Auth Setup:** Supabase Auth with Email/Password.
- [x] **Phase C2 — Original Database Schema Definition:** *(Historical verification: applied schema and RLS checked against two live accounts on 2026-09-17. See "RLS verification" in [`supabase/README.md`](supabase/README.md) to re-run it.)*
  - Tables: `users`, `devices`, `providers`, `promos`, `data_usage`, `notifications`, `associations`.
  - Enforce Row Level Security (RLS) policies per `user_id`. No table ships without its policy.
  - Room entities (D2) match the agreed quota schema; sync mappers (C3) must too. Convention: Postgres is `snake_case` plural (`data_usage`), Room entities are `PascalCase` singular (`DataUsage`) — the mapping is naming-only, types must match exactly.
- [x] **C2 follow-up — Quota schema:** design and apply new migrations for quota/cycle persistence, relationships and RLS. *(`20261104000001`, from the [quota schema ERD](docs/erd-draft.md); applied to the hosted project with `supabase db push` on 2026-11-04. It dropped the legacy promo tables.)*
- [ ] **Phase C3 — Telemetry Sync:**
  - Batched background sync of usage records from the Android client.
  - **Implementation note:** this is a batched Postgrest insert from `supabase-kt`, not a custom HTTP endpoint. Only introduce a Supabase Edge Function if server-side validation or aggregation is genuinely needed — otherwise there is no endpoint to build.
  - Records are scoped to `user_id` under RLS, which makes synced data **pseudonymized, not anonymized**. Use the accurate term in the manuscript; overstating this is an easy point to be challenged on. (Only the A4 CSV export is anonymized.)

---

### Track D: Android Client App (`datara-android`)
*Goal: User-facing application with real-time telemetry, dual-layer on-device inference, and PET dashboard.*

#### Phase D1: Authentication, Branding & App Shell *(implemented)*
- [x] Supabase Auth (`auth-kt`) with Ktor client.
- [x] Light auth UI (2026-10-31, replacing the original dark navy screens): welcome screen with an animated in-screen Login/Register panel (`AuthEntryScreen`), optically centered logo; navy launcher icon, splash and welcome with a splash-to-welcome logo transition (2026-11-03).
- [x] Tier 1 auth hardening (2026-10-31): 8-character letters-and-digits password rule with a strength meter, 60-second resend cooldown, no "no account found" hints on login or reset, autofill hints on every auth field. Hosted Supabase settings must match — see [supabase/README.md](supabase/README.md#usernames-and-google-sign-in).
- [x] `AuthRepository`, `AuthViewModel`, and Hilt injection.
- [x] Session auto-login persistence on launch — `SessionViewModel` maps `sessionStatus` to `SessionState`, `MainActivity` shows `SplashScreen` until it resolves, then picks the start destination.
- [x] Initial user record creation in Supabase on sign-up — created server-side by the `on_auth_user_created` trigger (C2); registration now collects a full name and sends it as auth metadata so the trigger populates `users.name` on insert.

- [x] Password recovery via email OTP; no phone/SMS login.
- [x] Username as an alternative login identifier (2026-10-31): `users.username` migration, `username-login` Edge Function (email never sent to the client), username screen after sign-up, and a `NeedsUsername` launch gate for any signed-in user without one.
- [x] Google sign-in/sign-up via Credential Manager + Supabase ID-token auth; hidden unless `GOOGLE_WEB_CLIENT_ID` is set. Requires Google Cloud and Supabase provider setup — see [supabase/README.md](supabase/README.md#usernames-and-google-sign-in).
- [ ] Before release: turn email confirmation on and add a "check your inbox" step after Register; revisit the Edge Function's shared-IP rate limiting.
- [x] Official DATAra logo in authentication, session loading and Home; adaptive/monochrome launcher assets and AndroidX native splash. No forced video or minimum startup delay. See [brand assets](docs/brand-assets.md).

#### Phase D2: Room Local Database & Hardware Detection
*Status (2026-11-04): built. Telemetry now persists in Room; nothing uploads yet (C3) and no screen reads Room yet (D3, D5, D7, D8).*

- [x] Add Room dependencies and KSP compiler (`ksp`, never `kapt` — matches the existing Hilt setup). *(Room 2.8.5; schemas exported to `app/schemas/`.)*
- [ ] Create Room entities and sync mappings after the C2 quota-schema follow-up is designed. Do not blindly mirror the legacy promo model into new quota persistence. *(Entities done: the eight synced tables column for column, each with a local `synced_at` upload queue, plus the four phone-only D4 tables. The Supabase sync mappers belong to C3.)*
- [x] Implement `DataraDatabase` and DAOs. *(Includes the queries D3, D6, D7 and D8 need: readings in a time window, the active cycle as a Flow, the unsynced queue. One active cycle per device is enforced by triggers. Instrumented tests: `app/src/androidTest/.../data/local/`.)*
- [x] Room-backed telemetry repository, swapped in `TelemetryModule`; it passes the same collector contract as the in-memory one. Each checkpoint commits in one transaction with its rows. *(Checked on a POCO F3 on 2026-11-05: after a force-stop the next sample continued from the saved checkpoint, and the closed window rolled up into `data_usage`.)*
- [x] 10-minute `data_usage` rollup from `usage_samples`, per the ERD's rules (`UsageRollup`, run after each durable read). Samples are kept 14 days after rollup.
- [x] Auto-detect SIM/Carrier via `TelephonyManager` (*strictly no SMS reading, no manual carrier entry*). *(Built in D4's `RadioInfoSource` with `getSimOperatorName()`, no permission; recorded on every sample and `data_usage` row, not on `devices`.)*
  - Try `getSimOperatorName()` / `getSimOperator()` first — these need **no permission**. Only request `READ_PHONE_STATE` if a specific value genuinely requires it; it is a dangerous permission and needs a justification you can defend.
- [x] Extract hardware metadata (`Build.MODEL`, `Build.VERSION.SDK_INT`). *(`DeviceRepository` registers a `devices` row with a `device_id` generated once per install and account; the database and ids are excluded from Auto Backup.)*

#### Phase D3: User-Defined Quota & Cycle Setup
- [x] Create Quota UI: decimal MB/GB amount, optional name, validation, draft restoration and cancel/submit navigation. Units: 1 MB = 1,000,000 bytes; 1 GB = 1,000 MB.
- [x] Cycle creation, Quota Details, confirmed end-cycle action and replacement-cycle flow. *(2026-11-04: Create Quota and Quota Details run on real cycles through `QuotaCycleViewModel`; Home keeps its preview fixtures until D5. See [quota cycles](docs/quota-cycles.md).)*
- [x] Persist quota amount, cycle boundaries and status using the agreed C2/D2 design. *(2026-11-05: `RoomTrackingCycleRepository` over D2's `TrackingCycleDao`; a replacement ends the old cycle and inserts the new one in one transaction. `QuotaCycleManagerContract` passes against in-memory Room on a POCO F3.)*
- [x] Establish the measured mobile-usage window at cycle start; derive used and remaining quota consistently from that window. Integrate reached/ended behavior with real measurements. *(Per the ERD there is no `baseline_bytes`: used = the 10-minute NetworkStatsManager readings in `[start_at, now)`, split proportionally at the edges. Reached/ended transitions follow the ERD. Unit-tested, and checked on a POCO F3 on mobile data: a 5 MB cycle reached its quota at the end of the crossing reading.)*
- [x] Preserve past cycles and restore the active cycle across app restarts through durable storage. *(On a POCO F3, an active cycle and its used bytes were restored after `am force-stop` and relaunch. Past cycles are kept in Room; listing them is D7.)*

The former promo catalog, promo entry and expiry setup are superseded. A chosen quota is a tracking amount, not a purchased allowance; all measured mobile traffic counts regardless of carrier freebies.

#### Phase D4: Dual Background Telemetry Harvester
*Per ⚠️ DECISION 1 (decided): 30 s and 10 min inside one foreground service, plus a 15-minute WorkManager watchdog — the same scheme as `data-harvester`, so app features match the training data.*

*Status (2026-11-04): built without persistence, and checked on a POCO F3 (MIUI) over adb. See [usage telemetry](docs/telemetry.md).*

- [x] **Permissions — three different mechanisms, do not treat them as one list:** *(Tracking setup screen; reached from Home and Settings)*
  - `PACKAGE_USAGE_STATS` — **special access, not a runtime permission.** There is no permission dialog. Send the user to `Settings.ACTION_USAGE_ACCESS_SETTINGS` and verify the grant via `AppOpsManager` (`unsafeCheckOpNoThrow`). Needs its own onboarding screen explaining why. This is the most common first-run failure for `NetworkStatsManager` apps.
  - `POST_NOTIFICATIONS` — **runtime permission on Android 13+.** Required for the foreground-service notification here and for all of D8. Request it before starting the service.
  - `FOREGROUND_SERVICE` — normal install-time permission, never requested at runtime. With `targetSdk 37` you additionally need a **typed** service: the `FOREGROUND_SERVICE_DATA_SYNC` permission (or `_SPECIAL_USE` with a written justification) plus `android:foregroundServiceType` on the `<service>` element. Untyped foreground services are rejected at this target level.
  - `READ_PHONE_STATE` — runtime permission. Optional on the setup screen: it gives the 5G/LTE/3G label, a model input; without it the label is `CELLULAR`, as in the harvester. Signal and carrier need no permission.
  - Also added with approval: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (lets the watchdog restart the service on Android 12+), `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE`. The service type is `specialUse`.
- [x] **30-Second Foreground Service:** *(verified on device: 30.0 s cadence; resumes when the app opens)*
  - Measure real-time delta and instantaneous speed (KB/s, MB/s) using `TrafficStats`.
  - **Handle reboots:** `TrafficStats` counters are device-wide and reset to zero on reboot. Persist the last-seen counter and, when the new reading is lower than the stored one, treat it as a reboot and re-baseline instead of recording a large negative delta.
  - Persistent notification copy should explain the tracking honestly (users see it constantly, and the panel will too).
- [ ] **10-Minute `NetworkStatsManager` read (inside the foreground service)** *(interval per DECISION 1)*: *(read, day-close and switch trigger built and verified on device; quota remaining is computed by D3 cycles, and reconciliation is not coded yet; on device the durable total matched the 30 s sum within 335 bytes)*
  - Also run it immediately on a Wi-Fi ↔ mobile switch (debounced), as the harvester does.
  - Measure durable cellular totals with `NetworkStatsManager`. Its buckets are ~2 h, too coarse to query "the last 10 minutes" directly: read cumulative usage since a fixed anchor (the harvester uses UTC midnight) and store the difference from the previous reading.
  - Calculate remaining tracking quota against the active cycle; include all measured mobile traffic regardless of carrier billing treatment. *(Done in D3 from these readings.)*
  - Reconcile against the service's `TrafficStats` figures; `NetworkStatsManager` is the source of truth for durable totals.
- [ ] **15-Minute WorkManager watchdog:** restarts the foreground service if the system killed it (WorkManager's minimum period). It does not take measurements itself. *(Built; not yet seen restarting on a device. MIUI blocks background starts while Autostart is off.)*
- [x] **Mirror the harvester's measurement rules** (intervals, gap thresholds, network-switch handling, reboot re-baselining) from `data-harvester`'s `MeasurementRules` rather than re-deriving them, so recorded intervals mean the same thing in training and in the app. *(Mirrors v1.6.7, plus one approved deviation found on device. A baseline read while mobile data was down is re-baselined when mobile data returns. 1.6.7 counted 1.2 GB in 29 s there, so the harvester's data likely has the same spikes; see [usage telemetry](docs/telemetry.md).)*
- [x] **Signal Strength Monitor:** *(on device: LTE RSRP −79 dBm, carrier GLOBE)*
  - `TelephonyCallback` on API 31+, `PhoneStateListener` below it (deprecated at 31). With `minSdk 26` **both** paths are required — pick by `Build.VERSION.SDK_INT`.
- [ ] Persist snapshots to local Room database and queue for cloud sync. *(Room done in D2: samples, readings and `data_usage` rows persist and `data_usage` is queued by `synced_at`. Upload blocked by C3.)*
- [ ] Verify behavior under Doze and battery optimization on a real device, not just the emulator.

#### Phase D5: Home & Floating Navigation
- [x] Home replaces the old dashboard placeholder; floating Material 3 navigation connects Home, History and Settings.
- [x] Home UI covers no active quota, collecting usage, forecast available, missing permission and quota reached, plus an ended-cycle presentation. Scenarios are under Preview options and forecasts are explicitly sample data.
- [x] Prominent forecast hero, quota progress/details action and seven-day usage bars. Collecting and missing-access states do not fabricate measured balances. The chart uses an independent History fixture.
- [x] Direction C light QuotaTheme: white hero/surfaces, navy text/actions, teal progress, soft blue panels and mint navigation selection. Authentication stays dark navy; no theme toggle.
- [ ] Connect Home and Details to persisted quota state, real access status, telemetry and D6 inference; retain honest loading/unavailable states.

Promo/expiry cards and live network badges are not part of the current UI. Signal and speed remain planned model inputs; omitting display badges does not remove them from the prediction pipeline.

#### Phase D6: On-Device Machine Learning & Dual-Model Execution
*Blocked by: B5 (needs `global_xgboost.onnx`), D2, D4. See ⚠️ DECISION 2 — written below against the residual-correction option.*

- [x] Add `com.microsoft.onnxruntime:onnxruntime-android`. *(1.31.1 via the version catalog; unit tests run the same model on the JVM artifact.)*
- [x] Bundle `global_xgboost.onnx` in `app/src/main/assets/models/`. *(Currently the interim `v0-tweedie-2026-11-06` (contract v4, Tweedie) from datara-ml `train_v0.py`: 689 hours from 17 devices, grouped-by-device CV next-hour MAE 59.8 MB vs 62.3 MB for the last-hour baseline; predictions sum to 79% of actual usage (log1p: 43%). The final 14-day model replaces it as a file swap, see [forecast](docs/forecast.md).)*
- [x] Build on-device Feature Extractor producing one hourly feature row in exactly B2's definitions. *(Contract v2, 2026-11-06: hourly bytes from the NetworkStatsManager `durable_readings`, network features from the 30-second `usage_samples`. The 30-second readings miss about a quarter of usage; see datara-ml `docs/measurement-gaps.md`.)*
  - Must reproduce B2's feature order, shapes, and dtypes **exactly**. Add a parity test against a known input/output pair captured from the Python pipeline — silent feature-order mismatch produces plausible-looking wrong predictions, which is the worst possible failure mode for a defense demo. *(`FeatureParityTest` replays datara-ml's golden fixture: features, ONNX output and PET.)*
- [x] **Dual-Model Conditional Switch:** *(`LocalLayer`, `ModelSwitch`: per-time-of-day residual correction in log space; LOCAL after 168 learned hours when its recent MAE is ≥ 5% below global, back to GLOBAL when it is no better.)*
  - `ModelType.GLOBAL`: baseline pre-trained ONNX model, used for new users.
  - `ModelType.LOCAL`: global ONNX output multiplied (or offset) by the per-user correction learned from hourly prediction errors (Decision 2).
  - Switch to LOCAL once the user has ≥ 7 days of telemetry **and** the corrected predictions' recent error is lower than the global model's; switch back automatically if it becomes worse.
  - The switch is conditional and reversible — never hardcode one mode.
  - *Strict rule:* all **inference** is on-device via ONNX Runtime. No live `/predict` server call, ever.
- [x] **PET projection:** starting from the active cycle's remaining quota, predict each following hour (updating time features and lags as it steps) and subtract until the remainder reaches zero; that hour is the PET. Cap the horizon (for example 30 days) and show "beyond 30 days" instead of an unbounded date.
- [x] Output predicted PET timestamp and expose via `StateFlow` to the UI. *(`ForecastEngine.status`; Home's hero and Quota Details show it for a real active cycle, with collecting/unavailable states.)*
- [x] Log each prediction with its inputs and the observed quota-reaching time (or an explicitly incomplete observation) — E3 cannot be done retroactively without this. Each hourly log row holds **both** the global and the local prediction (local in shadow until it takes over) and which one the user saw; see [quota schema ERD](docs/erd-draft.md). *(One `forecasts` row per active cycle per hour; the observed reach is `tracking_cycles.reached_at`.)*

#### Phase D7: Usage History & Analytics (Vico Charts)
- [x] Vico Compose 3.3.1 through the version catalog.
- [x] History UI with independent sample data: 24-hour, 7-day and quota-cycle filters, total/average and peak interval, exact-readings sheet, empty preview and read-only past-cycle details.
- [ ] Connect charts and cycle details to durable measured usage and quota history. Home actions do not currently create History entries.

#### Phase D8: Proactive Alerts & Notification System
*Production delivery depends on D4 measurements/access and D6 forecasts.*

*Status (2026-11-06): built. See [quota alerts](docs/notifications.md).*

- [x] In-app notification center: All/Unread filters, mark-all-read, unread badges and an empty state, over Room `notifications`. Opening an alert offers its cycle: Quota Details for the current cycle, read-only details for a past one.
- [x] Copy for the 75%, 90% and 100% milestones and the 24-hour and 3-hour forecast alerts, with the figures at sending time. Reaching a quota does not mean connectivity stops; every alert says nothing is blocked. The sample 50/80/90% and four-hour copy is gone.
- [x] Implement OS notification channels, permission handling and delivery. *(Channels "Quota milestones" and "Forecast alerts". `POST_NOTIFICATIONS` is the D4 permission, no new one; when it is denied the alert stays in the in-app inbox and Settings offers "Allow notifications". Tapping an alert opens its cycle.)*
- [x] Connect threshold and forecast triggers to real measurements and user preferences; rate-limit repeated alerts. *(Decided 2026-11-04: milestones at 75%, 90% and 100% (quota reached), plus forecast alerts when the PET is within 24 h and within 3 h, each once per cycle. Usage from D3, PET from D6's latest forecast only; never while collecting or beyond 30 days; highest level only, and never a lower level after a higher one. Evaluated on the telemetry and forecast cadence with no extra wake-ups. The Settings switches persist in `notification_preferences`.)*
- [ ] Persist notification history/read flags in Room and integrate agreed sync behavior. *(Room done: once per cycle is enforced by the unique `(cycle_id, notification_type)` index, `read_at` is real, and rows are queued by `synced_at`. Upload blocked by C3.)*

#### Phase D9: Profile, Settings & Developer ML Diagnostics Screen
- [x] Settings/Profile UI: display name loaded from and saved to `public.users.name` (with the `@username` shown read-only), notification switches (persisted in Room since D8), simulated sync states, Usage Access guidance, version/privacy information and signed-in email.
- [x] Confirmed sign-out through existing Supabase Auth, pending/error handling and navigation only after success.
- [ ] Persist profile name and preferences; connect actual permission/status checks and cloud sync. No phone field is required by the current UI.
- [ ] **Developer ML Diagnostics Screen (Hidden):** gated behind a secret gesture; expose active model mode, MAE/RMSE/R², inputs and latency only when the real pipeline exists.


---

### Track E: Integration, Evaluation & Thesis Defense Deliverables
*Goal: Academic validation and presentation readiness for the USTP BSIT panel.*

- [ ] **Phase E1 — Offline & Connectivity-Loss Resilience Testing.** Two distinct scenarios — the old single "airplane mode" test was incoherent, since airplane mode disables cellular and there is consequently no mobile data to track:
  - **No-network inference:** with the network fully off, confirm PET inference still runs on stored telemetry and the dashboard renders a prediction.
  - **Cellular up, Supabase unreachable:** with mobile data live but sync failing, confirm tracking continues, snapshots persist to Room, the sync queue accumulates, and it drains correctly once connectivity returns.
- [ ] **Phase E2 — Battery & Overhead Benchmarking:** Profile foreground service and background worker energy usage using Android Studio Energy Profiler. Report measured drain over a fixed window (e.g. %/hour over 8 hours) — panels ask for a number, not a claim of efficiency.
- [ ] **Phase E3 — Global vs. Local Accuracy Comparison:** Compare PET error (observed vs. predicted quota-reaching time) under Global vs. Local personalization, using the prediction log from D6, which records both models for the same hours.
  - **State the win condition in advance:** the improvement in MAE that makes the personalization layer worth having (e.g. "Local must reduce MAE by ≥ 15% over Global"). Report it honestly if the local layer does not help — a negative result with a clear explanation defends far better than a metric chosen after the fact.
  - Report A3 measurement resolution, gaps and quota-crossing uncertainty separately from model error. State how incomplete/ended cycles are handled; do not use carrier balance depletion as the target.
- [ ] **Phase E4 — Automated Test Coverage Audit** *(write tests continuously per phase; this phase is the final gap audit, not the first time tests are written — see the testing expectations in [`AGENTS.md`](AGENTS.md))*:
  - JUnit 4 tests for ViewModel state transitions, dual-model switch logic, feature-extraction parity, and Room queries.
  - Compose UI tests for Auth, Dashboard, and History flows.
- [ ] **Phase E5 — Capstone Manuscript & Presentation Assets:**
  - Align the original prepaid-exhaustion framing, ERD/use cases and research claims with the approved quota direction; confirm the final title separately.
  - Export final evaluation tables (MAE, RMSE, R²).
  - Screen recordings and interactive demo scripts.
  - Build a signed demo APK and verify it on the actual defense device.

---

## 4. Contingencies

Worth deciding now, while there is still time to change course:

- **Pilot yields too little usable data for a defensible global model (A3 → B3).** Fallback: train on a reduced feature set, or ship a transparent heuristic baseline (rolling average consumption rate) as the prediction engine and reframe the XGBoost layer as evaluated-but-not-shipped. Decide the minimum viable dataset size at A3 so this trigger is objective rather than a judgment call under deadline.
- **Measured quota-crossing time is too uncertain (A3).** Refine the observation protocol or prediction horizon, report timing uncertainty, and withhold unsupported accuracy claims. Switching to carrier balances would change the approved target.
- **Track B slips and D6 cannot land.** Once their production integrations are implemented, D1–D5 and D7–D9 can demonstrate tracking without ML; the current sample UI alone is not a working tracker. Keep the prediction interface behind an abstraction from D5 onward so a heuristic can be swapped in for the ONNX model without touching UI code.
- **Local personalization shows no improvement (E3).** This is a legitimate finding, not a failure — report it with the dual-layer architecture intact and the negative result explained.
