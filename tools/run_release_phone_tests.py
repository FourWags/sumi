#!/usr/bin/env python3
"""Legacy rollback self-test for disposable emulators only.
Physical-device replacement is disabled: Sumi app updates must use Obtainium.
The self-test uses synthetic emulator data and requires emulator qualification.
"""
import argparse,hashlib,io,json,os,re,shlex,subprocess,tarfile,zipfile
from pathlib import Path
APP='dev.fourwags.sumi.inputmethod.latin'
IME_KEYS=['default_input_method','enabled_input_methods','selected_input_method_subtype','show_ime_with_hard_keyboard']
READ_ONLY_KEYS=['enabled_accessibility_services','accessibility_enabled','touch_exploration_enabled','touch_exploration_granted_accessibility_services','accessibility_shortcut_target_service','accessibility_button_target_component']
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True);p.add_argument('--evidence',type=Path,required=True);p.add_argument('--backup-dir',type=Path,required=True);p.add_argument('--build-tools',type=Path,required=True);p.add_argument('--self-test',action='store_true');p.add_argument('--expected-tests',type=int,required=True)
a=p.parse_args()
if not (a.self_test and a.serial.startswith('emulator-')):
 raise SystemExit('Physical-device replacement/rollback is disabled. All Sumi app updates must use Obtainium; this legacy helper is limited to disposable emulator self-tests.')
a.evidence.mkdir(parents=True,exist_ok=True)
project=Path(__file__).resolve().parents[1]
assert project not in a.backup_dir.resolve().parents,'Rollback data must be outside source'
assert not a.backup_dir.exists(),'Choose a fresh private rollback directory'
base=['adb','-s',a.serial]
def adb(*items,check=True,raw=False,input=None):
 r=subprocess.run(base+list(items),capture_output=True,check=check,timeout=90,input=input)
 return r.stdout if raw else r.stdout.decode().strip()
def shell(*items,check=True):return adb('shell',shlex.join(items),check=check)
# Even self-test mode must target an actual emulator, not a renamed device.
assert shell('getprop','ro.kernel.qemu')=='1','Rollback self-test requires a disposable emulator'
def snapshot():
 return {'secure':{k:shell('settings','get','secure',k) for k in IME_KEYS+READ_ONLY_KEYS},
         'display':{k:shell('wm',k) for k in ['size','density']}}
def components():
 data=shell('dumpsys','package',APP).split('    User 0:',1)[1].split('\nQueries:',1)[0]
 result={APP+'.Icon'+str(i):'default-state' for i in range(8)}
 for title,value in [('enabledComponents:','enable'),('disabledComponents:','disable')]:
  match=re.search(r'      '+title+r'\n((?:        [^\n]+\n)+)',data)
  if match:
   for line in match.group(1).splitlines():
    name=line.strip()
    if name in result:result[name]=value
 return result
def manifest(blob):
 # Compare bytes and link targets without decoding any application data.
 with tarfile.open(fileobj=io.BytesIO(blob)) as archive:
  result={}
  for member in archive:
   assert not member.name.startswith('/') and '..' not in Path(member.name).parts
   if member.isfile():result[member.name]=hashlib.sha256(archive.extractfile(member).read()).hexdigest()
   elif member.issym():result[member.name]='symlink:'+member.linkname
  return result
def backup_area(path,private):
 prefix=['run-as',APP] if private else []
 exists=shell(*prefix,'sh','-c',f'if [ -d {shlex.quote(path)} ]; then echo yes; fi')=='yes'
 if not exists:return None
 dirs=[]
 if private:
  for name in ['files','shared_prefs','databases']:
   if shell('run-as',APP,'sh','-c',f'if [ -d {shlex.quote(path+"/"+name)} ]; then echo yes; fi')=='yes':dirs.append(name)
 else:dirs=['.']
 if not dirs:return None
 command=(['run-as',APP] if private else [])+['tar','-cf','-','-C',path]+dirs
 data=adb('exec-out',shlex.join(command),raw=True);manifest(data);return data
def external_zip(data):
 blob=io.BytesIO()
 with tarfile.open(fileobj=io.BytesIO(data)) as archive,zipfile.ZipFile(blob,'w',zipfile.ZIP_DEFLATED) as output:
  for entry in archive:
   name=entry.name.removeprefix('./')
   if name in ('','.'):continue
   assert not entry.issym() and not entry.islnk(),'External rollback does not support symlinks'
   if entry.isfile():output.writestr(name,archive.extractfile(entry).read())
 return blob.getvalue()
def restore_area(path,private,data):
 if data is None:return
 if private:
  command=['run-as',APP,'sh','-c',f'mkdir -p {shlex.quote(path)} && cd {shlex.quote(path)} && tar -xf -']
  adb('shell','-T',shlex.join(command),raw=True,input=data)
  return
 # Scoped storage must be recreated by an actual target-app Context, not shell
 # or run-as: their FUSE attribution cannot create the app's external root.
 archive='/data/user/0/'+APP+'/files/petal-rollback-external.zip'
 command=['run-as',APP,'sh','-c','mkdir -p files && cat > '+archive]
 adb('shell','-T',shlex.join(command),raw=True,input=external_zip(data))
 try:
  assert 'Success' in adb('install',str(rollback_helper))
  result=shell('am','instrument','-w','-e','mode','restore','dev.fourwags.sumi.rollback/dev.fourwags.sumi.rollback.RollbackInstrumentation')
  assert 'rollback=PASS' in result,'Target-process external restoration failed'
 finally:
  shell('run-as',APP,'rm','-f',archive)
  adb('uninstall','dev.fourwags.sumi.rollback',check=False)
label='qualified-phone-android17' if not a.self_test else 'rollback-self-test'
audit=json.loads((a.evidence/'apk-audit.json').read_text())
if not a.self_test:
 summary=json.loads((a.evidence/'qualification-summary.json').read_text())
 assert summary['app_sha256']==audit['sha256'] and len(summary['emulators'])==4
 assert all(x['status']=='PASS' and x['settings_restored'] for x in summary['emulators'])
 assert (a.evidence/'rollback-external-ownership-verified.txt').exists(),'Run the isolated-emulator rollback rehearsal first'
policy=shell('dumpsys','window','policy')
if 'inputRestricted=true' in policy or 'showing=true' in policy:
 (a.evidence/(label+'-blocker.txt')).write_text('Phone keyguard is locked. No installation, removal or keyboard selection was performed.\n')
 raise SystemExit('Phone locked; leave qualification pending until unlocked.')
assert not shell('pm','path',APP+'.test',check=False),'Existing test APK must be reviewed first'
assert not shell('pm','path','dev.fourwags.sumi.rollback',check=False),'Existing rollback helper must be reviewed first'
assert shell('run-as',APP,'id','-u').isdigit(),'Previous preview must support an opaque rollback backup'
before=snapshot();aliases=components()
fallback=None
if before['secure']['default_input_method'].startswith(APP+'/'):
 fallback='com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'
 assert fallback in shell('ime','list','-a','-s').splitlines(),'An installed local keyboard is needed while replacing the selected preview' 
a.backup_dir.mkdir(mode=0o700,parents=True,exist_ok=False);os.chmod(a.backup_dir,0o700)
old_path=shell('pm','path',APP).removeprefix('package:');old_apk=a.backup_dir/'previous-preview.apk'
adb('pull',old_path,str(old_apk));old_sha=hashlib.sha256(old_apk.read_bytes()).hexdigest()
assert shell('sha256sum',old_path).split()[0]==old_sha
rollback_helper=project/'tools/rollback-fixture/build/helper-debug.apk'
assert rollback_helper.is_file(),'Build the offline rollback fixture first'
def certificate(apk):
 result=subprocess.check_output([str(a.build_tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
 return re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',result).group(1)
assert certificate(old_apk)==certificate(rollback_helper),'Rollback helper must match previous preview signing identity'
(a.evidence/(label+'-helper-audit.json')).write_text(json.dumps({'sha256':hashlib.sha256(rollback_helper.read_bytes()).hexdigest(),'signer_matches_previous_preview':True},indent=2)+'\n')
areas={'ce':('/data/user/0/'+APP,True),'de':('/data/user_de/0/'+APP,True),'external':('/sdcard/Android/data/'+APP,False)}
backups={name:backup_area(*area) for name,area in areas.items()}
assert backups['de'] is not None,'Device-protected settings backup is required'
assert manifest(backups['de']),'Device-protected backup must not be empty'
if backups['external'] is not None:external_zip(backups['external'])
for name,data in backups.items():
 if data is not None:(a.backup_dir/(name+'.tar')).write_bytes(data)
(a.backup_dir/'rollback-metadata.json').write_text(json.dumps({'settings':before,'aliases':aliases,'previous_apk_sha256':old_sha},indent=2))
for file in a.backup_dir.iterdir():file.chmod(0o600)
(a.evidence/(label+'-rollback-before.json')).write_text(json.dumps(before,indent=2))
mutated=False;passed=False
try:
 mutated=True
 if fallback:
  shell('ime','enable',fallback);shell('ime','set',fallback);shell('ime','disable',APP+'/.LatinIME')
 assert adb('uninstall',APP)=='Success' 
 shell('rm','-rf','/sdcard/Android/data/'+APP)
 if a.self_test:
  assert 'Success' in adb('install',str(old_apk));passed=True
 else:
  r=subprocess.run(['python3',str(project/'tools/run_device_tests.py'),'--serial',a.serial,'--label',label,'--evidence',str(a.evidence)])
  assert r.returncode==0,'Signed-release phone tests failed'
  assert f'OK ({a.expected_tests} tests)' in (a.evidence/(label+'-instrumentation.txt')).read_text()
  adb('pull','/sdcard/Android/data/'+APP+'/files',str(a.evidence/(label+'-files')))
  passed=True
finally:
 if mutated:
  adb('uninstall',APP+'.test',check=False)
  adb('uninstall',APP,check=False)
  shell('rm','-rf','/sdcard/Android/data/'+APP)
  assert 'Success' in adb('install',str(old_apk))
  for name,area in areas.items():restore_area(*area,backups[name])
  for name,area in areas.items():
   actual=backup_area(*area)
   assert (manifest(actual) if actual else {})==(manifest(backups[name]) if backups[name] else {}),'Rollback data mismatch: '+name
  for name,value in aliases.items():shell('run-as',APP,'pm',value,'--user','0',APP+'/'+name)
  old_default=before['secure']['default_input_method']
  if old_default!='null':
   shell('ime','enable',old_default);shell('ime','set',old_default)
  for key in IME_KEYS:
   value=before['secure'][key]
   if value=='null':shell('settings','delete','secure',key)
   else:shell('settings','put','secure',key,value)
  if not old_default.startswith(APP+'/'):shell('am','force-stop',APP)
  assert snapshot()==before,'Phone settings restoration mismatch'
  assert components()==aliases,'Launcher alias restoration mismatch'
  restored_path=shell('pm','path',APP).removeprefix('package:')
  assert shell('sha256sum',restored_path).split()[0]==old_sha,'Previous APK restoration mismatch'
  assert not shell('pm','path',APP+'.test',check=False)
  assert not shell('pm','path','dev.fourwags.sumi.rollback',check=False)
  result={'status':'PASS' if passed else 'FAILED_WITH_ROLLBACK','previous_apk_sha256':old_sha,'settings_restored':True,'previous_preview_restored':True,'petal_data_bytes_restored':True,'launcher_aliases_restored':True,'instrumentation_removed':True,'signed_release_sha256':audit['sha256'],'private_backup_in_evidence':False,'previous_selected_petal_restored':fallback is not None}
  (a.evidence/(label+'-rollback-result.json')).write_text(json.dumps(result,indent=2)+'\n')
  print('Previous preview, Sumi data, launcher aliases and keyboard/device settings restored and verified.',flush=True)
assert passed
