#!/usr/bin/env bash
set -euo pipefail
apk=$(find apk -name '*.apk' -print -quit)
test -n "$apk"
adb install -r "$apk"
# A fresh CI AVD has no personal accounts, contacts, location or messages.
# Launch diagnostics below are restricted to component state and fatal stack traces.
trap 'adb logcat -b crash -d -v brief; adb shell dumpsys activity activities | grep -E "ResumedActivity|mFocusedActivity" || true' ERR
adb shell am start -W -n net.meshstar.app/com.bitchat.android.MainActivity > /tmp/meshstar-launch.txt
cat /tmp/meshstar-launch.txt
grep -q 'Status: ok' /tmp/meshstar-launch.txt
sleep 8
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
