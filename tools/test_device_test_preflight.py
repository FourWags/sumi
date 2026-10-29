#!/usr/bin/env python3
"""Verify the real runner refuses uninitialized settings without real adb access."""
from pathlib import Path
import argparse,json,os,subprocess,sys,tempfile,unittest
p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True);args=p.parse_args()
source=Path(__file__).resolve().parents[1]
class PreflightTests(unittest.TestCase):
 def check_refusal(self,default,subtype):
  with tempfile.TemporaryDirectory(prefix='preflight-',dir=args.evidence) as tmp:
   tmp=Path(tmp);bin_dir=tmp/'bin';bin_dir.mkdir();evidence=tmp/'evidence';evidence.mkdir()
   for name in ['apk-audit.json','host-checks.log']:(evidence/name).write_bytes((args.evidence/name).read_bytes())
   log=tmp/'commands.jsonl';fake=bin_dir/'adb'
   fake.write_text('#!'+sys.executable+'\n'+'''import os,sys,json,shlex
args=sys.argv[1:]
with open(os.environ['PETAL_PREFLIGHT_COMMAND_LOG'],'a') as out:out.write(json.dumps(args)+'\\n')
if args[:3]!=['-s','emulator-9998','shell']:sys.exit(91)
command=shlex.split(args[3])
if command[:3]!=['settings','get','secure']:sys.exit(92)
values={'default_input_method':os.environ['PETAL_PREFLIGHT_DEFAULT'],'selected_input_method_subtype':os.environ['PETAL_PREFLIGHT_SUBTYPE'],'enabled_input_methods':'synthetic.system/.Ime','show_ime_with_hard_keyboard':'0'}
print(values[command[3]])
''');fake.chmod(0o700)
   env=dict(os.environ,PATH=str(bin_dir)+os.pathsep+os.environ.get('PATH',''),PETAL_PREFLIGHT_COMMAND_LOG=str(log),PETAL_PREFLIGHT_DEFAULT=default,PETAL_PREFLIGHT_SUBTYPE=subtype)
   result=subprocess.run([sys.executable,str(source/'tools/run_device_tests.py'),'--serial','emulator-9998','--label','synthetic-preflight','--evidence',str(evidence)],env=env,capture_output=True,text=True,timeout=30)
   self.assertNotEqual(0,result.returncode)
   self.assertIn('refusing to install or change settings',result.stdout+result.stderr)
   self.assertTrue((evidence/'synthetic-preflight-blocker.txt').is_file())
   calls=[json.loads(line) for line in log.read_text().splitlines()]
   self.assertEqual(4,len(calls))
   for call in calls:self.assertEqual(['settings','get','secure'],__import__('shlex').split(call[3])[:3])
 def test_no_default_keyboard_blocks_before_mutation(self):self.check_refusal('null','-1')
 def test_no_subtype_blocks_before_mutation(self):self.check_refusal('synthetic.system/.Ime','null')
unittest.main(argv=[sys.argv[0]],verbosity=2)
