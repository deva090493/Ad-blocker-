plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.deva.adblocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.deva.adblocker"
        minSdk = 24
        targetSdk = 34
        // Every GitHub build gets a higher number, so new APKs install over the old one
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Same key every build -> updates install without uninstalling
    signingConfigs {
        create("fixed") {
            storeFile = file("adblocker.keystore")
            storePassword = "adblock123"
            keyAlias = "adblocker"
            keyPassword = "adblock123"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
