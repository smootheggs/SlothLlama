plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "app.localmind.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.localmind.assistant"
        minSdk = 33 // required by the llama.cpp module
        targetSdk = 35
        versionCode = 36
        versionName = "1.29.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["appLabel"] = "SlothLlama"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    // Release builds are signed with YOUR private key, which never enters the repo: GitHub
    // Actions decodes it from the SLOTH_KEYSTORE_B64 secret and points SLOTH_KEYSTORE at it.
    // Only someone holding that key can publish an update that Android will install over the
    // app (and so inherit its chats). Without the secrets, CI falls back to the debug build.
    val releaseKeystore = System.getenv("SLOTH_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }

    signingConfigs {
        // The standard Android debug key, committed on purpose so debug builds install over each
        // other. It's public, so debug builds are for development only.
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
            // Not debuggable: adb can't attach or read the app's private files (run-as).
            isDebuggable = false
            // R8 is off until the keep rules for Hilt, Room, MediaPipe and JNI are verified on a
            // device; shrinking adds little for a sideloaded personal app.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "SlothLlama Dev"
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
        // Release builds run "lint vital" by default; skip it so a lint rule can't block a
        // sideloaded build that CI can't preview. Lint still runs on demand (gradle :app:lint).
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Several libraries (PDFBox among them) carry their own copies of these.
            pickFirsts += listOf("/META-INF/DEPENDENCIES", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
        jniLibs {
            // llama.cpp loads its CPU backends (libggml-cpu-*.so) from nativeLibraryDir at runtime,
            // so native libraries must be extracted on install.
            useLegacyPackaging = true
            // The OpenCL loader is only linked against at build time. The tablet's own driver must
            // be the one that loads, so never ship a copy.
            excludes += "**/libOpenCL.so"
            // libkiwix brings its own C++ runtime; keep one copy if another library does too.
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // HTML to text (EPUB books, web pages, Wikipedia articles)
    implementation(libs.jsoup)
    // PDF text for document attachments. Bouncy Castle is only for certificate-encrypted PDFs,
    // which the app doesn't open, so it's left out (about 8 MB).
    implementation(libs.pdfbox.android) {
        exclude(group = "org.bouncycastle")
    }
    // Offline Wikipedia (Kiwix .zim files) in the library. arm64 natives only (abiFilters).
    implementation(libs.libkiwix)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Google AI Edge LiteRT-LM / MediaPipe GenAI Runtime
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.guava.listenablefuture)

    // llama.cpp for GGUF models
    implementation(project(":llama"))
    // Offline speech-to-text (SenseVoice). Not committed (50 MB): CI downloads the release AAR and
    // checks its SHA-256; for a local build run android-project/scripts/fetch-sherpa-onnx.sh first.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    // Unit & UI Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
