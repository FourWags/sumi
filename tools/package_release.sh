#!/usr/bin/env bash
# Build and qualify an update-compatible non-debuggable preview. Does not publish/install.
set -euo pipefail
project_home="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
if (( $# != 2 )); then echo 'Usage: package_release.sh PREVIOUS_VERIFIED_APK EVIDENCE_DIRECTORY' >&2; exit 2; fi
previous_apk="$(realpath -- "$1")"
evidence_dir="$(realpath -m -- "$2")"
: "${PETAL_SIGNING_CONFIG:?Set PETAL_SIGNING_CONFIG to the private established signing configuration}"
source "$project_home/env.sh"
mkdir -p "$evidence_dir"
python3 "$project_home/tools/check_public_media.py"
"$project_home/build.sh" --offline --rerun-tasks --max-workers=2 :app:assembleRelease :app:testDebugUnitTest :app:lintRelease > "$evidence_dir/host-checks.log" 2>&1
apk="$project_home/app/build/outputs/apk/release/app-release.apk"
build_tools="$ANDROID_HOME/build-tools/37.0.0"
python3 "$project_home/tools/verify_apk.py" "$apk" --build-tools "$build_tools" --output "$evidence_dir" --require-release
python3 - "$build_tools/apksigner" "$previous_apk" "$apk" "$evidence_dir" <<'PY'
import subprocess,re,sys,json
from pathlib import Path
signer,previous,current,evidence=sys.argv[1:]
def certificate(path):
 text=subprocess.check_output([signer,'verify','--print-certs',path],text=True)
 return re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',text).group(1)
old,new=certificate(previous),certificate(current)
if old!=new:raise SystemExit('Signing identity differs from the verified installed lineage; do not distribute this APK.')
Path(evidence,'signer-continuity.json').write_text(json.dumps({'matches_previous_verified_apk':True,'sha256':new},indent=2))
print('Non-debuggable candidate passed packaging and signing-continuity gates. No publication or phone update performed.')
PY
