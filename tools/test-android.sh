#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p artifacts
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w studio.nkg.tv.test/studio.nkg.tv.FlowInstrumentation > artifacts/android-instrumentation.txt
python3 - <<'PY'
import json
from pathlib import Path
lines=Path('artifacts/android-instrumentation.txt').read_text().splitlines()
result=next((json.loads(s) for s in lines if s.startswith('{"passed"')),None)
assert result and result['passed'], 'Android flow instrumentation failed; inspect log'
PY
./gradlew :app:assembleDebugAndroidTest -PtestRunner=studio.nkg.tv.EnhancerInstrumentation
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w studio.nkg.tv.test/studio.nkg.tv.EnhancerInstrumentation > artifacts/android-enhancer-test.txt
python3 - <<'PY'
import json
from pathlib import Path
lines=Path('artifacts/android-enhancer-test.txt').read_text().splitlines()
result=next((json.loads(s) for s in lines if s.startswith('{"passed"')),None)
assert result and result['passed'], 'Full-frame enhancement test failed; inspect log'
PY
