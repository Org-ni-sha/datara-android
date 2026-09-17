# AGENTS.md — DATAra

Guidance for AI coding agents (Antigravity, Claude Code, Cursor, etc.) working in this repository. Read this before making changes.

---

## What this project is

DATAra is an Android app that predicts when a prepaid mobile user's data will run out (Probable Exhaustion Time / PET), based on usage behavior, time-based patterns, and network conditions (signal strength, internet speed). It's a BSIT capstone project for USTP Cagayan de Oro City. The prediction is powered by a dual-layer model: a pre-trained **Global XGBoost model** (baseline, trained on aggregated usage data) and a **Local personalization layer** that adapts the prediction to each user's own usage over time. The exact form of that local layer is an open decision — see [ROADMAP.md §2](ROADMAP.md) before building it.

This repo is the Android client only (referred to as `datara-android` in planning docs; the folder and GitHub repo are named `DATAra`). Related repos in the same GitHub org:
- `data-harvester` — the standalone dataset-collection app used during research (do not modify unless explicitly asked; it's a separate deliverable)
- `datara-ml` — Python training pipeline (cleaning, XGBoost training, ONNX export)

---

## Where we are now — read ROADMAP.md first

[`ROADMAP.md`](ROADMAP.md) is the build-order source of truth. It tracks five parallel tracks (A–E) and, for this repo, phases **D1–D9** with live checkboxes. **Check it before starting any task** to see which phase is current and what that phase actually requires.

As of this writing:

- **Done:** D1 — Auth & app shell (Supabase email/password, dark navy Login/Register/Forgot Password, `AuthRepository`, `AuthViewModel`, Hilt wiring).
- **Open in D1:** session auto-login persistence on launch; initial user record creation in Supabase on sign-up.
- **Not started:** D2 (Room + `TelephonyManager` detection) onward — which is most of the app.

Everything below marked *planned* does not exist in the codebase yet. Do not assume it compiles.

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
| Auth | Email + password via Supabase Auth — **not** phone/SMS OTP (see note below) |
| Networking | Ktor `ktor-client-android` 3.0.3, declared explicitly as the engine for `supabase-kt` |
| Serialization | `kotlinx-serialization-json` |
| Navigation | `navigation-compose` + `hilt-navigation-compose` |
| Testing | JUnit 4, Compose UI Test |

### Planned — not yet a dependency

| Layer | Choice | Roadmap phase |
|---|---|---|
| Local DB | Room (SQLite), KSP compiler | D2 |
| Background work | WorkManager + Foreground Service — 30-sec (`TrafficStats`) and 15-min (`NetworkStatsManager`) polling | D4 |
| On-device inference | ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android`) | D6 |
| Charts | **Vico** (Compose-native) — decided; do not swap in MPAndroidChart | D7 |

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
├── MainActivity.kt                   # @AndroidEntryPoint, calls DataraNavGraph()
├── data/
│   ├── remote/SupabaseClientProvider.kt
│   └── repository/AuthRepository.kt
├── di/SupabaseModule.kt              # Hilt @Module
└── ui/
    ├── auth/
    │   ├── AuthViewModel.kt          # @HiltViewModel
    │   ├── AuthErrorParser.kt        # maps Supabase errors to user-facing text
    │   ├── LoginScreen.kt
    │   ├── RegisterScreen.kt
    │   ├── ForgotPasswordScreen.kt
    │   └── components/               # DataraAuthComponents, DataraIcons, DataraLogo
    ├── navigation/NavGraph.kt
    └── theme/                        # Color.kt, Theme.kt, Type.kt
```

Tests: `app/src/test/.../AuthErrorParserTest.kt`, plus the generated `ExampleUnitTest` / `ExampleInstrumentedTest`.

### Planned packages (create as their phase comes up)

```
data/local/          # Room entities, DAOs, DataraDatabase            (D2)
ui/promos/           # carrier promo catalog + manual entry           (D3)
ui/dashboard/        # PET hero card, balance gauge, network strip    (D5)
ui/history/          # Vico charts                                    (D7)
ui/profile/, ui/settings/                                             (D9)
```

---

## Secrets — critical, do not violate

- Supabase URL and anon key live in `local.properties` (git-ignored) and are exposed to code via `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
- **Never hardcode a Supabase URL, key, or any credential directly in a `.kt` file.** If a task seems to require this, stop and flag it instead of doing it.
- Never commit `local.properties`. If you ever see it staged in a diff, flag it before proceeding.
- When you introduce a new key, add a placeholder line for it to `local.properties.example` in the same change, so teammates' builds don't silently break.
- `screen-reference/` (design mockups) is also git-ignored.

---

## Data model (Room / Postgres — keep both in sync)

Neither side exists yet: Room entities are phase D2, the Supabase schema is phase C2. When they are built, these are the seven entities, mirrored between local Room tables and the Supabase Postgres schema:

- **User** — id, email, name, created_at (no address field, no raw ID exposed in UI — see Scope Boundaries)
- **Device** — device_id, device_name, device_model, android_version
- **DataUsage** — data_usage_id, data_consumed_gb, data_remaining_gb, usage_date, signal_strength
- **Notification** — notification_id, notification_type, title, message, date_sent, is_read
- **Provider** — provider_id, provider_name (auto-detected via `TelephonyManager`, not user-entered)
- **Promo** — promo_id, promo_name, data_amount_gb, promo_price, validity_days, expiry_date, promo_description
- **Association** — junction table linking user_id, device_id, provider_id, promo_id, notification_id, data_usage_id, total_data_used_gb

Rules:
- When adding a field, update both the Room entity and the Supabase table/migration — they must not drift.
- Every Supabase table carries **Row Level Security scoped to `user_id`**. Don't create a table without its RLS policy.

---

## Design system

- **Dark navy** (`#0C101A` / `DataraDarkBg`) background, card/input surface (`#141B2B` / `DataraCardBg`).
- The app is **dark-only**. `DATAraTheme` defaults to `darkTheme = true`, `dynamicColor = false`, and only `DarkColorScheme` is defined. Do not add a light color scheme or a theme toggle unless explicitly asked.
- Always use the semantic brand tokens defined in `ui/theme/Color.kt` (`DataraDarkBg`, `DataraCardBg`, `DataraInputBg`, `DataraInputBorder`, `DataraTextSecondary`, etc.) — never a raw hex literal inside a screen.
- Accent colors: vibrant/electric blue (`#1E64FA` / `DataraPrimaryBlue`, `#2F6BFF` / `DataraNeonBlue`), lime green (`#CEFF37` / `DataraNeonGreen`) for positive status, red (`#EF4444` / `DataraError`) for errors.
- Typography: use `MaterialTheme.typography` styles backed by `DataraFontFamily` (currently `FontFamily.SansSerif`, chosen for offline/emulator reliability).
- **Reuse the shared components in `ui/auth/components/DataraAuthComponents.kt`** (text fields, buttons, etc.) instead of writing new ones. Icons live in `DataraIcons.kt` — outline/line style, not filled glyphs.
- Card radius: 12–16dp, flat with subtle elevation — avoid full-pill/oval stat cards (pill shapes are reserved for inputs and primary buttons).
- Bottom nav (Home / History / Settings) is a fixed floating pill, shown on Dashboard, History, Profile, Settings — **not** on Login/Register. (Planned, D5.)

---

## Scope boundaries — things NOT to build without explicit confirmation

These were deliberately cut during design review because they fall outside the thesis objectives or conflict with the project's stated privacy compliance (RA 10173). Do not reintroduce them just because they seem like reasonable features:

- ❌ **No SMS reading** for promo auto-detection. Promos are added manually or synced from the auto-detected carrier only. `READ_SMS`/`RECEIVE_SMS` permissions are off-limits.
- ❌ **No address field** or other unnecessary PII collection on the User entity.
- ❌ **No raw user ID/UUID shown in the main UI.**
- ❌ **No manual data-budget setting/enforcement feature** (`NetworkPolicyManager`-level restriction). The app predicts depletion; it does not throttle or cap other apps' data.
- ❌ **No standalone data-usage calculator** unrelated to prediction.
- ⚠️ **ML diagnostics (MAE/RMSE/R² display) do not belong in the main Settings menu** — gate them behind the hidden dev-only screen (D9, secret gesture), since end users (prepaid students/professionals) aren't the audience for model metrics.
- ✅ Phone number, if present at all, is an **optional secondary profile field** — email/password is the login identifier, not phone/SMS OTP.

If a task description conflicts with this list, ask before proceeding rather than assuming the older behavior is still correct.

---

## Prediction logic — do not oversimplify

- **PET (Probable Exhaustion Time)** is the core output of the app and must always be prominently displayed on the Dashboard — do not bury it under other stats.
- New users get predictions from the **Global model**. Once a user has enough local usage history (roughly 7 days of telemetry), the app switches to the **Local personalization layer**. Don't hardcode "always use global" or "always use local" — this is a conditional, reversible switch (see the thesis's Fig. 1 data flow and ROADMAP D6).
- **On-device _inference_ is non-negotiable; on-device _training_ is not a given.** ONNX Runtime does inference only for tree ensembles — there is no on-device XGBoost retraining path. ROADMAP §2 (Decision 2) holds the options; don't write code that assumes local XGBoost training works.
- Predictions run **on-device via ONNX Runtime**, not via a live network call to a prediction server. If a task asks you to add a `/predict` API call, confirm that's actually intended first — it would contradict the offline-first design already documented. (Downloading an updated *model file* from Supabase Storage is not a `/predict` call and doesn't violate this.)
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

- `NavGraph.kt` declares a `"dashboard"` route whose composable body is **empty**. Successful login and register both navigate there, so the app currently lands on a blank screen after auth. That's expected until D5 — not a bug to chase.
- `MainActivity` does not yet check `sessionStatus`, so there is no auto-login on launch (open D1 item).
- Sign-up creates the Supabase Auth user but does not yet write a row to the `users` table (open D1 item).

---

## When in doubt

This is a capstone thesis project with panel-approved scope (Chapters 1–3 are fixed reference documents, not aspirational). If a request would expand scope, add a new permission, or change something already defended to a panel (dataset structure, prediction approach, privacy claims), flag it explicitly rather than silently implementing it.
