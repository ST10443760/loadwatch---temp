import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// The API base URL comes from local.properties (key api.base.url) so each developer and device can point
// at their own hosted API. A physical phone cannot reach localhost, so there is no localhost default.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl = (localProperties.getProperty("api.base.url") ?: "https://loadwatch-api.invalid/")
    .trim()
    .let { if (it.endsWith("/")) it else "$it/" }

// Release signing comes from Gradle properties (put them in %USERPROFILE%\.gradle\gradle.properties, not the
// project's gradle.properties) or from environment variables of the same names. Nothing about the keystore is
// in the repo. Without all four values the release build is still produced, just unsigned.
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("LOADWATCH_KEYSTORE_PATH")
val releaseStorePassword = signingValue("LOADWATCH_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("LOADWATCH_KEY_ALIAS")
val releaseKeyPassword = signingValue("LOADWATCH_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "com.example.loadwatch"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.loadwatch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.info("LoadWatch: release signing is not configured, so the release APK will be unsigned")
            }
            // R8 removes unused code and resources; proguard-rules.pro keeps what is only reached by reflection
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time.Instant on minSdk 24 devices
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Lets JVM unit tests run code that calls android.util.Log, which then does nothing
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
room {
    // Exported schemas are committed so future migrations can be verified against them
    schemaDirectory("$projectDir/schemas")
}
