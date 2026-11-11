#!/usr/bin/env bash
set -euo pipefail
apk=$(find apk -name '*.apk' -print -quit)
test -n "$apk"
adb install -r "$apk"
adb shell am start -W -n net.meshstar.app/com.bitchat.android.MainActivity > /tmp/meshstar-launch.txt
grep -q 'Status: ok' /tmp/meshstar-launch.txt
# Allow initialization to settle; assert a live process and a resumed app activity.
sleep 8
adb shell pidof net.meshstar.app >/dev/null
adb shell dumpsys activity activities | grep -E '(mResumedActivity|topResumedActivity).*net.meshstar.app' >/dev/null
printf 'MeshStar launch passed on API %s\n' "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
