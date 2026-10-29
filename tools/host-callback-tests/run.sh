#!/usr/bin/env bash
set -euo pipefail
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
: "${JAVA_HOME:?Set JAVA_HOME to a full JDK}"
: "${PETAL_CALLBACK_TEST_OUTPUT:?Set PETAL_CALLBACK_TEST_OUTPUT to a project evidence directory}"
mkdir -p "$PETAL_CALLBACK_TEST_OUTPUT/classes"
mapfile -d '' fixtures < <(find "$source_root/tools/host-callback-tests/fixtures" -name '*.java' -print0)
"$JAVA_HOME/bin/javac" -d "$PETAL_CALLBACK_TEST_OUTPUT/classes" "${fixtures[@]}" \
 "$source_root/app/src/main/java/dev/fourwags/sumi/inputmethod/latin/inputlogic/InputLogicHandler.java"
"$JAVA_HOME/bin/java" -cp "$PETAL_CALLBACK_TEST_OUTPUT/classes" dev.fourwags.sumi.inputmethod.latin.inputlogic.CallbackRegression
