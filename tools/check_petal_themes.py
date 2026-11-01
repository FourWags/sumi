#!/usr/bin/env python3
"""Verify approved named palette mappings, native styles, icon aliases and contrast."""
from pathlib import Path
import json,re,xml.etree.ElementTree as E
p=Path(__file__).resolve().parents[1];res=p/'app/src/main/res'
data=json.loads((p/'tools/petal_wake_palettes.json').read_text());rows=data['palettes'];assert len(rows)==21
styles={s.get('name'):{i.get('name'):i.text for i in s} for s in E.parse(res/'values/petal_themes.xml').getroot() if s.tag=='style'}
def luminance(c):
 vals=[int(c[i:i+2],16)/255 for i in [1,3,5]]
 return sum(w*(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4) for w,v in zip([.2126,.7152,.0722],vals))
def contrast(a,b):return (max(luminance(a),luminance(b))+.05)/(min(luminance(a),luminance(b))+.05)
ns='{http://schemas.android.com/apk/res/android}';manifest=E.parse(p/'app/src/main/AndroidManifest.xml');all_aliases=manifest.findall('application/activity-alias');aliases=[a for a in all_aliases if re.fullmatch(r'dev\.fourwags\.sumi\.inputmethod\.latin\.Icon\d+',a.get(ns+'name',''))];assert len(aliases)==42
shiba_aliases=[a for a in all_aliases if a.get(ns+'name','').startswith('${applicationId}.FourWagsIcon_')];assert len(shiba_aliases)==3
assert {a.get(ns+'icon') for a in shiba_aliases}=={'@mipmap/fw_sumi_'+i for i in 'abc'}
approved={r['id']:r for r in json.loads((p/'tools/approved_named_color_pairs.json').read_text())}
assert len(approved)==18
minimum=21
for i,row in enumerate(rows):
 k=row['keyboard']
 if row['id'] in approved:
  assert [k['surface'],k['key'],k['function']]==approved[row['id']]['paired'];assert row['name']==approved[row['id']]['name'];assert k==approved[row['id']]['keyboard']
 native=styles['PetalTheme.'+row['id'].title()]
 for name,value in k.items():assert native[('sumi' if name.startswith('key') else 'petal')+name[0].upper()+name[1:]]==value
 ratios=[contrast(k['ink'],k['key']),contrast(k['ink'],k['function']),contrast(k['stripInk'],k['surface'])];minimum=min(minimum,*ratios);assert min(ratios)>=4.5
 if row['legacy_index']<42:
  assert aliases[row['legacy_index']].get(ns+'name')=='dev.fourwags.sumi.inputmethod.latin.Icon'+str(row['legacy_index']);assert aliases[row['legacy_index']].get(ns+'icon')=='@drawable/petal_icon_'+str(row['legacy_index'])
 if row['legacy_index']>=42:continue
 icon=E.parse(res/f"drawable/petal_icon_{row['legacy_index']}.xml");paths={n.get(ns+'name'):n for n in icon.getroot()};assert set(paths)=={'sumi_background','sumi_ears','sumi_key_body','sumi_mark'}
 assert paths['sumi_background'].get(ns+'fillColor')==k['surface'];assert paths['sumi_key_body'].get(ns+'fillColor')==k['key'];assert paths['sumi_ears'].get(ns+'fillColor')==k['function'];assert paths['sumi_mark'].get(ns+'fillColor')==k['ink'];assert paths['sumi_key_body'].get(ns+'strokeColor')==k['stripInk']

for name in ['row_symbols4.xml','kbd_petal_large_symbols_six.xml','kbd_petal_large_symbols_nine.xml']:
 text=(res/'xml'/name).read_text();assert 'keySpec="_"' in text and '¢' not in text
print(f'PASS: 21 linked palettes/styles; 42 stable legacy launcher aliases; minimum label/icon contrast {minimum:.2f}:1; underscore in all primary layouts.')

assert len({r['name'] for r in rows})==21
for role,fill,outline,shadow in [('normal','sumiKey','sumiKeyOutline','sumiKeyShadow'),('function','petalFunction','petalFunctionOutline','petalFunctionShadow')]:
 layers=E.parse(res/('drawable/sumi_key_'+role+'.xml')).getroot();assert layers.tag=='layer-list' and len(layers)==2
 assert layers[1].get(ns+'bottom')=='1dp'
 face=layers[1].find('shape');assert face.find('corners').get(ns+'radius')=='8dp'
 assert face.find('solid').get(ns+'color')=='?attr/'+fill
 assert face.find('stroke').get(ns+'width')=='0.65dp' and face.find('stroke').get(ns+'color')=='?attr/'+outline
 assert layers[0].find('shape/solid').get(ns+'color')=='?attr/'+shadow

# Normal, pressed, latched Shift and action keys use these two native surfaces.
for filename, expected in [('sumi_key.xml', ['@drawable/sumi_key_function'] * 3 + ['@drawable/sumi_key_normal']),
                           ('petal_function.xml', ['@drawable/sumi_key_normal', '@drawable/sumi_key_function'])]:
 actual=[item.get(ns+'drawable') for item in E.parse(res/'drawable'/filename).getroot()]
 assert actual==expected, (filename, actual)
for row in rows:
 k=row['keyboard']
 for background in ['key','function']:
  for foreground in ['ink','hint']:
   assert contrast(k[foreground],k[background])>=4.5, (row['name'],foreground,background)
 assert k['key']!=k['function'], row['name']
assert len({tuple(r['keyboard'][k] for k in ['surface','key','function']) for r in rows})==21
print('PASS: normal, pressed, active and action-key label/hint contrast; 21 unique palette triples.')

app=manifest.find('application');assert app.get(ns+'icon')=='@mipmap/fw_sumi_c' and app.get(ns+'roundIcon')=='@mipmap/fw_sumi_c'
assert not any(a.get(ns+'enabled')=='true' for a in aliases)
assert any(a.get(ns+'name')=='${applicationId}.FourWagsIcon_c' and a.get(ns+'enabled')=='true' for a in all_aliases)
print('PASS: Sumi application/round icon and fresh-install launcher use approved Sumi artwork.')

# Preview foreground/background are paired independently of keycap ink.
preview_ratios=[]
for row in rows:
 style=styles['PetalTheme.'+row['id'].title()]
 surface,ink=style['sumiPreviewSurface'],style['sumiPreviewInk']
 assert luminance(surface)<=.12, (row['name'],surface)
 preview_ratios.append(contrast(ink,surface))
assert min(preview_ratios)>=7
for name in ['SumiKeyboardView','PetalMainKeyboardView']:
 assert styles[name]['keyPreviewTextColor']=='?attr/sumiPreviewInk'
 assert styles[name]['keyPreviewBackground']=='@drawable/sumi_key_preview'
print(f'PASS: all 21 dark popup/ivory letter pairs; minimum contrast {min(preview_ratios):.2f}:1.')

assert styles['PetalMainKeyboardView']['keyPreviewHeight']=='@dimen/sumi_key_preview_height'
base={i.get('name'):i.text for i in E.parse(res/'values/petal_themes.xml').getroot().find("style[@name='PetalTheme']")}
for attr in ['moreKeysKeyboardViewStyle','moreKeysKeyboardViewForActionStyle']:assert base[attr]=='@style/SumiMoreKeysView'
for attr in ['keyTextColor','functionalTextColor']:assert styles['SumiMoreKeysView'][attr]=='?attr/sumiPreviewInk'
assert styles['SumiMoreKeysView']['android:background']=='@drawable/sumi_popup_panel'
print('PASS: keypress height and both held-key panel styles explicitly use Sumi resources.')
