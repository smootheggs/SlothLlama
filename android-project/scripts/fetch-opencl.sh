#!/usr/bin/env bash
# Prepares what llama.cpp's GPU backend (OpenCL, tuned for Qualcomm Adreno) is compiled against:
# the Khronos OpenCL headers and the OpenCL ICD loader, each pinned to an exact commit, so a
# moved tag or a changed repository stops the build.
#
# Build time only. The loader is linked against but not packaged: on the tablet, the vendor's own
# driver (/vendor/lib64/libOpenCL.so, made visible by <uses-native-library> in the manifest) is used.
# Without this script the app still builds, just without the GPU backend.
set -euo pipefail

TAG=v2026.05.29
HEADERS_COMMIT=6fe718c31a45fe25151362a72ef041c3a1047cbd
LOADER_COMMIT=b7bd2803acc779c03d96588e9ca9e9568a18698a
NDK_VERSION=29.0.13113456
CMAKE_VERSION=3.31.6

PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$PROJECT/third_party/opencl"
STAMP="$OUT/PINNED"
WANT="$HEADERS_COMMIT $LOADER_COMMIT"

if [ -f "$OUT/lib/arm64-v8a/libOpenCL.so" ] && [ -f "$OUT/include/CL/cl.h" ] && [ "$(cat "$STAMP" 2>/dev/null)" = "$WANT" ]; then
  echo "OpenCL headers and loader already prepared: $OUT"
  exit 0
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK="$SDK/ndk/$NDK_VERSION"
CMAKE_BIN="$SDK/cmake/$CMAKE_VERSION/bin"
if [ -z "$SDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ] || [ ! -x "$CMAKE_BIN/cmake" ]; then
  echo "::error::Needs the Android SDK with ndk;$NDK_VERSION and cmake;$CMAKE_VERSION (ANDROID_HOME=$SDK)"
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT

fetch() { # <repository> <expected commit> <folder>
  git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$TAG" \
    "https://github.com/KhronosGroup/$1.git" "$WORK/$3"
  local got
  got="$(git -C "$WORK/$3" rev-parse HEAD)"
  if [ "$got" != "$2" ]; then
    echo "::error::$1 $TAG is commit $got, expected $2. Not using it."
    exit 1
  fi
}
fetch OpenCL-Headers "$HEADERS_COMMIT" headers
fetch OpenCL-ICD-Loader "$LOADER_COMMIT" loader

"$CMAKE_BIN/cmake" -S "$WORK/loader" -B "$WORK/build" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$CMAKE_BIN/ninja" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-33 \
  -DOPENCL_ICD_LOADER_HEADERS_DIR="$WORK/headers" \
  -DENABLE_OPENCL_LAYERINFO=OFF \
  -DBUILD_TESTING=OFF > "$WORK/configure.log" || { cat "$WORK/configure.log"; exit 1; }
"$CMAKE_BIN/cmake" --build "$WORK/build" --target OpenCL

LIB="$WORK/build/libOpenCL.so"
[ -f "$LIB" ] || { echo "::error::The OpenCL loader didn't build"; exit 1; }

# The GPU backend records this name as a dependency, so it must match the tablet's driver exactly.
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if ! "$READELF" -d "$LIB" | grep -q 'SONAME.*\[libOpenCL\.so\]'; then
  "$READELF" -d "$LIB" | grep SONAME || true
  echo "::error::libOpenCL.so has an unexpected SONAME; the tablet's driver wouldn't be found"
  exit 1
fi

rm -rf -- "$OUT"
mkdir -p "$OUT/include" "$OUT/lib/arm64-v8a"
cp -R "$WORK/headers/CL" "$OUT/include/"
cp "$LIB" "$OUT/lib/arm64-v8a/"
cp "$WORK/headers/LICENSE" "$OUT/LICENSE-OpenCL-Headers" 2>/dev/null || true
echo "$WANT" > "$STAMP"
echo "OpenCL $TAG ready: $OUT"
