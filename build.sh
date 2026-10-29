#!/usr/bin/env bash
set -euo pipefail
project_home="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "$project_home/env.sh"
if (( $# == 0 )); then set -- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug; fi
cd -- "$project_home"
exec "$JAVA_HOME/bin/java" -classpath "$project_home/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain --project-dir "$project_home" --no-daemon "$@"
