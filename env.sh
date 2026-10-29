#!/usr/bin/env bash
# Source this file for local development, or use caller-supplied JDK/SDK in a source export.
SumiProjectHome="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
if [[ -x "$SumiProjectHome/.tools/jdk-21/bin/javac" ]]; then
    export JAVA_HOME="$SumiProjectHome/.tools/jdk-21"
elif [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo 'Set JAVA_HOME to a full JDK21 installation.' >&2
    return 1
fi
if [[ -d "$SumiProjectHome/.tools/android-sdk/platforms" ]]; then
    export ANDROID_HOME="$SumiProjectHome/.tools/android-sdk"
else
    export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME/platforms" ]]; then
        echo 'Set ANDROID_HOME to an Android SDK with the required build tools, NDK and CMake.' >&2
        return 1
    fi
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="$SumiProjectHome/work/avds"
SumiEmulatorHome="$ANDROID_HOME/emulator"
if [[ -d "$ANDROID_HOME/emulator-2" ]]; then SumiEmulatorHome="$ANDROID_HOME/emulator-2"; fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$SumiEmulatorHome:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
unset SumiProjectHome SumiEmulatorHome
