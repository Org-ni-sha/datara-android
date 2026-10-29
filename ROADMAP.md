# DATAra — End-to-End Capstone Master Roadmap & Build Order

> **Original capstone framing:** Probable Exhaustion Time (PET) Prediction for Prepaid Mobile Data (manuscript alignment pending)
> **Institution:** University of Science and Technology of Southern Philippines (USTP) – Cagayan de Oro City  
> **Scope:** Full Capstone Ecosystem — ML Training Pipeline (`datara-ml`), Dataset Harvesting (`data-harvester`), Backend (`Supabase`), and Android Client (`datara-android`).

---

## Current direction and implementation status (2026-10-29)

User-defined usage-quota forecasting is the approved product direction. The user chooses an amount in MB or GB to track; PET means the estimated time when device-measured mobile consumption reaches that amount. It does not predict carrier balance depletion, promo expiry or loss of connectivity. Traffic covered by freebies or zero-rating still counts when recorded by the device; the app does not classify carrier billing buckets or reconcile a carrier balance. Quotas inform the user and never block or throttle traffic.

Authentication and the branded launch flow are implemented. Home, Create Quota, Quota Details, History, Notifications, Settings and Profile are implemented as UI previews using sample/session state. Room, real telemetry, quota persistence, inference, OS alert delivery and cloud sync remain pending. Completed UI checkboxes below do not mean their production integrations are complete.

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

D4 below is written against **(a) 15 minutes** as the provisional default. Change it if you pick otherwise — and note the interval is also recorded in [`AGENTS.md`](AGENTS.md).

### ⚠️ DECISION 2 — What "Local model" actually means (affects D6, E3, and Chapter 3)

The roadmap previously specified a local XGBoost model "retrained or fine-tuned on-device." **ONNX Runtime performs inference only** for tree ensembles — there is no on-device XGBoost training or fine-tuning path, so D6 as originally written is not buildable. Separately, ~7 days of a single user's telemetry is far too little to fit an XGBoost ensemble without severe overfitting.

Keep this distinction explicit in all documentation: **on-device _inference_ is non-negotiable; on-device _training_ was an assumption, not a capability.**

| Option | Trade-off |
|---|---|
| **(a) Residual correction on top of the global model** *(recommended)* | Global ONNX output + a small per-user correction (running bias plus an online linear fit on a few features), trained in plain Kotlin. No new dependency, fully offline, genuinely personalized, easy to defend to a panel. Less "a second XGBoost model" than the original framing. |
| **(b) Cloud retraining, on-device inference** | Per-user model retrained in `datara-ml`, exported to ONNX, stored in Supabase Storage, downloaded by the client. True per-user XGBoost. Does **not** violate the no-`/predict` rule (inference stays local) but gives up offline-only for the retraining step. |
| **(c) On-device lightweight learner** | A small incremental regressor (SGD/ridge) trained locally that replaces the global model once it outperforms it. Fully on-device training, but no longer XGBoost at the local layer. |

D6 below is written against **(a)** as a provisional default, not a resolved decision. E3's comparison is valid under any option.

#### Alternatives evaluated and rejected

Documented so the panel question "why not just train on-device?" has a researched answer rather than an improvised one.

| Alternative | Why rejected |
|---|---|
| **Treelite / Hummingbird** | Inference-optimization libraries, not training. They are alternatives to ONNX Runtime, not to on-device training, and would still need NDK cross-compilation to run on Android at all. |
| **TensorFlow Decision Forests / TFLite** | TFLite's on-device training API covers neural networks via signature-based transfer learning; it does not cover decision forests. TF-DF models train in Python via Yggdrasil Decision Forests. Would abandon XGBoost without delivering on-device training. |
| **Native XGBoost / LightGBM C API via Android NDK** | Technically possible — XGBoost does expose a C API — but there are no official Android builds or prebuilt AARs. Requires cross-compiling a large C++ library per ABI, a JNI bridge, and several MB of native libs in the APK. Multi-week build-engineering risk against a defense deadline, and it does not address the data problem below. |
| **River (online/incremental ML)** | Python-only, targeted at Linux edge nodes; there is no Kotlin/Android runtime. Shipping a Python runtime via Chaquopy to obtain it is disproportionate. **However, its underlying paradigm — incremental updates from streaming samples — is precisely what option (a) implements in ~50–80 lines of Kotlin with no dependency.** |

**The deeper reason none of these change the decision:** all four try to preserve the premise that the local layer must be XGBoost. But at 15-minute polling, 7 days of a single user's data is **~672 rows**. Fitting a gradient-boosted ensemble on 672 rows with ~10 features overfits regardless of which runtime executes it. The constraint is statistical as well as technical, so a better runtime does not solve it.

The claim the thesis actually defends is *"per-user personalization improves PET accuracy over a global baseline"* — not *"the local model is specifically XGBoost."* Options (a) and (c) deliver that claim without the engineering risk.

**Check before finalizing:** if Chapter 3 commits in defended text to a *local XGBoost model* specifically, then option **(b)** is the honest route — it keeps real per-user XGBoost at the cost of offline-only retraining. What Chapter 3 already says is the deciding fact here, not the engineering.

---

### Pending alignment — quota storage and research contract

- [ ] Design quota/cycle persistence and its relationships to usage and notifications before C2 follow-up migrations and D2 entities. Preserve existing deployed data and RLS; do not assume the seven-table promo model is the final quota schema.
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
    - **Resolution constraint:** `NetworkStatsManager` is coarsely bucketed (roughly 2-hour granularity for detailed queries), so on-device features are running totals, not true 10/15-minute resolution. Engineer features the client can actually reproduce at inference time — a feature the app cannot compute is useless no matter how well it trains.
  - `time_features`: hour of day (cyclical sine/cosine), day of week, is_weekend.
  - `network_features`: signal strength (dBm/bars), network type (LTE/5G), and measured speed. Confirm the speed field, units and availability in the research contract before training; the earlier A4 export list is not a finalized quota dataset schema.
  - Target variable definition: time until measured usage reaches the chosen quota, or a consumption-rate forecast from which quota PET is derived. Finalize the target and horizon before training; use consistent cycle windows and quota units.
- [ ] **Phase B3 — Global XGBoost Model Training & Hyperparameter Tuning:**
  - Algorithm: `XGBRegressor` / `XGBoost`.
  - Objective: minimize quota-reaching-time prediction error across users.
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
- [x] **Phase C2 — Original Database Schema Definition:** *(Historical verification: applied schema and RLS checked against two live accounts on 2026-09-17. See "RLS verification" in [`supabase/README.md`](supabase/README.md) to re-run it.)*
  - Tables: `users`, `devices`, `providers`, `promos`, `data_usage`, `notifications`, `associations`.
  - Enforce Row Level Security (RLS) policies per `user_id`. No table ships without its policy.
  - Room is not implemented. Future entities and sync mappers must match the agreed schema. Convention: Postgres is `snake_case` plural (`data_usage`), Room entities are `PascalCase` singular (`DataUsage`) — the mapping is naming-only, types must match exactly.
- [ ] **C2 follow-up — Quota schema:** design and apply new migrations for quota/cycle persistence, relationships and RLS. Legacy `promos`/`associations` remain deployed until an explicit migration handles compatibility.
- [ ] **Phase C3 — Telemetry Sync:**
  - Batched background sync of usage records from the Android client.
  - **Implementation note:** this is a batched Postgrest insert from `supabase-kt`, not a custom HTTP endpoint. Only introduce a Supabase Edge Function if server-side validation or aggregation is genuinely needed — otherwise there is no endpoint to build.
  - Records are scoped to `user_id` under RLS, which makes synced data **pseudonymized, not anonymized**. Use the accurate term in the manuscript; overstating this is an easy point to be challenged on. (Only the A4 CSV export is anonymized.)

---

### Track D: Android Client App (`datara-android`)
*Goal: User-facing application with real-time telemetry, dual-layer on-device inference, and PET dashboard.*

#### Phase D1: Authentication, Branding & App Shell *(implemented)*
- [x] Supabase Auth (`auth-kt`) with Ktor client.
- [x] Dark navy UI (`#0C101A` / `#141B2B`) for `LoginScreen` and `RegisterScreen`.
- [x] `AuthRepository`, `AuthViewModel`, and Hilt injection.
- [x] Session auto-login persistence on launch — `SessionViewModel` maps `sessionStatus` to `SessionState`, `MainActivity` shows `SplashScreen` until it resolves, then picks the start destination.
- [x] Initial user record creation in Supabase on sign-up — created server-side by the `on_auth_user_created` trigger (C2); registration now collects a full name and sends it as auth metadata so the trigger populates `users.name` on insert.

- [x] Password recovery via email OTP; no phone/SMS login.
- [x] Official DATAra logo in authentication, session loading and Home; adaptive/monochrome launcher assets and AndroidX native splash. No forced video or minimum startup delay. See [brand assets](docs/brand-assets.md).

#### Phase D2: Room Local Database & Hardware Detection
- [ ] Add Room dependencies and KSP compiler (`ksp`, never `kapt` — matches the existing Hilt setup).
- [ ] Create Room entities and sync mappings after the C2 quota-schema follow-up is designed. Do not blindly mirror the legacy promo model into new quota persistence.
- [ ] Implement `DataraDatabase` and DAOs.
- [ ] Auto-detect SIM/Carrier via `TelephonyManager` (*strictly no SMS reading, no manual carrier entry*).
  - Try `getSimOperatorName()` / `getSimOperator()` first — these need **no permission**. Only request `READ_PHONE_STATE` if a specific value genuinely requires it; it is a dangerous permission and needs a justification you can defend.
- [ ] Extract hardware metadata (`Build.MODEL`, `Build.VERSION.SDK_INT`).

#### Phase D3: User-Defined Quota & Cycle Setup
- [x] Create Quota UI: decimal MB/GB amount, optional name, validation, draft restoration and cancel/submit navigation. Units: 1 MB = 1,000,000 bytes; 1 GB = 1,000 MB.
- [x] Sample cycle creation, Quota Details, confirmed end-cycle action and replacement-cycle flow. State uses SavedStateHandle, not a persistent database or historical archive.
- [ ] Persist quota amount, cycle boundaries and status using the agreed C2/D2 design.
- [ ] Establish the measured mobile-usage baseline at cycle start; derive used and remaining quota consistently from that window. Integrate reached/ended behavior with real measurements.
- [ ] Preserve past cycles and restore the active cycle across app restarts through durable storage.

The former promo catalog, promo entry and expiry setup are superseded. A chosen quota is a tracking amount, not a purchased allowance; all measured mobile traffic counts regardless of carrier freebies.

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
  - Calculate remaining tracking quota against the active cycle baseline; include all measured mobile traffic regardless of carrier billing treatment.
  - Reconcile against the foreground service's `TrafficStats` figures; `NetworkStatsManager` is the source of truth for durable totals.
- [ ] **Signal Strength Monitor:**
  - `TelephonyCallback` on API 31+, `PhoneStateListener` below it (deprecated at 31). With `minSdk 26` **both** paths are required — pick by `Build.VERSION.SDK_INT`.
- [ ] Persist snapshots to local Room database and queue for cloud sync. *(Blocked by: D2, C3.)*
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
- [ ] Log each prediction with its inputs and the observed quota-reaching time (or an explicitly incomplete observation) — E3 cannot be done retroactively without this.

#### Phase D7: Usage History & Analytics (Vico Charts)
- [x] Vico Compose 3.3.1 through the version catalog.
- [x] History UI with independent sample data: 24-hour, 7-day and quota-cycle filters, total/average and peak interval, exact-readings sheet, empty preview and read-only past-cycle details.
- [ ] Connect charts and cycle details to durable measured usage and quota history. Home actions do not currently create History entries.

#### Phase D8: Proactive Alerts & Notification System
*Production delivery depends on D4 measurements/access and D6 forecasts.*

- [x] In-app sample notification center: All/Unread filters, mark-all-read, unread badges, empty preview and read-only sample-cycle links.
- [x] Sample copy for 50%, 80% and 90% quota milestones, a four-hour forecast reminder and an informational quota-reached event. Reaching a quota does not mean connectivity stops.
- [ ] Implement OS notification channels, permission handling and delivery.
- [ ] Connect threshold and forecast triggers to real measurements and user preferences; rate-limit repeated alerts.
- [ ] Persist notification history/read flags in Room and integrate agreed sync behavior. Current read flags are SavedStateHandle preview state only.

#### Phase D9: Profile, Settings & Developer ML Diagnostics Screen
- [x] Settings/Profile UI: session-only name edits and notification switches, simulated sync states, Usage Access guidance, version/privacy information and signed-in email.
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
- [ ] **Phase E3 — Global vs. Local Accuracy Comparison:** Compare PET error (observed vs. predicted quota-reaching time) under Global vs. Local personalization, using the prediction log from D6.
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
