# DATAra

> Capstone Android app — `com.capstone.datara`

## Tech Stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 2.4.0 |
| UI | Jetpack Compose (BOM 2026.02.01) |
| DI | Hilt 2.60.1 + KSP 2.3.9 |
| Backend | Supabase 3.8.0 (Auth, Postgrest, Storage) |
| Network | Ktor Android 3.0.3 |
| Build | AGP 9.3.2, Gradle wrapper (see `gradle/wrapper/`) |

---

## Getting Started

### Prerequisites
- Android Studio (latest stable — Ladybug or newer)
- JDK 11+
- Android SDK with API 26+

### 1. Clone the repo

```bash
git clone https://github.com/YOUR_ORG/DATAra.git
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

```
app/src/main/java/com/capstone/datara/
├── DataraApplication.kt          # @HiltAndroidApp
├── MainActivity.kt               # @AndroidEntryPoint, hosts NavGraph
├── data/
│   ├── remote/SupabaseClientProvider.kt
│   └── repository/AuthRepository.kt
├── di/
│   └── SupabaseModule.kt         # Hilt @Module
└── ui/
    ├── auth/
    │   ├── AuthViewModel.kt      # @HiltViewModel
    │   ├── LoginScreen.kt
    │   └── RegisterScreen.kt
    ├── navigation/NavGraph.kt
    └── theme/
```

---

## Important Notes

- **Never commit `local.properties`** — it contains your Supabase anon key.
- **`sdk.dir` path** in `local.properties` must match your local Android SDK location.
  Android Studio auto-generates this — just copy the `sdk.dir` line from your own file.
- All dependency versions are locked in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
  No manual version management needed after cloning.
