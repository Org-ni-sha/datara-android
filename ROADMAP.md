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
│  • Anonymized telemetry   │─────►│  • XGBoost Training       │
│  • Ground truth dataset   │      │  • Metric Tuning (MAE/R²) │
└───────────────────────────┘      │  • ONNX Export (.onnx)    │
                                   └─────────────┬─────────────┘
                                                 │
                                                 │ Bundled / Synced
                                                 ▼
┌───────────────────────────┐      ┌───────────────────────────┐
│      Supabase Cloud       │◄────►│      datara-android       │
│ • Postgres global schema  │      │   (Production Client)     │
│ • Auth & User profiles    │      │  • Live Telemetry Engine  │
│ • Usage log sync          │      │  • Global ONNX Inference  │
│ • Cloud model storage     │      │  • Local Model Personal.  │
└───────────────────────────┘      │  • PET Hero Dashboard     │
                                   └───────────────────────────┘
```

---

## 2. Cross-Ecosystem Roadmap (By Track & Phase)

### Track A: Dataset Harvesting & Preprocessing (`data-harvester`)
*Goal: Acquire realistic, panel-defensible Philippine prepaid mobile usage data.*

- [x] **Phase A1 — Harvester Baseline:** Standalone logger capturing mobile Rx/Tx bytes, timestamps, signal strength, and carrier info.
- [ ] **Phase A2 — Pilot Data Gathering:** Run pilot collection across volunteer respondents (prepaid students/users on Smart, Globe, DITO).
- [ ] **Phase A3 — Dataset Anonymization & Export:** Strip any device/user PII, format raw logs into standardized CSV format (`usage_date`, `time_interval`, `consumed_mb`, `signal_strength`, `carrier`, `session_duration`).

---

### Track B: Machine Learning Pipeline (`datara-ml`)
*Goal: Train, optimize, validate, and export the baseline Global XGBoost model to ONNX.*

- [ ] **Phase B1 — Exploratory Data Analysis (EDA) & Cleaning:**
  - Inspect distributions, handle missing intervals, detect spikes/outliers.
  - Analyze diurnal (day/night) usage patterns and weekend vs. weekday variations.
- [ ] **Phase B2 — Feature Engineering:**
  - `rolling_consumption_rate`: 1-hour, 3-hour, and 24-hour moving averages.
  - `time_features`: hour of day (cyclical sine/cosine), day of week, is_weekend.
  - `network_features`: signal strength (dBm/bars), network type (LTE/5G).
  - Target variable definition: Exhaustion timeframe / Consumption rate to compute PET.
- [ ] **Phase B3 — Global XGBoost Model Training & Hyperparameter Tuning:**
  - Algorithm: `XGBRegressor` / `XGBoost`.
  - Objective: Minimize exhaustion error margin across generalized prepaid users.
  - Hyperparameter optimization via Optuna or GridSearchCV (`learning_rate`, `max_depth`, `n_estimators`, `subsample`, `reg_lambda`).
- [ ] **Phase B4 — Model Evaluation & Thesis Metrics:**
  - Compute panel-required evaluation metrics:
    - **MAE** (Mean Absolute Error) in minutes/hours.
    - **RMSE** (Root Mean Squared Error).
    - **R²** (Coefficient of Determination).
  - Generate feature importance plots (SHAP values, gain) for thesis documentation.
- [ ] **Phase B5 — Model Export to ONNX (`.onnx`):**
  - Convert trained XGBoost model using `onnxmltools` / `skl2onnx`.
  - Validate inference parity between Python XGBoost and ONNX Runtime.
  - Export final asset: `global_xgboost.onnx`.

---

### Track C: Backend & Cloud Infrastructure (`Supabase`)
*Goal: Centralized authentication, synced Postgres schema, and backup logging.*

- [x] **Phase C1 — Auth Setup:** Supabase Auth with Email/Password.
- [ ] **Phase C2 — Database Schema Definition:**
  - Tables: `users`, `devices`, `providers`, `promos`, `data_usage`, `notifications`, `associations`.
  - Enforce Row Level Security (RLS) policies per `user_id`.
  - Align schema types 1:1 with Android Room entities.
- [ ] **Phase C3 — Telemetry Sync Endpoint:**
  - Batched background sync for anonymized usage records from Android client.

---

### Track D: Android Client App (`datara-android`)
*Goal: User-facing application with real-time telemetry, dual-layer on-device inference, and PET dashboard.*

#### Phase D1: Authentication & App Shell *(Status: Completed / Minor Polish)*
- [x] Supabase Auth (`auth-kt`) with Ktor client.
- [x] Dark navy UI (`#0C101A` / `#141B2B`) for `LoginScreen` and `RegisterScreen`.
- [x] `AuthRepository`, `AuthViewModel`, and Hilt injection.
- [ ] Session auto-login persistence on launch (`sessionStatus` check in `MainActivity`).
- [ ] Initial user record creation in Supabase on sign-up.

#### Phase D2: Room Local Database & Hardware Detection
- [ ] Add Room dependencies and KSP compiler.
- [ ] Create 7 Room entities mirroring Supabase schema (`User`, `Device`, `Provider`, `Promo`, `DataUsage`, `Notification`, `Association`).
- [ ] Implement `DataraDatabase` and DAOs.
- [ ] Auto-detect SIM/Carrier via `TelephonyManager` (*strictly no SMS reading, no manual carrier entry*).
- [ ] Extract hardware metadata (`Build.MODEL`, `Build.VERSION.SDK_INT`).

#### Phase D3: Active Promo & Data Baseline Setup
- [ ] Prepaid promo catalog filtered by detected carrier (Smart, Globe, DITO).
- [ ] Custom manual promo entry dialog (`data_amount_gb`, `validity_days`, `expiry_date`).
- [ ] Calculate baseline remaining data (`data_remaining_gb`).

#### Phase D4: Dual Background Telemetry Harvester
- [ ] Request runtime permissions (`PACKAGE_USAGE_STATS`, `READ_PHONE_STATE`, `FOREGROUND_SERVICE`).
- [ ] **30-Second Foreground Service:**
  - Measure real-time delta and instantaneous speed (KB/s, MB/s) using `TrafficStats`.
- [ ] **10-Minute WorkManager Worker:**
  - Measure durable bucketed cellular data using `NetworkStatsManager`.
  - Calculate remaining data balance against active promo.
- [ ] **Signal Strength Monitor:**
  - Listen via `TelephonyCallback` / `PhoneStateListener`.
- [ ] Persist snapshots to local Room database and queue for cloud sync.

#### Phase D5: Core Dashboard & Floating Navigation
- [ ] Fixed floating pill bottom nav (`Home`, `History`, `Settings`).
- [ ] **PET Hero Card (Centerpiece):** Prominent hours/days remaining countdown and predicted exhaustion timestamp.
- [ ] **Data Balance Gauge:** Remaining vs. consumed progress bar with status colors (`DataraNeonGreen`, `DataraPrimaryBlue`, `DataraError`).
- [ ] **Live Network Strip:** Carrier badge, live speed, and signal bars.
- [ ] **Active Promo Card:** Plan name and expiration timer.

#### Phase D6: On-Device Machine Learning & Dual-Model Execution
- [ ] Add `com.microsoft.onnxruntime:onnxruntime-android`.
- [ ] Bundle `global_xgboost.onnx` (from Track B) in `app/src/main/assets/models/`.
- [ ] Build on-device Feature Extractor from Room `DataUsage` entries.
- [ ] **Dual-Model Conditional Switch:**
  - `ModelType.GLOBAL`: Runs baseline pre-trained ONNX model for new users.
  - `ModelType.LOCAL`: Retrained or fine-tuned on-device personalized model once user has sufficient telemetry logs (e.g., >= 7 days).
  - *Strict rule:* Fully on-device inference via ONNX Runtime — no live `/predict` server call.
- [ ] Output predicted PET timestamp and expose via `StateFlow` to the UI.

#### Phase D7: Usage History & Analytics (Vico Charts)
- [ ] Add Vico Compose charting library.
- [ ] Build `HistoryScreen`:
  - Time-series charts for hourly and daily data usage.
  - Peak usage hours breakdown.
  - Average daily consumption rate.
  - Filters: 24h, 7 days, full promo cycle.

#### Phase D8: Proactive Alerts & Notification System
- [ ] High-priority notification channels for data exhaustion warnings.
- [ ] Triggers:
  - Critical PET window (e.g., predicted exhaustion in 4 hours).
  - Depletion milestones (80%, 90%, 100% of promo data).
- [ ] In-app notification center and Room `NotificationEntity` history.

#### Phase D9: Profile, Settings & Developer ML Diagnostics Screen
- [ ] Profile management (name, optional secondary phone number, dark theme).
- [ ] Settings (notification toggles, manual cloud sync, sign out).
- [ ] **Developer ML Diagnostics Screen (Hidden):**
  - Gated behind secret gesture (e.g., 5 taps on app version text).
  - Displays: active model mode (Global vs. Local), MAE, RMSE, R², input tensors, inference latency.

---

### Track E: Integration, Evaluation & Thesis Defense Deliverables
*Goal: Academic validation and presentation readiness for the USTP BSIT panel.*

- [ ] **Phase E1 — Offline Resilience Testing:** Validate that data tracking and PET inference function 100% offline in Airplane mode.
- [ ] **Phase E2 — Battery & Overhead Benchmarking:** Profile foreground service and background worker energy usage using Android Studio Energy Profiler.
- [ ] **Phase E3 — Global vs. Local Accuracy Comparison:** Compare PET error rate (actual vs. predicted exhaustion) under the Global model vs. Local personalized model.
- [ ] **Phase E4 — Unit & UI Automated Tests:**
  - JUnit 4 tests for ViewModel state transitions, dual-model switch logic, and Room queries.
  - Compose UI tests for Auth, Dashboard, and History flows.
- [ ] **Phase E5 — Capstone Manuscript & Presentation Assets:**
  - Export final evaluation tables (MAE, RMSE, R²).
  - Screen recordings and interactive demo scripts.
