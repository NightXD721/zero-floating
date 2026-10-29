plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingFile = rootProject.file("signing/zero.jks")
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.nightxd.zerofloating"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nightxd.zerofloating"
        minSdk = 26
        targetSdk = 34
        versionCode = 120 + runNumber
        versionName = "1.2.$runNumber"
    }

    signingConfigs {
        if (signingFile.exists()) {
            create("zero") {
                storeFile = signingFile
                storePassword = "zerofloating"
                keyAlias = "zero"
                keyPassword = "zerofloating"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (signingFile.exists()) {
                signingConfigs.getByName("zero")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
