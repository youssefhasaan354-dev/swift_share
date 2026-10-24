import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.swiftshare"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.swiftshare"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keyPropertiesFile = rootProject.file("key.properties").takeIf { it.exists() }
        ?: project.file("key.properties").takeIf { it.exists() }
    val keyProperties = Properties().apply {
        if (keyPropertiesFile != null && keyPropertiesFile.exists()) {
            keyPropertiesFile.inputStream().use { inputStream ->
                load(inputStream)
            }
        }
    }

    val releaseStoreFilePath = keyProperties.getProperty("storeFile")
        ?: System.getenv("SWIFTSHARE_STORE_FILE")
    val releaseStorePassword = keyProperties.getProperty("storePassword")
        ?: System.getenv("SWIFTSHARE_STORE_PASSWORD")
    val releaseKeyAlias = keyProperties.getProperty("keyAlias")
        ?: System.getenv("SWIFTSHARE_KEY_ALIAS")
    val releaseKeyPassword = keyProperties.getProperty("keyPassword")
        ?: System.getenv("SWIFTSHARE_KEY_PASSWORD")

    val hasReleaseConfig = !releaseStoreFilePath.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank() &&
            (file(releaseStoreFilePath).exists() || rootProject.file(releaseStoreFilePath).exists())

    signingConfigs {
        if (hasReleaseConfig) {
            create("release") {
                val store = file(releaseStoreFilePath!!).takeIf { it.exists() }
                    ?: rootProject.file(releaseStoreFilePath)
                storeFile = store
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Ktor Embedded HTTP & WebSocket Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.cors)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // CameraX & Barcode Scanner
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.play.services.mlkit.barcode)
    implementation(libs.zxing.core)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)

    // Image loading
    implementation(libs.coil.compose)

    // Biometrics & Security Vault
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
