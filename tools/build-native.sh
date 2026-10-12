#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an authorized installed NDK}"
: "${NCNN_ANDROID_DIR:?Set NCNN_ANDROID_DIR to ncnn-20250503-android}"
ndk_bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for pair in 'aarch64-linux-android arm64-v8a aarch64-linux-android26' 'x86_64-linux-android x86_64 x86_64-linux-android26'; do
  read -r target abi clang_target <<< "$pair"
  cargo --config "target.$target.linker=\"$ndk_bin/${clang_target}-clang\"" build --manifest-path core/Cargo.toml --release --target "$target"
  mkdir -p "app/src/main/jniLibs/$abi"
  "$ndk_bin/${clang_target}-clang" -c -fPIC -O3 core/jni.c -o "core/target/$target/jni.o"
  "$ndk_bin/${clang_target}-clang++" -shared -fPIC -O3 -std=c++17 -static-libstdc++ -fopenmp -static-openmp -Wl,-z,max-page-size=16384 -I"$NCNN_ANDROID_DIR/$abi/include/ncnn" core/anime.cpp "core/target/$target/jni.o" "core/target/$target/release/libnkg_core.a" "$NCNN_ANDROID_DIR/$abi/lib/libncnn.a" -ldl -lm -llog -landroid -o "app/src/main/jniLibs/$abi/libnkg_jni.so"
  "$ndk_bin/llvm-strip" "app/src/main/jniLibs/$abi/libnkg_jni.so"
done
