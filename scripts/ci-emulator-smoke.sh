#!/usr/bin/env bash

set -euo pipefail

apk_path="app/build/outputs/apk/debug/app-debug.apk"
package_name="io.github.togls.hypertweaks"
activity_name="${package_name}/.ui.settings.SettingsActivity"

test -f "${apk_path}"
adb install -r "${apk_path}"
adb logcat -c
adb shell am force-stop "${package_name}"

launch_output="$(adb shell am start -W -n "${activity_name}")"
printf '%s\n' "${launch_output}"
printf '%s\n' "${launch_output}" | rg -q "Status: ok"

sleep 3
adb shell pidof "${package_name}"

crash_output="$(adb logcat -d -b crash)"
if printf '%s\n' "${crash_output}" | rg -q "${package_name}"; then
    printf '%s\n' "${crash_output}"
    exit 1
fi
