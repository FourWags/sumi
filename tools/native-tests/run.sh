#!/usr/bin/env bash
# Host ASan/UBSan regression of the production coordinate lookup. No device needed.
set -euo pipefail
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
: "${JAVA_HOME:?Set JAVA_HOME to a full JDK}"
: "${PETAL_NATIVE_TEST_OUTPUT:?Set PETAL_NATIVE_TEST_OUTPUT to a project evidence folder}"
mkdir -p "$PETAL_NATIVE_TEST_OUTPUT"
cxx="${CXX:-g++}"
"$cxx" -std=c++17 -g -O1 -fsanitize=address,undefined -fno-sanitize-recover=all -fno-omit-frame-pointer -fno-pie -no-pie \
  -ffunction-sections -fdata-sections -Wl,--gc-sections \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I"$source_root/app/src/main/jni/src" \
  "$source_root/tools/native-tests/proximity_bounds.cpp" \
  "$source_root/app/src/main/jni/src/suggest/core/layout/proximity_info.cpp" \
  "$source_root/app/src/main/jni/src/suggest/core/layout/proximity_info_params.cpp" \
  "$source_root/app/src/main/jni/src/utils/char_utils.cpp" \
  "$source_root/app/src/main/jni/src/suggest/core/layout/additional_proximity_chars.cpp" \
  -o "$PETAL_NATIVE_TEST_OUTPUT/proximity_bounds"
python3 - "$PETAL_NATIVE_TEST_OUTPUT" <<'PY'
import json,pathlib,subprocess,sys
out=pathlib.Path(sys.argv[1]).resolve(); results=[]
cases=[('top_left',0,0),('bottom_right',99,39),('last_row',0,39),('negative_x',-1,0),('negative_y',0,-1),('bottom_edge',0,40),('right_edge',100,0),('far_right',400,0),('int_max',2147483647,2147483647),('int_min',-2147483648,-2147483648)]
for name,x,y in cases:
 p=subprocess.run([str(out/'proximity_bounds'),str(x),str(y)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 (out/(name+'.log')).write_text(p.stdout)
 results.append(dict(case=name,x=x,y=y,exit=p.returncode))
(out/'results.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps(results,indent=2))
if any(r['exit'] for r in results):sys.exit(1)
PY
