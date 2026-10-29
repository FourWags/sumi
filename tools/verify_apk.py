#!/usr/bin/env python3
"""Host gate for Sumi target APK (not the instrumentation APK)."""
import argparse,subprocess,zipfile,struct,hashlib,json
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('apk',type=Path);parser.add_argument('--build-tools',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--require-release',action='store_true');parser.add_argument('--test-apk',type=Path);args=parser.parse_args()
args.output.mkdir(parents=True,exist_ok=True)
def tool(name,*params):return subprocess.check_output([str(args.build_tools/name),*map(str,params)],text=True)
permissions=tool('aapt','dump','permissions',args.apk);(args.output/'apk-permissions.txt').write_text(permissions)
allowed={'android.permission.RECORD_AUDIO','android.permission.VIBRATE','android.permission.READ_USER_DICTIONARY','dev.fourwags.sumi.inputmethod.latin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
import re
badging=tool('aapt','dump','badging',args.apk);(args.output/'apk-badging.txt').write_text(badging)
assert "sdkVersion:'29'" in badging and "targetSdkVersion:'37'" in badging
actual=set(re.findall("uses-permission: name='([^']+)'",permissions));assert actual<=allowed,actual-allowed
assert "android.permission.RECORD_AUDIO" in actual, "Explicit microphone permission is required for user-started voice typing"
manifest=tool('aapt','dump','xmltree',args.apk,'AndroidManifest.xml');(args.output/'apk-manifest.txt').write_text(manifest)
signature=tool('apksigner','verify','--verbose','--print-certs',args.apk)
(args.output/'apk-signature.txt').write_text(signature)
signer=re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',signature).group(1)
variant='release' if args.require_release else 'debug'
if args.require_release:
 assert 'Verified using v3 scheme (APK Signature Scheme v3): true' in signature
 assert 'Number of signers: 1' in signature
 assert 'application-debuggable' not in badging
 assert 'PetalTestActivity' not in manifest and 'PetalLargeFontSettingsActivity' not in manifest
 with zipfile.ZipFile(args.apk) as z:
  for name in z.namelist():
   if name.endswith('.dex'):
    data=z.read(name)
    assert b'PetalTestActivity' not in data and b'PetalTestRunner' not in data and b'PetalLargeFontSettingsActivity' not in data
assert 'android.permission.BIND_INPUT_METHOD' in manifest
assert 'android.service.textservice.SpellCheckerService' not in manifest
for s in ['android.permission.INTERNET','android.permission.READ_SMS','android.permission.RECEIVE_SMS','android.permission.READ_CONTACTS']:assert s not in manifest,s
assert re.search(r'android:allowBackup.*?0x0',manifest)
assert re.search(r'android:fullBackupContent.*?0x0',manifest)
resources=tool('aapt2','dump','resources',args.apk)
rules_match=re.search(r'\bxml/petal_data_extraction_rules\s*\n\s*\(\) \(file\) (\S+)',resources);assert rules_match
rules=tool('aapt','dump','xmltree',args.apk,rules_match.group(1));(args.output/'apk-data-extraction-rules.txt').write_text(rules)
domains={'root','file','database','sharedpref','external','device_root','device_file','device_database','device_sharedpref'}
for section in ('cloud-backup','device-transfer'):
 part=rules.split('E: '+section+' ',1)[1].split('\n  E: ',1)[0]
 assert set(re.findall(r'A: domain="([^"]+)"',part))==domains
 assert part.count('A: path="."')==len(domains)
match=re.search(r'\bxml/method\s*\n\s*\(\) \(file\) (\S+)',resources);assert match,'IME method resource must exist'
method=tool('aapt','dump','xmltree',args.apk,match.group(1));(args.output/'apk-ime-subtype.txt').write_text(method)
assert method.count('E: subtype ')==1,method
assert '"en_US"' in method
alignment=tool('zipalign','-c','-P','16','-v','4',args.apk);(args.output/'apk-alignment.txt').write_text(alignment);assert 'Verification successful' in alignment
native={}
with zipfile.ZipFile(args.apk) as z:
 assert {'assets/bloom.gif','assets/wave.gif'}<=set(z.namelist())
 for name in z.namelist():
  if name.endswith('.so'):
   data=z.read(name);assert data[:4]==b'\x7fELF' and data[4] in (1,2)
   is64=data[4]==2
   offset=struct.unpack_from('<Q' if is64 else '<I',data,32 if is64 else 28)[0];size,count=struct.unpack_from('<HH',data,54 if is64 else 42)
   aligns=[]
   for i in range(count):
    segment=struct.unpack_from('<IIQQQQQQ' if is64 else '<IIIIIIII',data,offset+i*size)
    if segment[0]==1:assert segment[-1]>=(16384 if is64 else 4096),(name,segment[-1]);aligns.append(segment[-1])
   native[name]=aligns
 assert len(native)==3,native
result={'status':'PASS','variant':variant,'signer_sha256':signer,'sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),'permissions':sorted(actual),'native_segment_alignment':native,'subtype':'en_US','minSdk':29,'targetSdk':37}
if args.test_apk:
 test_signature=tool('apksigner','verify','--verbose','--print-certs',args.test_apk)
 (args.output/'test-apk-signature.txt').write_text(test_signature)
 assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',test_signature).group(1)==signer
 result['test_apk_sha256']=hashlib.sha256(args.test_apk.read_bytes()).hexdigest()
(args.output/'apk-audit.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
