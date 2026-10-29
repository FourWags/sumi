#!/usr/bin/env python3
"""Actual Google TalkBack/Switch Access tests, restricted to a disposable emulator.
Requires Pillow, root-capable AOSP/Google APIs image, and preinstalled services.
Switch Access: configure Volume Down=Next, Volume Up=Select, auto-select ON,
linear scanning except keyboard. Never use this tool on a physical phone.
"""
import argparse, io, json, shlex, struct, subprocess, sys, time
from pathlib import Path
from PIL import Image

PHASES = {'q':113,'space':32,'delete-space':-5,'delete-q':-5,'symbols':-3,
 'underscore':95,'alphabet':-3,'tools':'Keyboard tools','close-tools':'Close tools',
 'type-t':116,'type-e':101,'type-h':104,'correct-space':32,'backspace-undo':-5,
 'again-t':116,'again-e':101,'again-h':104,'again-correct-space':32,
 'visible-revert':'Revert correction to','password-t':116,'password-e':101,
  'password-h':104,'password-space':32,
 'tools-edit':'Keyboard tools','select-all':'Select all','tools-copy':'Keyboard tools','copy':'Copy',
 'tools-clipboard':'Keyboard tools','clipboard-open':'Clipboard','clipboard-paste':'Paste',
 'tools-editing':'Keyboard tools','editing-open':'Editing panel','editing-left':'Cursor left','editing-select':'Select left','editing-back':'Back to tools',
 'tools-settings':'Keyboard tools','settings-open':'Settings','settings-layout':'Layout','settings-standard':'Standard QWERTY'}
SERVICES = {
 'talkback':'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService',
 'switch':'com.google.android.accessibility.switchaccess/com.android.switchaccess.SwitchAccessService'}
KEYS = ['enabled_accessibility_services','accessibility_enabled','touch_exploration_enabled',
 'touch_exploration_granted_accessibility_services','accessibility_shortcut_target_service',
 'accessibility_button_target_component','default_input_method','enabled_input_methods',
 'selected_input_method_subtype','show_ime_with_hard_keyboard']
STATE = '/sdcard/Android/data/dev.fourwags.sumi.inputmethod.latin/files/switch-state.json'
APP = 'dev.fourwags.sumi.inputmethod.latin'


def blue_boxes(png):
    im=Image.open(io.BytesIO(png)).convert('RGB');pixels=im.load();blue=set()
    for y in range(im.height):
        for x in range(im.width):
            r,g,b=pixels[x,y]
            if (r,g,b) in [(33,150,243),(13,71,161)]:blue.add((x,y))
    boxes=[]
    while blue:
        stack=[blue.pop()];xs=[];ys=[]
        while stack:
            x,y=stack.pop();xs.append(x);ys.append(y)
            for other in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if other in blue:blue.remove(other);stack.append(other)
        if max(xs)-min(xs)>=20 and max(ys)-min(ys)>=20:boxes.append([min(xs),min(ys),max(xs)+1,max(ys)+1])
    return boxes


def category_group_outline(png, bounds):
    # Keyboard row groups outline several adjacent tabs. A horizontal-scroll
    # action outlines just the enclosing strip, with no internal tab borders.
    im=Image.open(io.BytesIO(png)).convert('RGB');pixels=im.load()
    left,top,right,bottom=bounds;height=bottom-top
    columns=0;previous=False
    for x in range(max(0,left+8),min(im.width,right-8)):
        blue=sum(pixels[x,y] in [(33,150,243),(13,71,161)]
                 for y in range(max(0,top+4),min(im.height,bottom-4)))
        current=blue>=max(8,(height-8)*.7)
        if current and not previous:columns+=1
        previous=current
    return columns>=2


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--mode',choices=SERVICES,required=True)
    parser.add_argument('--scope',choices=['all'],default='all')
    parser.add_argument('--label',required=True)
    parser.add_argument('--evidence',type=Path,required=True)
    args=parser.parse_args()
    assert args.serial.startswith('emulator-'), 'Only disposable emulators are supported'
    args.evidence.mkdir(parents=True,exist_ok=True)
    prefix=args.evidence/args.label
    def save(suffix,data):
        Path(str(prefix)+suffix).write_text(json.dumps(data,indent=2)+'\n')
    def adb(*items,check=True):
        return subprocess.run(['adb','-s',args.serial,*items],capture_output=True,check=check,timeout=30).stdout
    def shell(*items):return adb('shell',shlex.join(items)).decode().strip()
    def wait_uid(expected=None):
        # adbd can briefly report online before its restarted shell is ready.
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            try:
                current=shell('id','-u')
                if current.isdigit() and (expected is None or current==expected):return current
            except subprocess.CalledProcessError:
                pass
            time.sleep(.25)
        raise RuntimeError('Emulator adbd did not become ready with expected UID '+str(expected))

    def settings():return {k:shell('settings','get','secure',k) for k in KEYS}
    def state():
        end=time.monotonic()+5
        while time.monotonic()<end:
            try:return json.loads(adb('exec-out','cat',STATE))
            except (json.JSONDecodeError,subprocess.CalledProcessError):time.sleep(.1)
        raise RuntimeError('Synthetic fixture state unavailable')
    def events(device,items):
        # Numeric event tuples only; these enter InputReader and the actual service filter.
        adb('shell','; '.join('sendevent /dev/input/%s %d %d %d'%(device,*item) for item in items))
    def key(code):
        events('event1',[(1,code,1),(0,0,0)]);time.sleep(.07)
        events('event1',[(1,code,0),(0,0,0)])
        time.sleep(.35)
    tracking=10
    def down(x,y,w,h):
        nonlocal tracking
        tracking+=1;x=round(x*32767/(w-1));y=round(y*32767/(h-1))
        events('event2',[(3,47,0),(3,57,tracking),(3,53,x),(3,54,y),
                         (3,58,50),(3,48,6),(3,55,0),(0,0,0)])
    def double_tap(x,y,w,h):
        nonlocal tracking
        x=round(x*32767/(w-1));y=round(y*32767/(h-1));commands=[]
        for tap in range(2):
            tracking+=1
            down_events=[(3,47,0),(3,57,tracking),(3,53,x),(3,54,y),(3,58,50),(3,48,6),(3,55,0),(0,0,0)]
            up_events=[(3,47,0),(3,57,-1),(3,58,0),(0,0,0)]
            for suffix,items in [('down',down_events),('up',up_events)]:
                remote='/data/local/tmp/petal-touch-'+str(tap)+'-'+suffix
                packet=b''.join(struct.pack('<qqHHi',0,0,*item) for item in items)
                subprocess.run(['adb','-s',args.serial,'shell','cat > '+remote],input=packet,check=True,capture_output=True,timeout=30)
            commands.extend(['cat /data/local/tmp/petal-touch-'+str(tap)+'-down > /dev/input/event2',
                             'sleep 0.04','cat /data/local/tmp/petal-touch-'+str(tap)+'-up > /dev/input/event2','sleep 0.08'])
        adb('shell','; '.join(commands))
    def up():events('event2',[(3,47,0),(3,57,-1),(3,58,0),(0,0,0)])
    service=SERVICES[args.mode]
    assert shell('pm','path',service.split('/')[0]).startswith('package:'), 'Service not installed'
    before=settings();save('-service-settings-before.json',before)
    root_before=shell('id','-u');runner=None;proof=[];result=None
    try:
        if root_before!='0':
            adb('root');adb('wait-for-device')
        assert wait_uid('0')=='0','Emulator does not support raw kernel input'
        devices=shell('getevent','-lp')
        Path(str(prefix)+'-input-devices.txt').write_text(devices)
        assert '/dev/input/event1' in devices and 'AT Translated Set 2 keyboard' in devices
        assert '/dev/input/event2' in devices and 'virtio_input_multi_touch_1' in devices
        shell('settings','put','secure','enabled_accessibility_services',service)
        shell('settings','put','secure','accessibility_enabled','1')
        shell('settings','put','secure','touch_exploration_enabled','1' if args.mode=='talkback' else '0')
        time.sleep(3)
        Path(str(prefix)+'-service-state.txt').write_text(shell('dumpsys','accessibility'))
        Path(str(prefix)+'-service-package.txt').write_text(shell('dumpsys','package',service.split('/')[0]))
        shell('rm','-f',STATE,str(Path(STATE).with_name('switch-state.tmp')),str(Path(STATE).with_name('emoji-service-tree.json')))
        method='talkBackTouchKeyboardAndTools' if args.mode=='talkback' else 'hardwareSwitchKeyboardAndTools'
        if args.scope=='media':
            assert args.mode=='switch','Media-only probe supports Switch Access'
            method='hardwareSwitchMediaOnly'
        log=open(str(prefix)+'-runner.log','w')
        runner=subprocess.Popen([sys.executable,str(Path(__file__).with_name('run_device_tests.py')),
            '--serial',args.serial,'--label',args.label,'--evidence',str(args.evidence),
            '--class-name',APP+'.PetalSwitchAccessTest#'+method,'--timeout','1200'],stdout=log,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            if runner.poll() is not None:raise RuntimeError('Instrumentation ended during setup')
            try:
                s=state()
                if s['phase']==('q' if args.scope=='all' else 'emoji-media'):break
            except RuntimeError:pass
        else:raise RuntimeError('Fixture did not start')
        last=None
        for step in range(2000):
            s=state();phase=s['phase']
            if phase=='PASS':
                save('-completed.json',s);break
            if runner.poll() is not None:raise RuntimeError('Instrumentation ended before workflow completed')
            if phase!=last:print('Phase '+phase,flush=True);last=phase
            want=PHASES[phase]
            targets=[t for t in s['targets'] if (t['code']==want if isinstance(want,int) else t['label'].startswith(want))]
            if isinstance(want,str):targets=([t for t in targets if t['label']==want] or targets)
            if not targets:
                time.sleep(.1);continue  # Allow RecyclerView content to finish laying out.
            assert targets,'Missing target '+str(want)
            target=targets[0];bounds=target['bounds'];x=(bounds[0]+bounds[2])/2;y=(bounds[1]+bounds[3])/2
            png=adb('exec-out','screencap','-p');w,h=Image.open(io.BytesIO(png)).size
            assert 0<=x<w and 0<=y<h,(phase,bounds)
            if args.mode=='switch':
                boxes=blue_boxes(png);select=any(b[0]<=x<b[2] and b[1]<=y<b[3]
                           and b[3]-b[1]<=(bounds[3]-bounds[1])*1.5
                           and (phase!='emoji-category' or b[2]-b[0]<=(bounds[2]-bounds[0])*1.5
                                or category_group_outline(png,b)) for b in boxes)
                proof.append({'phase':phase,'action':'Select' if select else 'Next','highlight':boxes,'target':target,'text':s['text']})
                if select:Path(str(prefix)+'-'+phase+'-'+str(step)+'.png').write_bytes(png)
                key(115 if select else 114)
                if select:time.sleep(.4)
            else:
                regular=(isinstance(want,str) and want!='emoji-first') or phase in ['emoji-space','emoji-delete-space','emoji-delete','emoji-return']
                if phase=="emoji-insert":time.sleep(1)
                down(x,y,w,h);time.sleep(.55);focused=state()
                Path(str(prefix)+'-focus-'+phase+'.png').write_bytes(adb('exec-out','screencap','-p'))
                proof.append({'phase':phase,'target':target,'focusedDescription':focused.get('focusedDescription'),
                              'focusedBounds':focused.get('focusedBounds'),
                              'gesture':'explore then double tap' if regular else 'explore then lift','text_before':s['text']})
                up()
                if regular:
                    time.sleep(.4);double_tap(x,y,w,h);time.sleep(.4)
                deadline=time.monotonic()+6
                while time.monotonic()<deadline:
                    if state()['phase']!=phase:break
                    time.sleep(.1)
                else:raise RuntimeError('Gesture did not complete phase '+phase)
        else:raise RuntimeError('Workflow exhausted scan limit')
        runner.wait(timeout=30);log.close()
        assert runner.returncode==0,'Instrumentation assertions failed'
        result={'status':'PASS','mode':args.mode,'phases':len(PHASES) if args.scope=='all' else 11,'synthetic_only':True}
    finally:
        try:
            if wait_uid()=='0':
                up();shell('rm','-f',*[f'/data/local/tmp/petal-touch-{n}-{suffix}' for n in range(2) for suffix in ['down','up']])
            if runner is not None and runner.poll() is None:
                shell('am','force-stop',APP);runner.wait(timeout=30)
            shell('settings','delete','secure','enabled_accessibility_services');time.sleep(2)
            for k,value in before.items():
                shell('settings','delete','secure',k) if value=='null' else shell('settings','put','secure',k,value)
            time.sleep(1);after=settings();save('-service-settings-after.json',after)
            assert before==after,'Accessibility/keyboard settings restoration mismatch'
            if result:result['settings_restored']=True
        finally:
            save('-driver.json',proof)
            if root_before!='0':adb('unroot');adb('wait-for-device')
            assert wait_uid(root_before)==root_before
    if result:
        result['adbd_uid_restored']=True;save('-result.json',result)
    print('Actual '+args.mode+' service workflow PASS; settings restored.',flush=True)

if __name__=='__main__':main()
