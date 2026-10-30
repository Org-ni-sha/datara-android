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
- **Implemented as previews:** Home, Create Quota, Quota Details, History, Notifications, Settings and Profile. Sample forecasts, usage, notification preferences, name edits and sync states are not production integrations. Sign-out uses real Supabase Auth.
- **Historically applied:** C2 legacy promo-based Postgres schema, RLS and profile trigger. `supabase/` remains the schema source of truth; never edit applied migrations.
- **Pending:** quota-schema design/new migrations, Room, telemetry, durable cycles/history, inference, OS alert delivery, persistent profile/preferences and sync. Do not mark D2–D9 complete just because their UI exists.

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
| Charts | Vico Compose 3.3.1 — sample History UI; real telemetry integration pending |
| Launch | AndroidX core-splashscreen 1.2.0, native launch theme and Compose session loading |

### Planned — not yet a dependency

| Layer | Choice | Roadmap phase |
|---|---|---|
| Local DB | Room (SQLite), KSP compiler | D2 |
| Background work | WorkManager + Foreground Service — 30-sec (`TrafficStats`) and 15-min (`NetworkStatsManager`) polling | D4 |
| On-device inference | ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android`) | D6 |

**Auth OTP note:** password recovery does use a one-time code, but it is an *email* OTP (`verifyEmailOtp(OtpType.Email.RECOVERY)` in `AuthRepository`). That is intended and correct. The banned pattern is *phone/SMS* OTP as a login identifier.

Package root: `com.capstone.datara`

---

## Build rules — easy to get wrong

- **Use KSP, never kapt.** Hilt already uses `ksp(libs.hilt.compiler)`. When Room is added (D2), it must be `ksp(libs.room.compiler)` too.
- **All dependencies go through the version catalog** at [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Never hardcode a version string in `app/build.gradle.kts`.
- **SDK levels:** `minSdk = 26` (hard requirement — do not lower), `compileSdk = 37`, `targetSdk = 37`. Java/JVM target 11.
- **AGP is 9.3.2**, which uses newer DSL syntax already present in `app/build.gradle.kts` — `compileSdk { version = release(37) }` and `release { optimization { enable = false } }`. These are correct for AGP 9; do not "fix" them back to the older `compileSdk = 37` / `isMinifyEnabled` form.
- **The manifest currently declares only `INTERNET`.** Every additional permission (`READ_PHONE_STATE`, `PACKAGE_USAGE_STATS`, `FOREGROUND_SERVICE`, …) is a scope decision — see Scope Boundaries. Add them only when the corresponding roadmap phase is actually being built, and never add a permission the roadmap doesn't call for.

---

## Project structure

### What exists today

```
app/src/main/java/com/capstone/datara/
├── DataraApplication.kt              # @HiltAndroidApp
├── MainActivity.kt                   # @AndroidEntryPoint, session gate + DataraNavGraph()
├── data/
│   ├── remote/SupabaseClientProvider.kt
│   └── repository/AuthRepository.kt
├── di/SupabaseModule.kt              # Hilt @Module
└── ui/
    ├── auth/
    │   ├── AuthViewModel.kt          # @HiltViewModel
    │   ├── AuthErrorParser.kt        # error text + AuthValidator (pure form rules)
    │   ├── LoginScreen.kt
    │   ├── RegisterScreen.kt
    │   ├── ForgotPasswordScreen.kt
    │   └── components/               # DataraAuthComponents, DataraIcons, DataraLogo
    ├── session/                      # SessionState (pure mapper) + SessionViewModel
    ├── splash/SplashScreen.kt        # shown while the session is restored
    ├── dashboard/                    # Home, signed-in shell and sample usage bars
    ├── quota/                        # Create/Details, sample state and navigation
    ├── history/                      # Vico sample charts and cycle details
    ├── notifications/                # Sample alerts and read state
    ├── settings/, profile/           # Preview preferences/profile; real sign-out
    ├── components/                   # Theme-aware shared Material 3 wrappers
    ├── navigation/NavGraph.kt        # DataraRoute constants + start destination
    └── theme/                        # Color.kt, Theme.kt, Type.kt
```

Tests cover auth validation/errors, quota input and sample-state behavior, History fixtures, Notifications and Settings. Compose tests cover quota screens, History, Notifications, Settings/Profile and branded session loading, including enlarged-font checks. These do not validate production telemetry or ML.

### Planned infrastructure

`data/local/` (Room), background telemetry, sync and inference are still pending. `ui/promos/` is no longer planned: quota setup replaces the former catalog flow.

---

## Secrets — critical, do not violate

- Supabase URL and anon key live in `local.properties` (git-ignored) and are exposed to code via `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
- **Never hardcode a Supabase URL, key, or any credential directly in a `.kt` file.** If a task seems to require this, stop and flag it instead of doing it.
- Never commit `local.properties`. If you ever see it staged in a diff, flag it before proceeding.
- When you introduce a new key, add a placeholder line for it to `local.properties.example` in the same change, so teammates' builds don't silently break.
- `screen-reference/` (design mockups) is also git-ignored.

---

## Data model (applied legacy schema; quota design pending)

The Supabase schema (C2) is written as SQL migrations in [`supabase/`](supabase/) — read
[`supabase/README.md`](supabase/README.md) for the Postgres↔Kotlin type mapping before writing
the Room entities. The Room side (D2) does not exist yet. The following describes the seven legacy schema concepts, not existing Room entities or a finalized quota schema:

- **User** — id, email, name, created_at (no address field, no raw ID exposed in UI — see Scope Boundaries)
- **Device** — device_id, device_name, device_model, android_version
- **DataUsage** — data_usage_id, data_consumed_gb, data_remaining_gb, usage_date, signal_strength
- **Notification** — notification_id, notification_type, title, message, date_sent, is_read
- **Provider** — provider_id, provider_name (auto-detected via `TelephonyManager`, not user-entered)
- **Promo** — promo_id, promo_name, data_amount_gb, promo_price, validity_days, expiry_date, promo_description
- **Association** — junction table linking user_id, device_id, provider_id, promo_id, notification_id, data_usage_id, total_data_used_gb

Before D2/D3 persistence, design quota/cycle relationships and add new C2 migrations with RLS and compatibility handling. Keep deployed promo tables until an explicit migration handles them; do not create a Promo-based quota model merely to match this historical list.

Rules:
- Once Room and sync exist, keep persisted fields and mappings aligned with their corresponding Supabase migrations. Do not invent Room entities as part of UI-only work.
- Every Supabase table carries **Row Level Security scoped to `user_id`**. Don't create a table without its RLS policy. (`providers` is the one documented exception — shared reference data, read-only to the client.)
- Timestamps are `timestamptz` in Postgres and `Long` epoch millis in Room, converted in the sync mapper.
- Row primary keys are UUIDs generated client-side so sync retries upsert instead of duplicating.

---

## Design system

- **Authentication and launch:** dark navy (`#0C101A` / `DataraDarkBg`) background, card/input surface (`#141B2B` / `DataraCardBg`).
- Authentication retains the dark navy `DATAraTheme`. The user-approved quota UI uses the scoped **light `QuotaTheme`**: off-white background, white surfaces, navy text and dark navy accents. Home, Create Quota, Quota Details, History, Notifications, Settings and Profile use this theme. No theme toggle is planned.
- Always use the semantic brand tokens defined in `ui/theme/Color.kt` (`DataraDarkBg`, `DataraCardBg`, `DataraInputBg`, `DataraInputBorder`, `DataraTextSecondary`, etc.) — never a raw hex literal inside a screen.
- In quota screens, use `MaterialTheme.colorScheme` roles mapped by `QuotaTheme` to the light brand tokens. Shared components must respect their enclosing theme.
- Authentication accent colors: vibrant/electric blue (`#1E64FA` / `DataraPrimaryBlue`, `#2F6BFF` / `DataraNeonBlue`), lime green (`#CEFF37` / `DataraNeonGreen`) for positive status, red (`#EF4444` / `DataraError`) for errors.
- Typography: use `MaterialTheme.typography` styles backed by `DataraFontFamily` (currently `FontFamily.SansSerif`, chosen for offline/emulator reliability).
- Authentication reuses `ui/auth/components/DataraAuthComponents.kt`. Signed-in quota screens use native Material 3 controls and shared wrappers in `ui/components/DataraUi.kt`. Outline icons remain in `DataraIcons.kt`.
- Quota shapes use a hierarchy: 16dp controls and inset panels, 20dp cards, 24dp forecast hero; smaller icon and label containers use 8–14dp. Direction C refinement favors lighter white surfaces with soft blue grouping.
- Quota navigation uses Material 3 `NavigationBar` / `NavigationBarItem` inside a floating rounded surface (Home / History / Settings). Profile selects Settings. Authentication has no bottom navigation.
- Navy remains the primary quota accent for text and actions. Accessible teal highlights progress/status, with soft blue inset panels and mint selected states. The forecast hero is a white surface. Authentication retains its existing palette.
- UI fixtures remain labeled as sample data; scenario controls live under the top-bar **Preview options** menu. See `docs/ui-redesign.md` for rationale, inspiration and verification.


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

- **PET** means predicted quota-reaching time in the approved direction. Make it prominent on Home when available; show collecting/unavailable states honestly. Current displayed forecasts are fixtures, not inference.
- Planned D6 behavior: new users with sufficient valid inputs get predictions from the **Global model**. Once a user has enough local usage history (roughly 7 days of telemetry), the app switches to the **Local personalization layer**. Don't hardcode "always use global" or "always use local" — this is a conditional, reversible switch (see the thesis's Fig. 1 data flow and ROADMAP D6).
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

- The `"dashboard"` route renders `DashboardScreen` in `ui/dashboard/HomeScreen.kt`, which hosts the signed-in quota flow. The old placeholder is gone. Preview state uses SavedStateHandle; it is not a durable quota database. History and alert fixtures are independent of Home actions.
- Routes are constants on `DataraRoute`, not string literals. Login and register clear the whole back stack on success (`popUpTo(0)`), so back from the dashboard exits the app rather than returning to a login screen with a live session.
- Auto-login is live: `MainActivity` gates on `SessionViewModel.sessionState` and shows `SplashScreen` while Supabase restores a persisted session. `SessionStatus.RefreshFailure` is deliberately treated as unauthenticated — a stale refresh token means signing in again, not landing on a dashboard whose requests all 401.
- The `public.users` row is created by the `on_auth_user_created` Postgres trigger, not by a client insert. `AuthRepository.signUp` passes the name as auth metadata (`raw_user_meta_data`) so the trigger can populate it — if you change the sign-up call, keep that metadata key or `users.name` silently becomes null.

---

## When in doubt

This is a capstone thesis project. The approved quota direction supersedes older promo-based product instructions; Chapters 1–3 remain historical references until their documented alignment is completed. If a request would expand scope, add a new permission, or change something already defended to a panel (dataset structure, prediction approach, privacy claims), flag it explicitly rather than silently implementing it.
