#!/usr/bin/env bash
set -euo pipefail
cd "${1:-$(dirname "$0")/../..}"
ndk=$(sed -n "s/^ *ndkver *= *'\(.*\)'.*/\1/p" Common/build.gradle)
cmake=$(sed -n "s/^ *CMAKEVERSION *= *'\(.*\)'.*/\1/p" Common/build.gradle)
sdk=$(sed -n 's/^ *TARGETSDK *= *\([0-9]*\).*/\1/p' Common/build.gradle)
compile_sdk=$(sed -nE 's/^ *version = release\(([0-9]+)\) *\{.*/\1/p' Common/build.gradle)
minor_sdk=$(sed -nE 's/^ *minorApiLevel *= *([0-9]+).*/\1/p' Common/build.gradle)
build_tools=$(sed -nE "s/^ *buildToolsVersion *= *'([^']+)'.*/\1/p" Common/build.gradle)
test -n "$ndk" && test -n "$cmake" && test -n "$sdk" && test -n "$compile_sdk" && test -n "$build_tools"
if [[ -n "$minor_sdk" ]]; then
    compile_sdk="$compile_sdk.$minor_sdk"
fi
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
"$sdkmanager" --licenses <<< "$(printf 'y\n%.0s' {1..100})" > /dev/null
"$sdkmanager" "ndk;$ndk" "cmake;$cmake" "platforms;android-$sdk" \
    "platforms;android-$compile_sdk" "build-tools;$build_tools"
