#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools
curl -fL --retry 2 https://github.com/Tencent/ncnn/releases/download/20250503/ncnn-20250503-android.zip -o .tools/ncnn.zip
python3 - <<'PY'
from pathlib import Path
import hashlib,zipfile
p=Path('.tools/ncnn.zip')
expected='d3c81c221a6f942dfdb5242e7a189a97f20006d6b1d62daae325b1f6b81a17a0'
assert hashlib.sha256(p.read_bytes()).hexdigest()==expected, 'ncnn archive checksum mismatch'
with zipfile.ZipFile(p) as z:z.extractall('.tools')
PY
printf 'NCNN_ANDROID_DIR=%s/.tools/ncnn-20250503-android\n' "$PWD"
