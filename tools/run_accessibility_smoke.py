#!/usr/bin/env python3
"""Extra accessibility smoke tests, restricted to an explicitly chosen emulator."""
import argparse, json, re, shlex, subprocess, sys, time
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--mode', choices=['landscape', 'talkback'], required=True)
p.add_argument('--evidence', type=Path, required=True)
p.add_argument('--label', required=True)
a = p.parse_args()
assert a.serial.startswith('emulator-'), 'Never change phone accessibility/display settings with this tool'
a.evidence.mkdir(parents=True, exist_ok=True)

def shell(*args):
    return subprocess.check_output(['adb', '-s', a.serial, 'shell', shlex.join(args)],
                                   text=True, timeout=30).strip()

if a.mode == 'talkback':
    assert 'package:' in shell('pm', 'path', 'com.google.android.marvin.talkback'), 'TalkBack is not installed'
    keys = ['enabled_accessibility_services', 'accessibility_enabled',
            'touch_exploration_enabled', 'touch_exploration_granted_accessibility_services',
            'accessibility_shortcut_target_service', 'accessibility_button_target_component']
    def snapshot():
        return {k: shell('settings', 'get', 'secure', k) for k in keys}
    def restore(before):
        for key, value in before.items():
            if value == 'null':
                shell('settings', 'delete', 'secure', key)
            else:
                shell('settings', 'put', 'secure', key, value)
    classes = 'dev.fourwags.sumi.inputmethod.latin.PetalTalkBackSmokeTest'
else:
    def snapshot():
        return {k: shell('wm', k) for k in ['size', 'density']}
    def restore(before):
        for key, value in before.items():
            match = re.search('Override ' + key + ': ([^\n]+)', value)
            shell('wm', key, match.group(1) if match else 'reset')
    prefix = 'dev.fourwags.sumi.inputmethod.latin.PetalIntegrationTest#'
    classes = ','.join(prefix + m for m in ['testLargeKeysAlphabetSymbolsAndReturn',
        'testLargeKeyPreview', 'testAccessibilityLargeKeyboardFrameworkAndRevertLabel',
        'testLargeKeysNativeCorrectionAndProtectedInput', 'testLargeEmojiTargetsCategoriesScrollAndActions',
        'testLargeEmojiReflowsAfterModeChange', 'testEditorAndSendStayAboveKeyboard',
        'testToolsMenuReplacesMediaAndStaysWithinKeyboard', 'testAccessibilityToolsTargetsAndFramework'])

before = snapshot()
(a.evidence / (a.label + '-extra-settings-before.json')).write_text(json.dumps(before, indent=2))
try:
    if a.mode == 'talkback':
        shell('settings', 'put', 'secure', 'enabled_accessibility_services',
              'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService')
        shell('settings', 'put', 'secure', 'accessibility_enabled', '1')
        shell('settings', 'put', 'secure', 'touch_exploration_enabled', '1')
        time.sleep(3)
    else:
        shell('wm', 'size', '640x320')
        shell('wm', 'density', '160')
    (a.evidence / (a.label + '-extra-settings-during.json')).write_text(json.dumps(snapshot(), indent=2))
    runner = Path(__file__).with_name('run_device_tests.py')
    result = subprocess.run([sys.executable, str(runner), '--serial', a.serial,
        '--label', a.label, '--evidence', str(a.evidence), '--class-name', classes])
finally:
    restore(before)
    after = snapshot()
    (a.evidence / (a.label + '-extra-settings-after.json')).write_text(json.dumps(after, indent=2))
    assert before == after, 'Additional device settings restoration mismatch'
    print('Additional emulator settings restored and verified.', flush=True)
sys.exit(result.returncode)
