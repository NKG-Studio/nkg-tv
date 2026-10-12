#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_NDK_HOME:?Set an already authorized installed Android NDK}"
mkdir -p app/src/debug/assets
python3 tools/generate-tinysr-shader.py
"$ANDROID_NDK_HOME/shader-tools/linux-x86_64/glslc" -O -fshader-stage=compute core/gpu/tinysr2.comp -o app/src/debug/assets/tinysr2.spv
ndk_bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for pair in 'arm64-v8a aarch64-linux-android26' 'x86_64 x86_64-linux-android26';do
 read -r abi target <<< "$pair"
 mkdir -p "app/src/debug/jniLibs/$abi"
 "$ndk_bin/${target}-clang++" -std=c++17 -O2 -Wall -Wextra -Wno-missing-field-initializers -fPIC -shared -static-libstdc++ -Wl,-z,max-page-size=16384 core/gpu/jni.cpp -lvulkan -llog -o "app/src/debug/jniLibs/$abi/libnkg_gpu.so"
 "$ndk_bin/llvm-strip" "app/src/debug/jniLibs/$abi/libnkg_gpu.so"
done
