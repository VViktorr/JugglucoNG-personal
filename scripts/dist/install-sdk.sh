#!/usr/bin/env bash
set -euo pipefail
cd "${1:-$(dirname "$0")/../..}"

gradle_string() {
    sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*'([^']+)'.*$/\1/p" Common/build.gradle
}

gradle_number() {
    sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*([0-9]+).*$/\1/p" Common/build.gradle
}

sdk=$(gradle_number COMPILE_SDK)
minor=$(gradle_number COMPILE_SDK_MINOR)
build_tools=$(gradle_string BUILD_TOOLS)
ndk=$(gradle_string ndkver)
cmake=$(gradle_string CMAKEVERSION)
if [[ -z "$sdk" || -z "$minor" || -z "$build_tools" || -z "$ndk" || -z "$cmake" ]]; then
    echo "Missing Android toolchain version in Common/build.gradle" >&2
    exit 1
fi
if [[ "$minor" != 0 ]]; then
    sdk="$sdk.$minor"
fi

sdkmanager="${ANDROID_HOME:?Set ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
"$sdkmanager" --licenses <<< "$(printf 'y\n%.0s' {1..100})" > /dev/null
packages=("platforms;android-$sdk" "build-tools;$build_tools")
if [[ "${ANDROID_SDK_ONLY:-0}" != 1 ]]; then
    packages+=("ndk;$ndk" "cmake;$cmake")
fi
"$sdkmanager" "${packages[@]}"
