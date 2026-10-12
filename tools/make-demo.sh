#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Original procedural color bars and sine tone; no third-party footage.
ffmpeg -y -f lavfi -i 'testsrc2=size=320x180:rate=24:duration=12' -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=12' -c:v libx264 -profile:v baseline -pix_fmt yuv420p -c:a aac -b:a 64k -movflags +faststart app/src/main/assets/demo.mp4
