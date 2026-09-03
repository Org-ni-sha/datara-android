# AGENT.md — DATAra

Guidance for AI coding agents (Antigravity, Claude Code, Cursor, etc.) working in this repository. Read this before making changes.

---

## What this project is

DATAra is an Android app that predicts when a prepaid mobile user's data will run out (Probable Exhaustion Time / PET), based on usage behavior, time-based patterns, and network conditions (signal strength, internet speed). It's a BSIT capstone project for USTP Cagayan de Oro City. The prediction is powered by a dual-layer model: a pre-trained **Global XGBoost model** (baseline, trained on aggregated usage data) and a **Local XGBoost model** (personalized per-user, retrained on-device usage over time).

This repo (`datara-android`) is the Android client only. Related repos in the same GitHub org:
- `data-harvester` — the standalone dataset-collection app used during research (do not modify unless explicitly asked; it's a separate deliverable)
- `datara-ml` — Python training pipeline (cleaning, XGBoost training, ONNX export)

---

## Tech stack — do not substitute without asking

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM — ViewModel + StateFlow, no LiveData |
| DI | Hilt |
| Local DB | Room (SQLite) |
| Backend | Supabase (Postgres, Auth, Storage) — **not** Firebase |
| Auth | Email + password via Supabase Auth (`auth-kt`) — **not** phone/OTP |
| Networking | `supabase-kt` client (bundles Ktor under the hood) |
| Background work | WorkManager + Foreground Service, for 30-sec (`TrafficStats`) and 10-min (`NetworkStatsManager`) polling |
| Charts | Vico (Compose-native) or MPAndroidChart |
| On-device inference | ONNX Runtime Mobile — the trained XGBoost model is converted to `.onnx` and run locally; there is no live prediction API call |
| Min SDK | 26 (hard requirement — do not lower) |

Package root: `com.capstone.datara`

---

## Project structure

```
app/src/main/java/com/capstone/datara/
├── DataraApplication.kt          # @HiltAndroidApp
├── MainActivity.kt               # @AndroidEntryPoint, must call DataraNavGraph()
├── data/
│   ├── remote/                   # SupabaseClientProvider, remote DTOs
│   ├── local/                    # Room entities, DAOs, database
│   └── repository/                # AuthRepository, UsageRepository, etc.
├── di/                            # Hilt modules
└── ui/
    ├── auth/                     # Login, Register, AccountRecovery
    ├── dashboard/
    ├── history/
    ├── promos/
    ├── profile/
    ├── settings/
    └── navigation/                # NavGraph
```

---

## Secrets — critical, do not violate

- Supabase URL and anon key live in `local.properties` (git-ignored) and are exposed to code via `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
- **Never hardcode a Supabase URL, key, or any credential directly in a `.kt` file.** If a task seems to require this, stop and flag it instead of doing it.
- Never commit `local.properties`. If you ever see it staged in a diff, flag it before proceeding.

---

## Data model (Room / Postgres — keep both in sync)

Core entities, mirrored between local Room tables and the Supabase Postgres `global` schema:

- **User** — id, email, name, created_at (no address field, no raw ID exposed in UI — see Scope Boundaries)
- **Device** — device_id, device_name, device_model, android_version
- **DataUsage** — data_usage_id, data_consumed_gb, data_remaining_gb, usage_date, signal_strength
- **Notification** — notification_id, notification_type, title, message, date_sent, is_read
- **Provider** — provider_id, provider_name (auto-detected via `TelephonyManager`, not user-entered)
- **Promo** — promo_id, promo_name, data_amount_gb, promo_price, validity_days, expiry_date, promo_description
- **Association** — junction table linking user_id, device_id, provider_id, promo_id, notification_id, data_usage_id, total_data_used_gb

When adding a field, update both the Room entity and the Supabase table/migration — they must not drift.

---

## Design system

- **Dark navy** (`#0C101A` / `DataraDarkBg`) background, card/input surface (`#141B2B` / `DataraCardBg`) — dark mode is the default
- Always use the semantic brand tokens defined in `ui/theme/Color.kt` (`DataraDarkBg`, `DataraCardBg`, `DataraInputBg`, `DataraInputBorder`, etc.)
- Accent colors: vibrant/electric blue (`#1E64FA` / `DataraPrimaryBlue`, `#2F6BFF` / `DataraNeonBlue`), lime green (`#CEFF37` / `DataraNeonGreen`) for positive status, red (`#EF4444` / `DataraError`) for errors
- Typography: Use `MaterialTheme.typography` styles backed by `DataraFontFamily` (`FontFamily.SansSerif`)
- Card radius: 12–16dp, flat with subtle elevation — avoid full-pill/oval stat cards (pill shapes are reserved for inputs and primary buttons)
- Icons: outline/line style, not filled glyphs (see `ui/auth/components/DataraIcons.kt`)
- Bottom nav (Home / History / Settings) is a fixed floating pill, shown on Dashboard, History, Profile, Settings — **not** on Login/Register

---

## Scope boundaries — things NOT to build without explicit confirmation

These were deliberately cut during design review because they fall outside the thesis objectives or conflict with the project's stated privacy compliance (RA 10173). Do not reintroduce them just because they seem like reasonable features:

- ❌ **No SMS reading** for promo auto-detection. Promos are added manually or synced from the auto-detected carrier only. `READ_SMS`/`RECEIVE_SMS` permissions are off-limits.
- ❌ **No address field** or other unnecessary PII collection on the User entity.
- ❌ **No raw user ID/UUID shown in the main UI.**
- ❌ **No manual data-budget setting/enforcement feature** (`NetworkPolicyManager`-level restriction). The app predicts depletion; it does not throttle or cap other apps' data.
- ❌ **No standalone data-usage calculator** unrelated to prediction.
- ⚠️ **ML diagnostics (MAE/RMSE/R² display) do not belong in the main Settings menu** — if built at all, gate behind a hidden/dev-only screen, since end users (prepaid students/professionals) aren't the audience for model metrics.
- ✅ Phone number, if present at all, is an **optional secondary profile field** — email/password is the login identifier, not phone/OTP.

If a task description conflicts with this list, ask before proceeding rather than assuming the older behavior is still correct.

---

## Prediction logic — do not oversimplify

- **PET (Probable Exhaustion Time)** is the core output of the app and must always be prominently displayed on the Dashboard — do not bury it under other stats.
- New users get predictions from the **Global model**. Once a user has enough local usage history, the app switches to their **Local model**. Don't hardcode "always use global" or "always use local" — this is a conditional switch (see the thesis's Fig. 1 data flow).
- Predictions run **on-device via ONNX Runtime**, not via a live network call to a prediction server. If a task asks you to add a `/predict` API call, confirm that's actually intended first — it would contradict the offline-first design already documented.
- Network factors (signal strength, internet speed) are model inputs, not just display metadata — don't drop them when refactoring the prediction pipeline.

---

## Git conventions

- Branch naming: `feature/...`, `fix/...`, `chore/...`, `refactor/...`, `docs/...`
- Commit format: `type(scope): summary` (Conventional Commits) — e.g. `feat(auth): wire up register screen to Supabase`
- All changes go through a PR into `main`, squash-merged, branch deleted after merge
- `main` must always build — don't commit directly to it

---

## Testing expectations

- Unit tests: JUnit 4 (configured in `gradle/libs.versions.toml`), ViewModel logic in particular (auth state transitions, prediction-switch logic)
- UI tests: Compose UI Test for critical flows (login, register, dashboard renders PET)
- Before marking a task done, the project should still compile and existing tests should still pass — run them, don't assume

---

## When in doubt

This is a capstone thesis project with panel-approved scope (Chapters 1–3 are fixed reference documents, not aspirational). If a request would expand scope, add a new permission, or change something already defended to a panel (dataset structure, prediction approach, privacy claims), flag it explicitly rather than silently implementing it.