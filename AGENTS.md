# AGENTS.md — DATAra

Guidance for AI coding agents (Antigravity, Claude Code, Cursor, etc.) working in this repository. Read this before making changes.

---

## What this project is

DATAra is an Android app for **user-defined usage-quota forecasting**, an approved direction confirmed on 2026-10-29. Users choose an amount in MB/GB; the planned forecast estimates when device-measured mobile usage reaches it. PET refers to that quota-reaching time, not carrier balance depletion or promo expiry. Freebie/zero-rated traffic counts when measured by the device. The app does not identify billing buckets, reconcile carrier balances, or enforce network caps.

It is a BSIT capstone project for USTP Cagayan de Oro City. The planned prediction pipeline uses a pre-trained **Global XGBoost model** and a **Local personalization layer**. Its exact form remains open — see [ROADMAP.md §2](ROADMAP.md). The manuscript and research protocol still need alignment with the approved quota direction; do not invent a replacement thesis title.

This repo is the Android client only. The GitHub repo is `Org-ni-sha/datara-android`; the local working folder is named `DATAra`. Related repos in the same GitHub org:
- `data-harvester` — the standalone dataset-collection app used during research (do not modify unless explicitly asked; it's a separate deliverable)
- `datara-ml` — Python training pipeline (cleaning, XGBoost training, ONNX export)

---

## Where we are now — read ROADMAP.md first

[`ROADMAP.md`](ROADMAP.md) is the build-order source of truth. It tracks five parallel tracks (A–E) and, for this repo, phases **D1–D9** with live checkboxes. **Check it before starting any task** to see which phase is current and what that phase actually requires.

As of 2026-10-29:

- **Implemented:** D1 authentication (email/username + password, Google, username screen and `NeedsUsername` gate), session routing, official logo/launcher icons and native splash plus session-loading UI.
- **Implemented as previews:** Home, Create Quota, Quota Details, Settings and Profile. Sample forecasts, usage and sync states are not production integrations. Sign-out uses real Supabase Auth, and the Profile name and username are read from and saved to `public.users`.
- **Applied to the hosted project:** the C2 quota schema (`20261104000001`, pushed 2026-11-04), which replaced the legacy promo tables, plus the earlier RLS, profile trigger and usernames migrations. `supabase/` remains the schema source of truth; never edit applied migrations.
- **Implemented (D4):** the telemetry foreground service (30 s `TrafficStats`, 10 min `NetworkStatsManager`, 15 min watchdog), signal/network/carrier inputs, the Tracking setup screen and its permissions — see [usage telemetry](docs/telemetry.md).
- **Implemented (D2):** Room `DataraDatabase` with the eight synced entities and the four phone-only telemetry tables, DAOs with the upload queue, Room-backed telemetry storage, the 10-minute `data_usage` rollup and device registration. No screen reads it yet, and nothing uploads it (C3).
- **Implemented (D3):** quota cycles — `TrackingCycle` domain model and `TrackingCycleRepository` seam (Room-backed via `TrackingCycleDao`), pure cycle math (`domain/cycle/CycleMath`, `CycleRules`) and `QuotaCycleManager` use cases. Create Quota and Quota Details use real, durable cycles; Home stays on fixtures until D5 — see [quota cycles](docs/quota-cycles.md).
- **Implemented (D6):** on-device forecasting — ONNX Runtime runs the bundled Global model (`assets/models/global_xgboost.onnx`, currently the interim `v0-tweedie-pet-2026-11-07`, a Tweedie model trained on about a week of harvester data from 17 devices with hyperparameters chosen by PET error), `FeatureExtractor` reproduces datara-ml's feature contract v4 (parity-tested; hourly bytes from NetworkStatsManager `durable_readings`, not the 30-second samples — see datara-ml `docs/measurement-gaps.md`), the local residual layer and reversible GLOBAL/LOCAL switch live in `user_model_state`, the PET projection logs one `forecasts` row per active cycle per hour, and Home's hero and Quota Details show the real PET — see [forecast](docs/forecast.md).
- **Implemented (D8):** quota alerts — `alerts/` (`AlertRules`, `QuotaAlertEngine`, channels and delivery in `QuotaAlertNotifications`): 75%, 90% and 100% milestones and 24 h / 3 h forecast alerts, each once per cycle (Room unique index), evaluated on the telemetry/forecast cadence, never while the forecast is collecting or beyond 30 days. The notification center reads Room `notifications` with real `read_at`; the Settings switches persist in `notification_preferences` — see [quota alerts](docs/notifications.md).
- **Implemented (D7):** History on durable data — `ui/history/` (`HistoryAggregation`, `HistoryViewModel`): 24-hour and 7-day charts and per-cycle daily charts from the NetworkStatsManager hourly rule the forecast uses (`HourlyUsage`), unmeasured hours shown as gaps, every cycle's progress and read-only details, and the logged PET against the actual reach — see [history](docs/history.md).
- **Implemented (D9):** the hidden ML diagnostics screen (tap the Settings version 7 times; `ui/diagnostics/`), real tracking status checks in Settings, and an honest "Not synced yet" sync card in place of the simulated sync states. See [diagnostics](docs/diagnostics.md).
- **Pending:** the final 14-day model (a file swap) and sync (C3). Do not mark D5 complete just because its UI exists.

The current implementation details and limitations are in [quota UI preview](docs/quota-ui-preview.md).

---

## Tech stack — do not substitute without asking

### Currently in the build

| Layer | Choice |
|---|---|
| Language | Kotlin 2.4.0 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3 |
| Architecture | MVVM — ViewModel + StateFlow, no LiveData |
| DI | Hilt 2.60.1 (annotation processing via **KSP**, not kapt) |
| Backend | Supabase 3.8.0 — `auth-kt`, `postgrest-kt`, `storage-kt` — **not** Firebase |
| Auth | Email or username + password, and Google (Credential Manager ID token), via Supabase Auth — **not** phone/SMS OTP (see note below). Username login goes through the `username-login` Edge Function. |
| Networking | Ktor `ktor-client-android` 3.0.3, declared explicitly as the engine for `supabase-kt` |
| Serialization | `kotlinx-serialization-json` |
| Navigation | `navigation-compose` + `hilt-navigation-compose` |
| Testing | JUnit 4, Compose UI Test |
| Charts | Vico Compose 3.3.1 — History charts over durable usage (D7) |
| Launch | AndroidX core-splashscreen 1.2.0, native launch theme and Compose session loading |
| Local DB | Room 2.8.5 (SQLite), compiler via KSP, schemas exported to `app/schemas/` |
| Background work | `specialUse` foreground service sampling `TrafficStats` every 30 s and `NetworkStatsManager` every 10 min (matching `data-harvester`), plus a 15-min WorkManager 2.11.2 watchdog — ROADMAP Decision 1 |
| On-device inference | ONNX Runtime 1.31.1 (`onnxruntime-android`; JVM `onnxruntime` for unit tests only) — D6 |

**Auth OTP note:** password recovery does use a one-time code, but it is an *email* OTP (`verifyEmailOtp(OtpType.Email.RECOVERY)` in `AuthRepository`). That is intended and correct. The banned pattern is *phone/SMS* OTP as a login identifier.

Package root: `com.capstone.datara`

---

## Build rules — easy to get wrong

- **Use KSP, never kapt.** Hilt already uses `ksp(libs.hilt.compiler)`. Room uses `ksp(libs.androidx.room.compiler)` too.
- **All dependencies go through the version catalog** at [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Never hardcode a version string in `app/build.gradle.kts`.
- **SDK levels:** `minSdk = 26` (hard requirement — do not lower), `compileSdk = 37`, `targetSdk = 37`. Java/JVM target 11.
- **AGP is 9.3.2**, which uses newer DSL syntax already present in `app/build.gradle.kts` — `compileSdk { version = release(37) }` and `release { optimization { enable = false } }`. These are correct for AGP 9; do not "fix" them back to the older `compileSdk = 37` / `isMinifyEnabled` form.
- **The manifest declares `INTERNET` plus the D4 tracking permissions** (foreground service `specialUse`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS`, `POST_NOTIFICATIONS`, `READ_PHONE_STATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), each commented with its reason; see [usage telemetry](docs/telemetry.md). Every further permission is a scope decision — see Scope Boundaries. Add one only when its roadmap phase is actually being built, and never add a permission the roadmap doesn't call for.

---

## Project structure

### What exists today

```
app/src/main/java/com/capstone/datara/
├── DataraApplication.kt              # @HiltAndroidApp
├── MainActivity.kt                   # @AndroidEntryPoint, session gate + DataraNavGraph()
├── data/
│   ├── local/                        # Room: DataraDatabase, entity/, dao/, device/DeviceRepository
│   ├── remote/SupabaseClientProvider.kt
│   ├── repository/AuthRepository.kt
│   ├── session/CurrentUserSource.kt  # signed-in user id for background work
│   └── telemetry/                    # TelemetryRepository (Room), UsageRollup to data_usage
├── di/                               # Hilt modules: Supabase, Database, Telemetry
└── ui/
    ├── auth/
    │   ├── AuthViewModel.kt          # @HiltViewModel
    │   ├── AuthErrorParser.kt        # error text + AuthValidator (pure form rules)
    │   ├── AuthEntryScreen.kt        # Welcome + animated Login/Register panel
    │   ├── UsernameScreen.kt
    │   ├── ForgotPasswordScreen.kt
    │   └── components/               # DataraAuthComponents, DataraIcons, DataraLogo
    ├── session/                      # SessionState (pure mapper) + SessionViewModel
    ├── splash/SplashScreen.kt        # shown while the session is restored
    ├── dashboard/                    # Home, signed-in shell and sample usage bars
    ├── quota/                        # Create/Details, sample state and navigation
    ├── history/                      # Real usage charts and cycle history (D7)
    ├── notifications/                # Alert inbox over Room notifications (D8)
    ├── settings/, profile/           # Real tracking status, alert switches, profile name/username, sign-out
    ├── diagnostics/                  # Hidden ML diagnostics (D9; 7 taps on the Settings version)
    ├── components/                   # Theme-aware shared Material 3 wrappers
    ├── navigation/NavGraph.kt        # DataraRoute constants + start destination
    └── theme/                        # Color.kt, Theme.kt, Type.kt
```

Tests cover auth validation/errors, quota input and sample-state behavior, History aggregation (gaps, daily totals, cycle progress, PET comparison), Notifications and Settings. Compose tests cover quota screens, History, Notifications, Settings/Profile and branded session loading, including enlarged-font checks. These do not validate production telemetry or ML.

### Planned infrastructure

Sync (C3) is still pending. Quota alerts (D8) live in `alerts/` (rules, wording, engine, Room stores, Android channels and tap navigation). Forecasting (D6) lives in `forecast/` (feature contract, extractor, projector, ONNX model, local layer, `ForecastEngine`). Telemetry lives in `telemetry/` (service, rules, device readers), `data/telemetry/` (Room-backed repository and rollup) and `ui/tracking/` (setup screen). The telemetry repository contract is in `src/sharedTest/` and runs on the JVM (in memory) and on a device (Room; see `src/androidTest/.../data/local/`). `ui/promos/` is no longer planned: quota setup replaces the former catalog flow.

---

## Secrets — critical, do not violate

- Supabase URL and anon key live in `local.properties` (git-ignored) and are exposed to code via `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
- **Never hardcode a Supabase URL, key, or any credential directly in a `.kt` file.** If a task seems to require this, stop and flag it instead of doing it.
- Never commit `local.properties`. If you ever see it staged in a diff, flag it before proceeding.
- When you introduce a new key, add a placeholder line for it to `local.properties.example` in the same change, so teammates' builds don't silently break.
- `screen-reference/` (design mockups) is also git-ignored.

---

## Data model (quota schema, migration `20261104000001`)

The Supabase schema is written as SQL migrations in [`supabase/`](supabase/), built from [`docs/erd-draft.md`](docs/erd-draft.md) — read [`supabase/README.md`](supabase/README.md) for the table rules and the Postgres↔Kotlin type mapping before changing a Room entity. The Room entities in `data/local/entity/` match these tables column for column; a schema change needs both a Supabase migration and a Room `Migration` (bump `DataraDatabase.version`, commit the exported schema).

Synced tables (Room entity name in brackets):

- **users** (`User`) — id, email, username, name, phone_number (optional, user-entered, E.164 `+639XXXXXXXXX`), created_at. No address field, no raw ID exposed in UI — see Scope Boundaries.
- **devices** (`Device`) — device_id (one per install), user_id, device_model, android_sdk_int, device_name, created_at.
- **tracking_cycles** (`TrackingCycle`) — cycle_id, user_id, device_id, name, quota_bytes, start_at, reached_at, ended_at, status (`active`/`reached`/`ended`), created_at, updated_at. One active cycle per device.
- **data_usage** (`DataUsage`) — one row per 10-minute window: data_usage_id, user_id, device_id, interval_start_at, interval_end_at, bytes_rx, bytes_tx, measurement_status, network_type, signal_dbm (null when unknown), carrier (detected, never entered), schema_version. Unique `(device_id, interval_start_at)`.
- **forecasts** (`Forecast`) — one row per active cycle per hour, both models: forecast_id, user_id, device_id, cycle_id, predicted_at, remaining_bytes, active_model, global_next_hour_bytes, global_reach_at, local_next_hour_bytes, local_reach_at, model_version, inputs (jsonb). Unique `(cycle_id, predicted_at)`.
- **notifications** (`Notification`) — notification_id, user_id, cycle_id, notification_type (`milestone_75`, `milestone_90`, `quota_reached`, `forecast_24h`, `forecast_3h`), title, message, sent_at, read_at (null = unread). Each type once per cycle.
- **notification_preferences** (`NotificationPreferences`) — user_id (PK), milestones_enabled, forecast_alerts_enabled, updated_at.
- **user_model_state** (`UserModelState`) — user_id (PK), active_model, parameters (jsonb), device_id, hours_seen, global_recent_mae, local_recent_mae, updated_at.

Phone-only Room tables for D4 telemetry (`usage_samples`, `durable_readings`, `telemetry_checkpoint`, `durable_checkpoint`) are never synced; see the ERD draft. The legacy `providers`, `promos`, `associations` and the old `data_usage`/`notifications` are dropped by the quota migration — do not bring them back.

Rules:
- Keep Room entities aligned with their Supabase migrations. Do not invent Room entities as part of UI-only work.
- Room has no partial indices, so the one-active-cycle-per-device rule is two triggers (`DataraDatabase.ONE_ACTIVE_CYCLE_TRIGGERS`); a migration that rebuilds `tracking_cycles` must recreate them. There are no local foreign keys; Postgres checks ownership on upload.
- Every Supabase table carries **Row Level Security scoped to `user_id`** (`users` by `id`), granted to `authenticated` only. Don't create a table without its RLS policy. There is no shared reference table.
- Child rows reference parents with composite `(…_id, user_id)` foreign keys so a row cannot point at another user's device or cycle. Keep that pattern for new tables.
- Amounts are bytes as `bigint` / `Long`; MB is display-only (1 MB = 1,000,000 bytes).
- Timestamps are `timestamptz` in Postgres and `Long` epoch millis in Room, converted in the sync mapper.
- Row primary keys are UUIDs generated client-side so sync retries upsert instead of duplicating. Each synced Room entity adds a local-only `synced_at: Long?`.

---

## Design system

- **Light app, navy front door (2026-11-03):** `DATAraTheme` delegates to `QuotaTheme` (off-white `#F5F7FB` background, white surfaces, navy text and accents) for every form and signed-in screen. The launcher icon, native splash, session-loading screen and welcome screen share brand navy `DataraBrandNavy` (`#101522`) via `WelcomeTheme` (`ui/theme/WelcomeTheme.kt`), so launch reads as one surface; the logo then animates from the splash position into the welcome layout. No dark theme or toggle is planned.
- Use `MaterialTheme.colorScheme` roles in screens, backed by the semantic tokens in `ui/theme/Color.kt` — never a raw hex literal inside a screen. The old dark auth tokens (`DataraDarkBg`, `DataraCardBg`, `DataraNeonBlue`, …) remain defined but are no longer used by screens.
- In quota screens, use `MaterialTheme.colorScheme` roles mapped by `QuotaTheme` to the light brand tokens. Shared components must respect their enclosing theme.
- Typography: use `MaterialTheme.typography` styles backed by `DataraFontFamily` (currently `FontFamily.SansSerif`, chosen for offline/emulator reliability).
- Authentication reuses `ui/auth/components/DataraAuthComponents.kt` (16dp controls, autofill `contentType` on every field). The navy welcome screen (Log in / Create account only) raises an in-screen panel for Login/Register rather than a `ModalBottomSheet`; the panel is a white sheet in the light theme and is the only place Google sign-in appears. Forgot password (email and code on one card, then new password) and Choose a username are full navy screens with the form in a floating white card, via the shared `AuthCardScaffold`. `SplashMatchedLogoSize` (in `DataraLogo.kt`) must be recomputed if the splash vector or logo PNG scale changes. See `AuthEntryScreen`. Signed-in quota screens use native Material 3 controls and shared wrappers in `ui/components/DataraUi.kt`. Outline icons remain in `DataraIcons.kt`.
- Quota shapes use a hierarchy: 16dp controls and inset panels, 20dp cards, 24dp forecast hero; smaller icon and label containers use 8–14dp. Direction C refinement favors lighter white surfaces with soft blue grouping.
- **Light header, navy docked bar (2026-11-08):** every signed-in screen uses `DataraAppBar` (`ui/components/DataraUi.kt`): a light bar on the app background with a navy title (and back where applicable) and dark status-bar icons. Home's header is the small D mark, a time-of-day greeting with the profile name (else @username) and a "Tracking “…”" subtitle; the DATAra wordmark stays on splash/welcome only. Quota navigation is a docked, full-width `DataraBrandNavy` Material 3 `NavigationBar` (`ui/quota/QuotaBottomBar.kt`: mint pill behind a navy icon, white label, unselected white at 70%, light navigation-bar icons, system insets) for Home / History / Settings. Profile selects Settings. Authentication has no bottom navigation. Live status chips use the shared `StatusPill`.
- Navy remains the primary quota accent for text and actions. Accessible teal highlights progress/status, with soft blue inset panels and mint selected states. The forecast hero is a white surface. Authentication uses the same palette.
- UI fixtures remain labeled as sample data; scenario controls live under the top-bar **Preview options** menu, a review-only tool (removed before the freeze). A real cycle shows real data until a scenario is picked; "Show my real quota" returns to it. See `docs/ui-redesign.md` for rationale, inspiration and verification.


---

## Scope boundaries — things NOT to build without explicit confirmation

These were deliberately cut during design review because they fall outside the thesis objectives or conflict with the project's stated privacy compliance (RA 10173). Do not reintroduce them just because they seem like reasonable features:

- ❌ **No SMS reading** or promo auto-detection. The approved flow uses a manually chosen tracking quota, not a promo catalog. `READ_SMS`/`RECEIVE_SMS` permissions are off-limits.
- ❌ **No address field** or other unnecessary PII collection on the User entity.
- ❌ **No raw user ID/UUID shown in the main UI.**
- ✅ **User-defined tracking quotas are approved.** Users choose an amount to forecast against.
- ❌ **No network enforcement** (`NetworkPolicyManager` restrictions), throttling or blocking other apps when the quota is reached.
- ❌ **No standalone data-usage calculator** unrelated to prediction.
- ⚠️ **ML diagnostics (MAE/RMSE/R² display) do not belong in the main Settings menu** — gate them behind the hidden dev-only screen (D9, secret gesture), since end users (prepaid students/professionals) aren't the audience for model metrics.
- ✅ Phone number, if present at all, is an **optional secondary profile field** — email (or the username that maps to it) with a password, or Google, is the login identifier, not phone/SMS OTP.

If a task description conflicts with this list, ask before proceeding rather than assuming the older behavior is still correct.

---

## Prediction logic — do not oversimplify

- **PET** means predicted quota-reaching time in the approved direction. Make it prominent on Home when available; show collecting/unavailable states honestly. Home's hero and Quota Details show the on-device forecast for a real active cycle; the other Home preview scenarios are fixtures.
- **Decided (ROADMAP Decision 2):** the Global model predicts **next-hour mobile usage**; the app computes the PET by projecting hourly predictions against the remaining quota. Do not train or wire a model that outputs the PET directly.
- Planned D6 behavior: new users with sufficient valid inputs get predictions from the **Global model**. Once a user has enough local usage history (roughly 7 days of telemetry), the app switches to the **Local personalization layer**. Don't hardcode "always use global" or "always use local" — this is a conditional, reversible switch (see the thesis's Fig. 1 data flow and ROADMAP D6).
- **The feature list is frozen** by datara-ml `docs/feature-contract.md` (v4: hourly usage from NetworkStatsManager, not TrafficStats; zero-byte readings count at any length; the model predicts mean bytes with the Tweedie loss and its output transform is in its metadata). `FeatureContract`/`FeatureExtractor`/`PetProjector` must match `datara_ml/features.py`; `FeatureParityTest` replays its golden fixture. A new model trained on the same contract is a file swap (model + fixture), never a code change.
- **On-device _inference_ is non-negotiable; on-device _training_ is not a given.** ONNX Runtime does inference only for tree ensembles — there is no on-device XGBoost retraining path. ROADMAP §2 (Decision 2) holds the options; don't write code that assumes local XGBoost training works.
- Planned predictions run **on-device via ONNX Runtime**, not via a live network call to a prediction server. If a task asks you to add a `/predict` API call, confirm that's actually intended first — it would contradict the offline-first design already documented. (Downloading an updated *model file* from Supabase Storage is not a `/predict` call and doesn't violate this.)
- Network factors (signal strength, internet speed) are model inputs, not just display metadata — don't drop them when refactoring the prediction pipeline.

---

## Git conventions

[`CONTRIBUTING.md`](CONTRIBUTING.md) is the source of truth for branching, commits, and PRs — read it rather than relying on the summary here.

The short version: branch off `main` as `<type>/<short-description>` (`feature/`, `fix/`, `style/`, `refactor/`, `test/`, `chore/`, `docs/`); commit as `type(scope): summary` (Conventional Commits); open a PR into `main` using the template, get at least one approval, squash-merge, delete the branch. `main` has branch protection — no direct pushes, and it must always build.

---

## Testing expectations

- Unit tests: JUnit 4, ViewModel logic in particular (auth state transitions, and the prediction-switch logic once D6 lands).
- UI tests: Compose UI Test for critical flows (login, register, dashboard renders PET).
- Before marking a task done, the project should still compile and existing tests should still pass — **run them, don't assume**:

```bash
./gradlew test            # unit tests   (gradlew.bat on Windows)
./gradlew assembleDebug   # compile check
```

---

## Known current state / traps

- The `"dashboard"` route renders `DashboardScreen` in `ui/dashboard/HomeScreen.kt`, which hosts the signed-in quota flow. The old placeholder is gone. Preview state uses SavedStateHandle; it is not a durable quota database. History reads real cycles and readings (D7); alerts are real (D8). Home's 7-day trend card is still a sample.
- Routes are constants on `DataraRoute`, not string literals. Login and register clear the whole back stack on success (`popUpTo(0)`), so back from the dashboard exits the app rather than returning to a login screen with a live session.
- Auto-login is live: `MainActivity` gates on `SessionViewModel.sessionState` and shows `SplashScreen` while Supabase restores a persisted session. `SessionStatus.RefreshFailure` is deliberately treated as unauthenticated — a stale refresh token means signing in again, not landing on a dashboard whose requests all 401.
- The `public.users` row is created by the `on_auth_user_created` Postgres trigger, not by a client insert. `AuthRepository.signUp` passes the name as auth metadata (`raw_user_meta_data`) so the trigger can populate it — if you change the sign-up call, keep that metadata key or `users.name` silently becomes null.

---

## When in doubt

This is a capstone thesis project. The approved quota direction supersedes older promo-based product instructions; Chapters 1–3 remain historical references until their documented alignment is completed. If a request would expand scope, add a new permission, or change something already defended to a panel (dataset structure, prediction approach, privacy claims), flag it explicitly rather than silently implementing it.
