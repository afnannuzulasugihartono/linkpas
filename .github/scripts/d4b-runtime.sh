#!/usr/bin/env bash
set -euo pipefail

APK='android/app/build/outputs/apk/debug/app-debug.apk'
PACKAGE='id.barangpas.linkpas'
ACTIVITY='id.barangpas.linkpas/.LauncherActivity'

adb install -r "$APK"
adb shell am force-stop "$PACKAGE"

TRIGGER_OUTPUT="$(adb shell am start -W -n "$ACTIVITY" --ez linkpas_native_diag_test true)"
printf '%s\n' "$TRIGGER_OUTPUT"
echo "$TRIGGER_OUTPUT" | grep -q 'Status: ok'

REPORT_ID=''
for i in $(seq 1 30); do
  PREFS="$(adb shell run-as "$PACKAGE" cat shared_prefs/linkpas_native_diagnostics.xml 2>/dev/null || true)"
  REPORT_ID="$(printf '%s\n' "$PREFS" | sed -n 's/.*name="last_report_id">\([^<]*\)<.*/\1/p' | head -n 1)"
  if [ -n "$REPORT_ID" ]; then
    break
  fi
  sleep 1
done

test -n "$REPORT_ID"
printf '%s\n' "$REPORT_ID" | tee d4b-report-id.txt

adb shell am force-stop "$PACKAGE"
NORMAL_OUTPUT="$(adb shell am start -W -n "$ACTIVITY")"
printf '%s\n' "$NORMAL_OUTPUT"
echo "$NORMAL_OUTPUT" | grep -q 'Status: ok'

if adb logcat -d -v brief | grep -E 'FATAL EXCEPTION:.*|Process: id\.barangpas\.linkpas' | tail -n 40 | grep -q 'id.barangpas.linkpas'; then
  echo 'Unexpected LINKPAS fatal exception detected after diagnostics initialization.' >&2
  exit 1
fi
