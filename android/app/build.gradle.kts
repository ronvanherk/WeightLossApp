plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Override for a local server: ./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:3002/
val apiBaseUrl = (findProperty("apiBaseUrl") as String?) ?: "https://food.quantumvikings.com/"

// CI sets VERSION_CODE (the run number) so every Play upload is higher than the last.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
// The Play upload key; only CI has it (GitHub secrets), see .github/workflows/android.yml.
val uploadKeystore = System.getenv("UPLOAD_KEYSTORE")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "com.quantumvikings.weightloss"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quantumvikings.weightloss"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 2
        versionName = "0.2.${ciVersionCode ?: 0}"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    // A fixed debug key, so every CI build installs over the previous one (a fresh runner would
    // otherwise generate a new key and Android refuses the update). Not a secret: debug only.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = "upload"
                keyPassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Installs next to the Play version instead of clashing with its signature.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "WeightLoss dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
            resValue("string", "app_name", "WeightLoss")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.play.services.code.scanner)
    implementation(libs.health.connect)
    implementation(libs.glance.appwidget)
}
