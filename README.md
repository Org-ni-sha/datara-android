# DATAra

> Capstone Android app — `com.capstone.datara`

## Product and current status

DATAra forecasts when device-measured mobile usage will reach an amount the user chooses in MB or GB. This user-defined quota direction is approved (2026-10-29). PET refers to quota-reaching time, not carrier balance depletion, promo expiry or loss of connectivity. Measured traffic counts even when a carrier treats it as a freebie. Quotas never throttle or block traffic.

Everything except cloud sync is built and runs on real data:

- **Accounts:** email or username with a password, or Google, through Supabase Auth; password recovery by email code.
- **Tracking:** a foreground service measures mobile usage (NetworkStatsManager every 10 minutes, TrafficStats every 30 seconds) with signal, network type and carrier, stored in Room on the phone.
- **Quotas:** the user sets an amount; used and remaining come from the measured readings since the cycle started.
- **Forecast:** an XGBoost model (`final-2026-11-17`, ONNX Runtime, on the phone) predicts next-hour usage, and the app projects it against the remaining quota to give the PET. A local layer learns each user's usage and takes over when it is at least 5% more accurate. Held out by device, the PET is off by 10.2 h on average, against 79.4 h for repeating the last 24 hours.
- **Alerts:** 75%, 90% and 100% of the quota, and when the PET is within 24 h or 3 h.
- **History:** 24-hour and 7-day charts, and every past cycle with how the forecast did.

Usage data never leaves the phone; Supabase holds accounts and profiles only. Cloud sync (ROADMAP C3) is not built, so a second phone signed in to the same account starts empty. The model is trained in [datara-ml](https://github.com/Org-ni-sha/datara-ml) from data collected by the separate data-harvester app.

See [ROADMAP](ROADMAP.md) for phases and open decisions, [quota UI behavior](docs/quota-ui-preview.md), [design system](docs/ui-redesign.md), [branding](docs/brand-assets.md), and [contribution rules](CONTRIBUTING.md).

## Tech Stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 2.4.0 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3 |
| Charts / Launch | Vico 3.3.1 / AndroidX core-splashscreen 1.2.0 |
| DI | Hilt 2.60.1 + KSP 2.3.9 |
| Backend | Supabase 3.8.0 (Auth, Postgrest, Storage) |
| Local database | Room 2.8.5 (KSP) |
| On-device ML | ONNX Runtime 1.31.1 |
| Background work | Foreground service + WorkManager 2.11.2 watchdog |
| Network | Ktor Android 3.0.3 |
| Build | AGP 9.3.2, Gradle wrapper (see `gradle/wrapper/`) |

---

## Getting Started

### Prerequisites
- Android Studio compatible with the pinned AGP 9.3.2 and Gradle 9.5.0
- Use Android Studio’s compatible bundled JBR as the Gradle JVM; Java source/target compatibility is 11, which is not the Gradle runtime requirement
- Android SDK Platform 37 for compilation; emulator/device API 26+ to run

### 1. Clone the repo

```bash
git clone https://github.com/Org-ni-sha/datara-android.git DATAra
cd DATAra
```

### 2. Create your `local.properties`

This file is **gitignored** (it holds secrets). Copy the template and fill in the real values:

```bash
# Windows
copy local.properties.example local.properties

# macOS / Linux
cp local.properties.example local.properties
```

Then open `local.properties` and replace the placeholders:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here
```

> **Where to get the values:**
> Supabase Dashboard → your project → **Settings → API** → copy *Project URL* and *anon public* key.
> Ask the project lead if you need access to the shared Supabase project.

### 3. Sync & Run

- Open the project in Android Studio
- Let Gradle sync finish (all dependencies are pinned in `gradle/libs.versions.toml`)
- Run on an emulator (API 26+) or a physical device

---

## Project Structure

```text
app/src/main/java/com/capstone/datara/
├── DataraApplication.kt          # Hilt application
├── MainActivity.kt               # Native splash and session gate
├── data/                         # Room (local/), Supabase (remote/), repositories, session, telemetry rollup
├── domain/cycle/                 # Quota cycle rules and use cases
├── telemetry/                    # Foreground service, measurement rules, device readers
├── forecast/                     # Feature extractor, ONNX model, local layer, PET projection
├── alerts/                       # Milestone and forecast alerts, notification channels
├── di/                           # Hilt wiring
└── ui/
    ├── auth/, session/, splash/  # Authentication and session restoration
    ├── dashboard/, quota/        # Home, Create Quota, Quota Details
    ├── history/, notifications/  # Usage charts, cycle history, alert inbox
    ├── settings/, profile/       # Tracking status, alert switches, profile, sign-out
    ├── tracking/, diagnostics/   # Tracking setup; hidden ML diagnostics (7 taps on the version)
    ├── components/, navigation/  # Shared Material 3 UI and navigation
    └── theme/                    # Light Material 3 theme, navy launch screens
```

`supabase/` holds the schema migrations (the quota schema `20261104000001` is applied). The bundled model is `app/src/main/assets/models/global_xgboost.onnx`.

## Verification

On Windows, with a compatible Gradle JVM configured:

```powershell
./gradlew.bat test assembleDebug assembleDebugAndroidTest
```

Use `./gradlew` on macOS/Linux. Instrumentation APK compilation alone does not run device tests; run those on an emulator, because `connectedDebugAndroidTest` uninstalls the app (and its data) afterwards. If the ONNX unit tests crash the JVM on Windows, run Gradle with another JDK, e.g. `-Dorg.gradle.java.home=<path to a Temurin JDK>`.

Releases: pushing a `v*` tag builds a signed release APK and publishes it on GitHub Releases (`.github/workflows/release.yml`).

---

## Important Notes

- **Never commit `local.properties`** — it contains your Supabase anon key.
- **`sdk.dir` path** in `local.properties` must match your local Android SDK location.
  Android Studio auto-generates this — just copy the `sdk.dir` line from your own file.
- All dependency versions are locked in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
  No manual version management needed after cloning.
