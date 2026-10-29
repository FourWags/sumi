#!/usr/bin/env python3
"""Build an offline test-only rollback helper using the EXISTING debug identity."""
from pathlib import Path
import subprocess,os,zipfile
root=Path(__file__).resolve().parent;build=root/'build';build.mkdir(exist_ok=True)
sdk=Path(os.environ['ANDROID_SDK_ROOT']);jdk=Path(os.environ['JAVA_HOME']);bt=sdk/'build-tools/37.0.0';platform=sdk/'platforms/android-37.0/android.jar'
if not platform.is_file():platform=sdk/'platforms/android-37/android.jar'
assert platform.is_file()
key=Path(os.environ.get('PETAL_DEBUG_KEYSTORE',str(Path.home()/'.android/debug.keystore')));assert key.is_file()
classes=build/'classes';dex=build/'dex';classes.mkdir(exist_ok=True);dex.mkdir(exist_ok=True)
def run(items):subprocess.run(list(map(str,items)),check=True)
run([jdk/'bin/javac','-source','17','-target','17','-classpath',platform,'-d',classes,root/'RollbackInstrumentation.java'])
run([bt/'d8','--min-api','29','--lib',platform,'--output',dex,*classes.rglob('*.class')])
run([bt/'aapt2','link','-I',platform,'--manifest',root/'AndroidManifest.xml','--version-code','1','--version-name','1','-o',build/'helper-unsigned.apk'])
with zipfile.ZipFile(build/'helper-unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
run([bt/'zipalign','-f','-P','16','4',build/'helper-unsigned.apk',build/'helper-aligned.apk'])
# Standard Android debug credentials only. Never uses the production release key.
run([bt/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',build/'helper-debug.apk',build/'helper-aligned.apk'])
run([bt/'apksigner','verify','--verbose','--print-certs',build/'helper-debug.apk'])
