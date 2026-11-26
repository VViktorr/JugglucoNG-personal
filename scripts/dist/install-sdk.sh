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
if [[ "${ANDROID_SDK_ONLY:-0}" == 1 ]]; then
    "$sdkmanager" "${packages[@]}"
    exit
fi

if [[ "$cmake" != 4.5.1 ]]; then
    "$sdkmanager" "${packages[@]}" "ndk;$ndk" "cmake;$cmake"
    exit
fi

# SDK Manager does not distribute 4.5.1. AGP resolves an exact version on PATH.
if [[ -n "${GITHUB_PATH:-}" && "$(uname -sm)" == "Linux x86_64" ]]; then
    cmake_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/juggluco-cmake.XXXXXX")
    archive="cmake-$cmake-linux-x86_64.tar.gz"
    curl --fail --silent --show-error --location --retry 3 \
        "https://github.com/Kitware/CMake/releases/download/v$cmake/$archive" \
        -o "$cmake_dir/$archive"
    printf '%s  %s\n' '83b71181fb62b4fe8f26067a9321448572e7a43bd04ff6c7f54a24c33698f16d' \
        "$cmake_dir/$archive" | sha256sum --check
    tar -xzf "$cmake_dir/$archive" -C "$cmake_dir" --strip-components=1
    printf '%s\n' "$cmake_dir/bin" >> "$GITHUB_PATH"
elif [[ "$(cmake --version 2>/dev/null | sed -n '1p')" != "cmake version $cmake" ]]; then
    echo "Install Kitware CMake $cmake and add its bin directory to PATH." >&2
    exit 1
fi

"$sdkmanager" "${packages[@]}" "ndk;$ndk"
