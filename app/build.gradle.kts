import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Load local.properties
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

// A secret or setting: an environment variable first (GitHub Actions passes repository secrets
// this way, see .github/workflows/release.yml), then local.properties (a laptop), else blank.
// Never write a real value in this file.
fun setting(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: localProperties.getProperty(name, "")

// Release signing. Only when every value is present and the keystore file exists; otherwise the
// release build still assembles, unsigned (so Android CI, which has no keystore, keeps passing).
val releaseKeystore = setting("DATARA_KEYSTORE_FILE").takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
val canSignRelease = releaseKeystore?.exists() == true &&
    listOf("DATARA_KEYSTORE_PASSWORD", "DATARA_KEY_ALIAS", "DATARA_KEY_PASSWORD").all { setting(it).isNotBlank() }

android {
    namespace = "com.capstone.datara"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.capstone.datara"
        minSdk = 26
        targetSdk = 37
        // versionCode is internal and must only ever increase — bump it on every build
        // handed to someone else. versionName is the string shown in Android's App info,
        // and is what D9's hidden diagnostics screen counts taps on.
        // 0.x while pre-defense; 1.0.0 (versionCode 4, tag v1.0.0) is reserved for the
        // Nov 12 defense build with the final model.
        versionCode = 3
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose Supabase secrets via BuildConfig
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${setting("SUPABASE_URL")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${setting("SUPABASE_ANON_KEY")}\""
        )
        // Web OAuth client ID for Google sign-in. Blank hides the Google button.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${setting("GOOGLE_WEB_CLIENT_ID")}\""
        )
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = setting("DATARA_KEYSTORE_PASSWORD")
                keyAlias = setting("DATARA_KEY_ALIAS")
                keyPassword = setting("DATARA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Tests shared by the JVM and instrumented suites, e.g. the telemetry repository contract
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
        // The D6 golden parity fixture, so DeviceParityTest runs it on the Android ONNX Runtime
        getByName("androidTest").assets.srcDir("src/test/resources")
    }
}

// Room writes each schema version here as JSON. Commit these files: migration tests (and
// reviewers) compare against them.
room {
    schemaDirectory("$projectDir/schemas")
}

// JVM unit tests use onnxruntime (desktop natives) instead of the Android AAR: both ship the
// ai.onnxruntime classes, and the AAR's native libraries cannot load on the JVM.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") || it.name.endsWith("UnitTestCompileClasspath") }
    .configureEach { exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android") }

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.googlefonts)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.vico.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.supabase.functions.kt)

    // Google sign-in
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Background work (telemetry watchdog)
    implementation(libs.androidx.work.runtime.ktx)

    // Local database (Room). KSP, never kapt.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // On-device inference (D6): ONNX Runtime runs the bundled Global XGBoost model
    implementation(libs.onnxruntime.android)

    // Ktor
    implementation(libs.ktor.client.android)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Test
    testImplementation(libs.junit)
    // The desktop build of the same ONNX Runtime API, so the parity test runs the real model on the JVM
    testImplementation(libs.onnxruntime.jvm)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
