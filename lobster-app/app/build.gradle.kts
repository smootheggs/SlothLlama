plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.slothlobster"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.slothlobster"
        minSdk = 29
        targetSdk = 35
        versionCode = 21
        versionName = "1.13.4"
        manifestPlaceholders["appLabel"] = "SlothLobster"
    }

    // Signed with the same private key as SlothLlama (the SLOTH_KEYSTORE_* secrets), so only you
    // can publish an update that installs over it. Without the secrets, CI builds the debug APK.
    val releaseKeystore = System.getenv("SLOTH_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storeType = "pkcs12"
                storePassword = System.getenv("SLOTH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SLOTH_KEY_ALIAS") ?: "slothllama"
                keyPassword = System.getenv("SLOTH_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "SlothLobster Dev"
            signingConfig = signingConfigs.getByName("debug")
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

    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
    // The web: pages, searches and feeds. Only this app has these; SlothLlama has none.
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
