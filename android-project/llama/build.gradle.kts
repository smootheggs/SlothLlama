plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// llama.cpp for Android (GGUF models). Native code is built from the pinned
// git submodule at ../third_party/llama.cpp — run `git submodule update --init` first.

// GPU: llama.cpp's OpenCL backend (tuned for Qualcomm Adreno) is built when
// scripts/fetch-opencl.sh has prepared the pinned OpenCL headers and loader (CI always runs it).
// Without them the app builds CPU-only. At runtime the backend is optional: if the tablet's
// OpenCL driver can't be opened, llama.cpp quietly stays on the CPU.
val openClDir = rootProject.file("third_party/opencl")
val openClLibrary = openClDir.resolve("lib/arm64-v8a/libOpenCL.so")
val withOpenCl = openClLibrary.isFile && openClDir.resolve("include/CL/cl.h").isFile
logger.lifecycle(if (withOpenCl) "llama.cpp: building with the GPU (OpenCL) backend" else "llama.cpp: CPU only (run scripts/fetch-opencl.sh for the GPU backend)")

android {
    namespace = "app.localmind.llama"
    compileSdk = 35
    ndkVersion = "29.0.13113456"

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            // The Y700 is arm64; x86_64 only matters for emulators.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // Release native code even in debug app builds — debug llama.cpp is ~10x slower.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_BUILD_TESTS=OFF"
                arguments += "-DLLAMA_BUILD_EXAMPLES=OFF"
                arguments += "-DLLAMA_BUILD_SERVER=OFF"
                arguments += "-DLLAMA_BUILD_TOOLS=OFF"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DLLAMA_CURL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
                if (withOpenCl) {
                    arguments += "-DGGML_OPENCL=ON"
                    arguments += "-DGGML_OPENCL_EMBED_KERNELS=ON"
                    arguments += "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
                    arguments += "-DOpenCL_INCLUDE_DIR=${openClDir.resolve("include").absolutePath}"
                    arguments += "-DOpenCL_LIBRARY=${openClLibrary.absolutePath}"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
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

dependencies {
    implementation(libs.androidx.core.ktx)
}
