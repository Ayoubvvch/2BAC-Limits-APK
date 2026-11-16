plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.ayoub.baclimits.full"
    compileSdk = 34

    defaultConfig {
        val propVersionName = (project.findProperty("versionName") as String?)
            ?.takeIf { it.isNotBlank() } ?: "1.1.0"
        val propVersionCode = (project.findProperty("versionCode") as String?)
            ?.toIntOrNull() ?: 4

        val customAppId = (project.findProperty("applicationId") as String?)?.takeIf { it.isNotBlank() }
        applicationId = customAppId ?: "com.ayoub.baclimits.full"
        minSdk = 24
        targetSdk = 34
        versionCode = propVersionCode
        versionName = propVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        buildConfigField("String", "APP_EDITION", "\"full\"")
        buildConfigField("String", "APP_EDITION_NAME", "\"Full\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.jks")
            storePassword = "baclimits_release_key_2026"
            keyAlias = "baclimits"
            keyPassword = "baclimits_release_key_2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("sympy>=1.13")
        }
    }
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
        }
    }
}

dependencies {
    implementation(project(":core"))
}
