# DATAra

> Capstone Android app — `com.capstone.datara`

## Product and current status

DATAra forecasts when device-measured mobile usage will reach an amount the user chooses in MB or GB. This user-defined quota direction is approved (2026-10-29). PET refers to quota-reaching time, not carrier balance depletion, promo expiry or loss of connectivity. Measured traffic counts even when a carrier treats it as a freebie. Quotas never throttle or block traffic.

Authentication, password recovery, session routing and branded launch are implemented. The signed-in screens—Home, Create Quota, Quota Details, History, Notifications, Settings and Profile—use sample/session state. Forecasts are fixtures; usage access and sync are simulated. Sign-out uses real Supabase Auth.

Room, real telemetry, durable quotas/history, ML inference, OS notifications and cloud sync are pending. The deployed Supabase schema still uses the earlier promo model; quota migrations and manuscript/research alignment are separate pending work.

See [ROADMAP](ROADMAP.md) for phases and open decisions, [quota UI behavior](docs/quota-ui-preview.md), [design system](docs/ui-redesign.md), [branding](docs/brand-assets.md), and [contribution rules](CONTRIBUTING.md).

## Tech Stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 2.4.0 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3 |
| Charts / Launch | Vico 3.3.1 / AndroidX core-splashscreen 1.2.0 |
| DI | Hilt 2.60.1 + KSP 2.3.9 |
| Backend | Supabase 3.8.0 (Auth, Postgrest, Storage) |
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
├── data/remote/, repository/     # Supabase client and AuthRepository
├── di/                          # Hilt wiring
└── ui/
    ├── auth/, session/, splash/  # Authentication and session restoration
    ├── dashboard/, quota/       # Home, Create Quota, Details and sample state
    ├── history/, notifications/ # Independent sample usage and alerts
    ├── settings/, profile/      # Preview settings/profile; real sign-out
    ├── components/, navigation/ # Shared Material 3 UI and navigation
    └── theme/                   # Dark auth theme and light QuotaTheme
```

`supabase/` contains the applied legacy migrations. Room and inference packages do not exist yet. Authentication uses dark navy; signed-in screens use Direction C white surfaces, navy accents and teal progress. There is no theme toggle.

## Verification

On Windows, with a compatible Gradle JVM configured:

```powershell
./gradlew.bat test assembleDebug assembleDebugAndroidTest
```

Use `./gradlew` on macOS/Linux. Instrumentation APK compilation alone does not run device tests. See [UI review evidence](docs/ui-redesign.md) for historical emulator results and remaining device checks. Review sample scenarios through the Home **Preview options** menu.

---

## Important Notes

- **Never commit `local.properties`** — it contains your Supabase anon key.
- **`sdk.dir` path** in `local.properties` must match your local Android SDK location.
  Android Studio auto-generates this — just copy the `sdk.dir` line from your own file.
- All dependency versions are locked in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
  No manual version management needed after cloning.
