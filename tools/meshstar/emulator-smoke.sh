#!/usr/bin/env bash
set -euo pipefail
apk=$(find apk -name '*.apk' -print -quit)
test -n "$apk"
adb install -r "$apk"
# A fresh CI AVD has no personal accounts, contacts, location or messages.
# Launch diagnostics below are restricted to component state and fatal stack traces.
trap 'adb logcat -b crash -d -v brief; adb shell dumpsys activity activities | grep -E "ResumedActivity|mFocusedActivity" || true' ERR
# Android's am -W has a fixed cold-start timeout on busy shared runners.
# Allow completion of the same launch, then require real visible UI below.
for attempt in 1 2 3; do
  adb shell am start -W -n net.meshstar.app/com.bitchat.android.MainActivity > /tmp/meshstar-launch.txt
  cat /tmp/meshstar-launch.txt
  if grep -q 'Status: ok' /tmp/meshstar-launch.txt; then break; fi
  grep -q 'Status: timeout' /tmp/meshstar-launch.txt
  sleep 3
done
grep -q 'Status: ok' /tmp/meshstar-launch.txt
sleep 8
adb shell uiautomator dump /sdcard/meshstar-ui.xml >/dev/null
adb shell cat /sdcard/meshstar-ui.xml | grep -q 'MeshStar/'
# Exercise activity disposal/recreation, including devices with no Bluetooth adapter.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
adb shell settings put system user_rotation 0
sleep 3
adb shell pidof net.meshstar.app >/dev/null
adb shell dumpsys activity activities > /tmp/meshstar-activities.txt
grep -E '(mResumedActivity|topResumedActivity|mFocusedActivity).*net.meshstar.app' /tmp/meshstar-activities.txt
printf 'MeshStar launch passed on API %s\n' "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

# Only synthetic fixture state; the runner substitutes Application and starts no
# application startup services. Link tests exercise MainActivity without granting
# radio/location permissions; AndroidKeyStore remains the real provider.
test_apk=$(find test-apk -name '*.apk' -print -quit)
adb install -r "$test_apk"
adb shell am instrument -w -e class net.meshstar.KeystoreInstrumentedTest,net.meshstar.SatelliteUiInstrumentedTest,net.meshstar.AppLinkInstrumentedTest,net.meshstar.UnifiedUiInstrumentedTest,net.meshstar.MeshCoreUiInstrumentedTest \
  net.meshstar.app.test/com.bitchat.android.ui.PerformanceTestRunner > /tmp/meshstar-instrumentation.txt
cat /tmp/meshstar-instrumentation.txt
grep -q 'OK (9 tests)' /tmp/meshstar-instrumentation.txt
