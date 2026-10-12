#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_NDK_HOME:?Authorized installed NDK required}"
mkdir -p artifacts/gpu-tiers
for dimensions in '854 480' '1280 720' '1920 1080'; do
 read -r width height <<< "$dimensions"
 python3 - "$width" "$height" <<'PY'
from pathlib import Path
import struct,sys
w,h=map(int,sys.argv[1:]);out=bytearray(w*h*4)
for y in range(h):
 for x in range(w):struct.pack_into('<I',out,(y*w+x)*4,0xff000000|((x*255//(w-1))<<16)|((y*255//(h-1))<<8)|((x+y)%256))
Path('artifacts/gpu-tiers/input.argb').write_bytes(out)
PY
 adb push artifacts/gpu-tiers/input.argb /data/local/tmp/gpu-input-tier.argb > /dev/null
 adb shell /data/local/tmp/vulkan-sr-smoke /data/local/tmp/tinysr2.spv /data/local/tmp/gpu-input-tier.argb /data/local/tmp/gpu-output-tier.argb "$width" "$height" 3 --allow-software > "artifacts/gpu-tiers/${height}p.json"
 adb pull /data/local/tmp/gpu-output-tier.argb artifacts/gpu-tiers/output.argb > /dev/null
 python3 tests/verify-vulkan-tiled.py core/target/release/libnkg_core.so artifacts/gpu-tiers/input.argb artifacts/gpu-tiers/output.argb "$width" "$height" > "artifacts/gpu-tiers/${height}p-parity.json"
 printf 'Completed source-native %sx%s Vulkan/reference test\n' "$width" "$height"
done
