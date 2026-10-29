#!/usr/bin/env python3
"""Run synthetic IME tests; restore exact keyboard settings even on failure."""
import subprocess,json,argparse,hashlib,shlex,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--label',required=True);p.add_argument('--evidence',type=Path,required=True);p.add_argument('--timeout',type=int,default=300);p.add_argument('--class-name',default='dev.fourwags.sumi.inputmethod.latin.PetalIntegrationTest,dev.fourwags.sumi.inputmethod.latin.PetalSettingsTest,dev.fourwags.sumi.inputmethod.latin.PetalReliabilityTest');a=p.parse_args()
project=Path(__file__).resolve().parents[1]
audit=json.loads((a.evidence/'apk-audit.json').read_text());variant=audit.get('variant','debug')
apk=project/f'app/build/outputs/apk/{variant}/app-{variant}.apk'
test=project/f'app/build/outputs/apk/androidTest/{variant}/app-{variant}-androidTest.apk'
if 'test_apk_sha256' in audit: assert hashlib.sha256(test.read_bytes()).hexdigest()==audit['test_apk_sha256'],'Test APK differs from audited APK'
assert audit['status']=='PASS' and hashlib.sha256(apk.read_bytes()).hexdigest()==audit['sha256']
assert 'BUILD SUCCESSFUL' in (a.evidence/'host-checks.log').read_text()
(a.evidence/(a.label+'-apk-sha256.txt')).write_text(audit['sha256']+'\n')
if 'test_apk_sha256' in audit: (a.evidence/(a.label+'-test-apk-sha256.txt')).write_text(audit['test_apk_sha256']+'\n')
base=['adb','-s',a.serial]
def adb(*cmd,timeout=90,check=True):
 remote=['shell',shlex.join(cmd[1:])] if cmd[0]=='shell' else list(cmd)
 return subprocess.run(base+remote,capture_output=True,text=True,timeout=timeout,check=check).stdout.strip()
if not a.serial.startswith('emulator-'):
 summary=json.loads((a.evidence/'qualification-summary.json').read_text())
 assert summary['app_sha256']==audit['sha256'] and all(x['status']=='PASS' and x['settings_restored'] for x in summary['emulators']), 'Emulator qualification must match this APK before phone testing'
 policy=adb('shell','dumpsys','window','policy')
 if 'inputRestricted=true' in policy or 'showing=true' in policy:
  (a.evidence/(a.label+'-blocker.txt')).write_text('Phone is locked; keyguard blocks the synthetic editor. No installation or keyboard selection was performed.\n')
  raise SystemExit('Phone is locked. Unlock it before running synthetic UI tests.')
keys=['default_input_method','enabled_input_methods','selected_input_method_subtype','show_ime_with_hard_keyboard']
before={key:adb('shell','settings','get','secure',key) for key in keys}
(a.evidence/(a.label+'-settings-before.json')).write_text(json.dumps(before,indent=2))
if before['default_input_method']=='null' or before['selected_input_method_subtype']=='null':
 (a.evidence/(a.label+'-blocker.txt')).write_text('Keyboard selection/subtype is not initialized. Exact restoration cannot be guaranteed while Android is choosing defaults. No installation or keyboard selection was performed. Initialize the isolated test emulator keyboard, then retry.\n')
 raise SystemExit('Keyboard selection/subtype is not initialized; refusing to install or change settings before a restorable baseline exists.')
info={key:adb('shell','getprop',key) for key in ['ro.build.version.release','ro.build.version.sdk','ro.product.model']};info['page_size']=adb('shell','getconf','PAGE_SIZE')
(a.evidence/(a.label+'-device.json')).write_text(json.dumps(info,indent=2));result='';failed=None
try:
 if a.serial.startswith('emulator-'):
  print(adb('install','-r',str(apk)),flush=True);print(adb('install','-r',str(test)),flush=True)
 else:
  # Physical app updates are exclusively Obtainium-managed. Require both exact
  # target and an already provisioned test package; never replace either here.
  test_path=adb('shell','pm','path','dev.fourwags.sumi.inputmethod.latin.test').removeprefix('package:')
  assert test_path and test_path.startswith('/'), 'Provision the matching instrumentation package separately before phone testing'
  assert adb('shell','sha256sum',test_path).split()[0]==hashlib.sha256(test.read_bytes()).hexdigest(), 'Installed instrumentation differs from the qualified test APK'

 installed=adb('shell','pm','path','dev.fourwags.sumi.inputmethod.latin').removeprefix('package:')
 installed_sha=adb('shell','sha256sum',installed).split()[0]
 assert installed_sha==audit['sha256'],'Installed APK differs from audited APK'
 (a.evidence/(a.label+'-installed-sha256.txt')).write_text(installed_sha+'\n')
 # The instrumentation first opens the synthetic editor, then selects Sumi
 # inside its setup. Never activate an IME over an unrelated foreground editor.
 adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
 result=adb('shell','am','instrument','-w','-r','-e','class',a.class_name,'dev.fourwags.sumi.inputmethod.latin.test/dev.fourwags.sumi.inputmethod.latin.PetalTestRunner',timeout=a.timeout)
 (a.evidence/(a.label+'-instrumentation.txt')).write_text(result);print(result,flush=True)
 assert 'FAILURES!!!' not in result and 'OK (' in result and 'INSTRUMENTATION_FAILED' not in result,'Instrumentation did not report passing tests'
except Exception as e:
 failed=str(e);print('TEST FAILURE: '+failed,flush=True)
finally:
 if before['default_input_method']!='null':adb('shell','ime','set',before['default_input_method'],check=False)
 for key,value in before.items():
  if value=='null':adb('shell','settings','delete','secure',key,check=False)
  else:adb('shell','settings','put','secure',key,value,check=False)
 after={key:adb('shell','settings','get','secure',key) for key in keys}
 (a.evidence/(a.label+'-settings-after.json')).write_text(json.dumps(after,indent=2));assert before==after,'Keyboard setting restoration mismatch'
 print('Keyboard settings restored and verified.',flush=True)
 if failed:raise SystemExit(1)
