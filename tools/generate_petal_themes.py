#!/usr/bin/env python3
"""Render native palettes and matching sprout vectors from the approved offline color map."""
from pathlib import Path
import argparse,json,re,xml.etree.ElementTree as ET
p=Path(__file__).resolve().parents[1];res=p/'app/src/main/res';data=json.loads((p/'tools/petal_wake_palettes.json').read_text());check=argparse.ArgumentParser();check.add_argument('--check',action='store_true');args=check.parse_args()
xml=(res/'values/petal_themes.xml').read_text();start=xml.index('<style name="PetalTheme.Rose"');end=xml.index('<style name="SumiKeyboardView"');styles=[]
for r in data['palettes']:
 styles.append('<style name="PetalTheme.'+r['id'].title()+'" parent="PetalTheme">'+''.join('<item name="'+('sumi' if k.startswith('key') else 'petal')+k[0].upper()+k[1:]+'">'+v+'</item>' for k,v in r['keyboard'].items())+'</style>')
outputs={res/'values/petal_themes.xml':xml[:start]+'\n'.join(styles)+'\n'+xml[end:]}
family=json.loads((p/'tools/petal_family_palettes.json').read_text()); byid={r['id']:r for r in data['palettes']}
sumi_ns='{http://schemas.android.com/apk/res/android}'
sumi_paths=[node.get(sumi_ns+'pathData') for node in ET.parse(res/'drawable/fw_sumi_c_foreground.xml').getroot()]
assert len(sumi_paths)==3
for i,legacy in enumerate(family['legacy_ids']):
 r=byid[family['retired_id_fallbacks'].get(legacy,legacy)]
 k=r['keyboard'];vector=f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:name="sumi_background" android:fillColor="{k['surface']}" android:pathData="M24,0 L84,0 Q108,0 108,24 L108,84 Q108,108 84,108 L24,108 Q0,108 0,84 L0,24 Q0,0 24,0"/>
<path android:name="sumi_ears" android:pathData="{sumi_paths[0]}" android:fillColor="{k['function']}" android:strokeColor="{k['stripInk']}" android:strokeWidth="1.4"/>
<path android:name="sumi_key_body" android:pathData="{sumi_paths[1]}" android:fillColor="{k['key']}" android:strokeColor="{k['stripInk']}" android:strokeWidth="1.4"/>
<path android:name="sumi_mark" android:pathData="{sumi_paths[2]}" android:fillColor="{k['ink']}"/>
</vector>
'''
 outputs[res/f'drawable/petal_icon_{i}.xml']=vector
 if i==0:outputs[res/'drawable/petal_icon_preview.xml']=vector
# The same stable order drives Settings, keyboard styles and launcher aliases.
rows=data['palettes'];names=', '.join(json.dumps(r['name']) for r in rows)
ids=', '.join(json.dumps(r['id']) for r in rows)
styles=', '.join('R.style.PetalTheme_'+r['id'].title() for r in rows)
icons=', '.join('R.drawable.petal_icon_'+str(r['legacy_index']) if r['legacy_index']<42 else 'R.mipmap.fw_sumi_'+chr(97+r['legacy_index']-42) for r in rows)
legacy=', '.join(str(r['legacy_index']) for r in rows)
lookup=', '.join(str(next(i for i,r in enumerate(rows) if r['id']==family['retired_id_fallbacks'].get(id,id))) for id in family['legacy_ids'])+(', '+', '.join(str(i) for i in range(18,len(rows))) if len(rows)>18 else '')
outputs[p/'app/src/main/java/dev/fourwags/sumi/inputmethod/latin/PetalThemeCatalog.java']='package dev.fourwags.sumi.inputmethod.latin;\n\nimport dev.fourwags.sumi.R;\n\n/** Generated from the approved offline palette map; regenerate with tools/generate_petal_themes.py. */\npublic final class PetalThemeCatalog {\n    private PetalThemeCatalog() {}\n    public static final String[] IDS = {'+ids+'};\n    public static final String[] NAMES = {'+names+'};\n    public static final int[] STYLES = {'+styles+'};\n    public static final int[] ICONS = {'+icons+'};\n    public static final int[] LEGACY_INDICES = {'+legacy+'};\n    private static final int[] LEGACY_TO_SELECTION = {'+lookup+'};\n    public static final int ALIAS_COUNT = '+str(len(family['legacy_ids']))+';\n    public static int indexForLegacy(int value) { return value>=0 && value<LEGACY_TO_SELECTION.length ? LEGACY_TO_SELECTION[value] : 0; }\n    public static int selected(android.content.SharedPreferences prefs) { return prefs.contains("petal_theme") ? indexForLegacy(prefs.getInt("petal_theme",0)) : prefs.contains("petal_icon") ? indexForLegacy(prefs.getInt("petal_icon",0)) : 19; }\n    public static final String SHIBA_RESET_MARKER = "petal_shiba_theme_reset_version";\n    public static boolean migrateSelection(android.content.SharedPreferences prefs) { return prefs.getInt(SHIBA_RESET_MARKER,0)>=1 || prefs.edit().putInt("petal_theme",43).putInt(SHIBA_RESET_MARKER,1).commit(); }\n    public static void ensureSelection(android.content.Context context) { android.content.SharedPreferences prefs=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(context); if(!migrateSelection(prefs))return; int index=selected(prefs), legacy=LEGACY_INDICES[index]; if(prefs.getInt("petal_theme",0)!=legacy || (legacy<ALIAS_COUNT && prefs.getInt("petal_icon",0)!=legacy)) PetalSettingsActivity.selectTheme(context,index); }\n}\n'
# Component registration is deliberately maintained in the manifest, not regenerated here.
for role,fill,outline,shadow in [('normal','sumiKey','sumiKeyOutline','sumiKeyShadow'),('function','petalFunction','petalFunctionOutline','petalFunctionShadow')]:
 outputs[res/('drawable/sumi_key_'+role+'.xml')]='<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n<item><shape android:shape="rectangle"><solid android:color="?attr/'+shadow+'"/><corners android:radius="8dp"/></shape></item>\n<item android:bottom="1dp"><shape android:shape="rectangle"><solid android:color="?attr/'+fill+'"/><stroke android:width="0.65dp" android:color="?attr/'+outline+'"/><corners android:radius="8dp"/></shape></item>\n</layer-list>\n'
for path,text in outputs.items():
 if args.check:assert path.read_text()==text,'Generated theme resource differs: '+str(path)
 else:path.write_text(text)
print(('Verified' if args.check else 'Generated')+f': {len(rows)} native palettes and defined keycaps; 42 existing launcher aliases preserved.')
