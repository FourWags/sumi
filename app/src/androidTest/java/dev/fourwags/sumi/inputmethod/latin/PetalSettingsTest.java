package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.view.*;
import android.widget.*;
public class PetalSettingsTest {
    @Rule public ActivityTestRule<PetalSettingsActivity> rule=new ActivityTestRule<>(PetalSettingsActivity.class);
    private java.util.Map<String,?> savedPreferences;
    private java.util.Map<ComponentName,Integer> savedComponents;
    @Before public void preserveStateAndCompleteFirstRun() {
        PetalSettingsActivity a=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            savedPreferences=new java.util.HashMap<>(dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a).getAll());
            savedComponents=new java.util.HashMap<>();
            for(int i=0;i<PetalThemeCatalog.ALIAS_COUNT;i++){ComponentName c=new ComponentName(a,a.getPackageName()+".Icon"+i);savedComponents.put(c,a.getPackageManager().getComponentEnabledSetting(c));}
            for(String id:new String[]{"a","b","c"}){ComponentName c=new ComponentName(a,a.getPackageName()+".FourWagsIcon_"+id);savedComponents.put(c,a.getPackageManager().getComponentEnabledSetting(c));}
            for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){View later=find(root,"Later");if(later!=null)later.performClick();}
            FourWagsKeyIcon.skip(a);
        });
    }
    @After public void restoreState() {
        if(savedPreferences==null)return;
        PetalSettingsActivity a=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.content.SharedPreferences.Editor editor=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a).edit().clear();
            for(java.util.Map.Entry<String,?> entry:savedPreferences.entrySet()){
                String k=entry.getKey();Object v=entry.getValue();
                if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);else if(v instanceof Integer)editor.putInt(k,(Integer)v);else if(v instanceof Long)editor.putLong(k,(Long)v);else if(v instanceof Float)editor.putFloat(k,(Float)v);else if(v instanceof String)editor.putString(k,(String)v);else if(v instanceof java.util.Set)editor.putStringSet(k,new java.util.HashSet<>((java.util.Set<String>)v));
            }
            assertTrue(editor.commit());
            for(java.util.Map.Entry<ComponentName,Integer> entry:savedComponents.entrySet())if(a.getPackageManager().getComponentEnabledSetting(entry.getKey())!=entry.getValue())a.getPackageManager().setComponentEnabledSetting(entry.getKey(),entry.getValue(),PackageManager.DONT_KILL_APP);
        });
    }
    private View find(View root,String text){
        if(root instanceof Button && ((Button)root).getText().toString().equals(text))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=find(((ViewGroup)root).getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    @Test public void keypressPreviewIsControllableAtEveryScreenSize() {
        PetalSettingsActivity a=rule.getActivity();
        android.content.SharedPreferences p=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a);
        String key=dev.fourwags.sumi.inputmethod.latin.settings.Settings.PREF_POPUP_ON;
        Switch toggle=(Switch)find(a.getWindow().getDecorView(),"Popup letters on keypress");
        assertNotNull("Keypress previews need a visible recovery control",toggle);
        for(int width:new int[]{360,600,800}) {
            android.content.res.Configuration config=new android.content.res.Configuration(a.getResources().getConfiguration());
            config.smallestScreenWidthDp=width;config.screenWidthDp=width;
            android.content.res.Resources resources=a.createConfigurationContext(config).getResources();
            for(boolean enabled:new boolean[]{false,true}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->toggle.setChecked(enabled));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals("Preview preference ignored at width "+width,enabled,
                    dev.fourwags.sumi.inputmethod.latin.settings.Settings.readKeyPreviewPopupEnabled(p,resources));
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(a::recreate);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(p.getBoolean(key,false));
    }
    @Test public void longPressAlternativesAccessibleAndPersistent() {
        PetalSettingsActivity a=rule.getActivity();android.content.SharedPreferences p=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a);
        String key=dev.fourwags.sumi.inputmethod.latin.settings.SettingsValues.PREF_LONG_PRESS_ALTERNATIVES;
        java.util.Map<String,?> before=new java.util.HashMap<>(p.getAll());
        Switch toggle=(Switch)find(a.getWindow().getDecorView(),"Long-press alternatives");assertNotNull(toggle);
        assertTrue(toggle.isEnabled());assertTrue(toggle.isClickable());assertEquals(p.getBoolean(key,false),toggle.isChecked());
        for(boolean value:new boolean[]{true,false}) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->toggle.setChecked(value));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(value,p.getBoolean(key,!value));
            java.util.Map<String,?> after=new java.util.HashMap<>(p.getAll());after.remove(key);java.util.Map<String,?> expected=new java.util.HashMap<>(before);expected.remove(key);assertEquals(expected,after);
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(a::recreate);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse(p.getBoolean(key,true));
    }
    @Test public void allThemesAndMatchingLauncherIconsCanBeSelected(){
        PetalSettingsActivity activity=rule.getActivity();
        for(int step=0;step<PetalThemeCatalog.IDS.length;step++){
            int selected=(step+1)%PetalThemeCatalog.IDS.length;
            Spinner selector=activity.getWindow().getDecorView().findViewWithTag(PetalSettingsActivity.THEME_SELECTOR);
            assertNotNull(selector);
            final int index=selected;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->selector.setSelection(index));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(PetalThemeCatalog.LEGACY_INDICES[selected],dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(activity).getInt("petal_theme",-1));
            String chosen=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(activity).getString("fourwags_icon_choice",null);
            for(int i=0;i<PetalThemeCatalog.ALIAS_COUNT;i++)assertEquals(chosen==null&&i==PetalThemeCatalog.LEGACY_INDICES[selected]?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,activity.getPackageManager().getComponentEnabledSetting(new ComponentName(activity,activity.getPackageName()+".Icon"+i)));
            java.util.List<android.content.pm.ResolveInfo> launchers=activity.getPackageManager().queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(activity.getPackageName()),0);
            assertEquals(1,launchers.size());
            if(chosen!=null)assertTrue(launchers.get(0).activityInfo.name.endsWith(".FourWagsIcon_"+chosen));
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((Spinner)activity.getWindow().getDecorView().findViewWithTag(PetalSettingsActivity.THEME_SELECTOR)).setSelection(0));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void legacyThemeMigrationPreservesOtherPreferences() {
        PetalSettingsActivity a=rule.getActivity();android.content.SharedPreferences p=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->p.edit().remove("fourwags_icon_choice").putBoolean("fourwags_icon_pending",false).commit());
        for(int old=0;old<PetalThemeCatalog.ALIAS_COUNT;old++){final int value=old;InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.content.pm.PackageManager pm=a.getPackageManager();
            java.util.List<android.content.pm.ResolveInfo> previous=pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(a.getPackageName()),0);
            pm.setComponentEnabledSetting(new ComponentName(a,a.getPackageName()+".Icon"+value),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            for(android.content.pm.ResolveInfo info:previous)if(!info.activityInfo.name.endsWith(".Icon"+value))pm.setComponentEnabledSetting(new ComponentName(a,info.activityInfo.name),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            p.edit().putInt("petal_theme",value).putInt("petal_icon",value).putBoolean("petal_literal",true).putBoolean("petal_large_keys",true).apply();PetalThemeCatalog.ensureSelection(a);
            int index=PetalThemeCatalog.indexForLegacy(value);assertEquals(PetalThemeCatalog.LEGACY_INDICES[index],p.getInt("petal_theme",-1));assertTrue(p.getBoolean("petal_literal",false));assertTrue(p.getBoolean("petal_large_keys",false));
            java.util.List<android.content.pm.ResolveInfo> aliases=a.getPackageManager().queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(a.getPackageName()),0);assertEquals(1,aliases.size());assertTrue(aliases.get(0).activityInfo.name.endsWith(".Icon"+PetalThemeCatalog.LEGACY_INDICES[index]));
        });}
    }
    @Test public void layoutSelectorPersistsBothExistingLayouts() {
        PetalSettingsActivity activity=rule.getActivity();
        android.content.SharedPreferences prefs=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(activity);
        boolean before=prefs.getBoolean("petal_large_keys",false);
        Spinner spinner=activity.getWindow().getDecorView().findViewWithTag(PetalSettingsActivity.LAYOUT_SELECTOR);
        try {
            assertEquals(before?0:1,spinner.getSelectedItemPosition());
            assertEquals(2,spinner.getCount());
            for(int i=0;i<2;i++) {
                final int index=i;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->spinner.setSelection(index));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(i==0,prefs.getBoolean("petal_large_keys",false));
                InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::onResume);
                assertEquals(i,spinner.getSelectedItemPosition());
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->spinner.setSelection(before?0:1));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }
    @Test public void enableControlReflectsSelectedKeyboardOnResume() {
        PetalSettingsActivity activity=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::onResume);
        String current=android.provider.Settings.Secure.getString(activity.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName component=new ComponentName(activity,LatinIME.class);
        boolean active=component.equals(ComponentName.unflattenFromString(String.valueOf(current)));
        boolean enabled=false;
        for(android.view.inputmethod.InputMethodInfo info:activity.getSystemService(android.view.inputmethod.InputMethodManager.class).getEnabledInputMethodList())
            if(component.equals(new ComponentName(info.getPackageName(),info.getServiceName())))enabled=true;
        View button=find(activity.getWindow().getDecorView(),activity.getString(active?R.string.sumi_keyboard_active:enabled?R.string.sumi_keyboard_enabled:R.string.sumi_keyboard_enable));
        assertNotNull(button);assertEquals(!(active||enabled),button.isEnabled());
    }
    @Test public void completedSetupStatesAreIndependentReadableAndDoNotChangePreferences() {
        PetalSettingsActivity a=rule.getActivity();android.content.SharedPreferences prefs=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a);
        java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());int permission=a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO);
        try{
            for(boolean active:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true})for(boolean allowed:new boolean[]{false,true}){
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->a.updateSetupControls(active,enabled,allowed));
                Button keyboard=(Button)find(a.getWindow().getDecorView(),a.getString(active?R.string.sumi_keyboard_active:enabled?R.string.sumi_keyboard_enabled:R.string.sumi_keyboard_enable));
                Button mic=(Button)find(a.getWindow().getDecorView(),a.getString(allowed?R.string.petal_microphone_allowed:R.string.petal_voice_permission_button));
                assertNotNull(keyboard);assertNotNull(mic);assertEquals(!(active||enabled),keyboard.isEnabled());assertEquals(!allowed,mic.isEnabled());
                for(Button control:new Button[]{keyboard,mic}){
                    boolean completed=!control.isEnabled();assertNull("Theme tint must not recolor completed setup controls",control.getBackgroundTintList());android.graphics.drawable.RippleDrawable background=(android.graphics.drawable.RippleDrawable)control.getBackground();
                    int fill=((android.graphics.drawable.GradientDrawable)background.getDrawable(0)).getColor().getDefaultColor();
                    assertEquals(completed?0xffe4e0dd:0xfff1e5e8,fill);
                    assertTrue("Completed controls retain readable text",androidx.core.graphics.ColorUtils.calculateContrast(control.getCurrentTextColor(),fill)>=4.5);
                }
                assertEquals("Rendering status must not grant microphone permission",permission,a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO));
                assertEquals(before,prefs.getAll());
            }
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(a::onResume);}
    }
    @Test public void keyboardThemeSelectionAppearsWhenSettingsResumes() {
        PetalSettingsActivity activity=rule.getActivity();
        android.content.SharedPreferences prefs=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(activity);
        int before=PetalThemeCatalog.selected(prefs);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{PetalSettingsActivity.selectTheme(activity,15);activity.onResume();});
            Spinner selector=activity.getWindow().getDecorView().findViewWithTag(PetalSettingsActivity.THEME_SELECTOR);
            assertEquals(15,selector.getSelectedItemPosition());
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{PetalSettingsActivity.selectTheme(activity,before);activity.onResume();});}
    }
    @Test public void settingsAccessibilityFramework() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->PetalAccessibilityChecks.check(rule.getActivity().getWindow().getDecorView(),"settings"));
    }
    @Test public void settingsAtLargeFontAreScrollableAndControlsReachable() {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        PetalLargeFontSettingsActivity activity=(PetalLargeFontSettingsActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(PetalTestRunner.fixtureIntent("large-font"));
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                assertEquals(2f,activity.getResources().getConfiguration().fontScale,0.01f);
                android.view.View root=activity.getWindow().getDecorView();
                android.widget.ScrollView scroll=(android.widget.ScrollView)((android.view.ViewGroup)root.findViewById(android.R.id.content)).getChildAt(0);
                assertTrue(scroll.getChildAt(0).getHeight()>scroll.getHeight());
                scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
                assertNotNull(root.findViewWithTag(PetalSettingsActivity.THEME_SELECTOR));
                PetalAccessibilityChecks.check(root,"settings-large-font");
            });
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);}
    }
    @Test public void voicePermissionExplanationDoesNotRecordOrGrant() {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        int before=c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO);
        PetalVoicePermissionActivity a=(PetalVoicePermissionActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new android.content.Intent(c,PetalVoicePermissionActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(before,c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                Button control=(Button)find(a.getWindow().getDecorView(),c.getString(before==PackageManager.PERMISSION_GRANTED?R.string.petal_microphone_allowed:R.string.petal_voice_permission_button));assertNotNull(control);assertEquals(before!=PackageManager.PERMISSION_GRANTED,control.isEnabled());
                PetalAccessibilityChecks.check(a.getWindow().getDecorView(),"voice-permission");
            });
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync(a::finish);}
    }

    @Test public void settingsPreviewScreenshot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            PetalSettingsActivity a=rule.getActivity();android.view.View root=a.getWindow().getDecorView();
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
            try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),"settings-refresh.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}
        });
    }


    @Test public void personalShibaButtonChoicePreservesOtherSettings() {
        PetalSettingsActivity a=rule.getActivity();
        android.content.SharedPreferences p=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a);
        java.util.Map<String,?> before=new java.util.HashMap<>(p.getAll());
        Spinner selector=a.getWindow().getDecorView().findViewWithTag("sumi_keyboard_button_selector");
        assertNotNull(selector);assertEquals(3,selector.getCount());
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->p.edit().remove(PetalButtonIcon.PREFERENCE).apply());
            assertEquals(0,PetalButtonIcon.selected(p));
            for(int i=0;i<3;i++) {
                final int value=i;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->selector.setSelection(value));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(value,PetalButtonIcon.selected(p));
                java.util.Map<String,?> after=new java.util.HashMap<>(p.getAll());after.remove(PetalButtonIcon.PREFERENCE);
                java.util.Map<String,?> expected=new java.util.HashMap<>(before);expected.remove(PetalButtonIcon.PREFERENCE);
                assertEquals(expected,after);
                assertNotNull(a.getDrawable(PetalButtonIcon.drawable(value)));
            }
            p.edit().putInt(PetalButtonIcon.PREFERENCE,99).commit();assertEquals(0,PetalButtonIcon.selected(p));
            p.edit().putString(PetalButtonIcon.PREFERENCE,"invalid").commit();assertEquals(0,PetalButtonIcon.selected(p));
        } finally {
            android.content.SharedPreferences.Editor e=p.edit();
            if(before.containsKey(PetalButtonIcon.PREFERENCE))e.putInt(PetalButtonIcon.PREFERENCE,(Integer)before.get(PetalButtonIcon.PREFERENCE));else e.remove(PetalButtonIcon.PREFERENCE);
            e.commit();
        }
    }

    @Test public void dictationAndSuggestionSizesPersistIndependently() {
        PetalSettingsActivity a=rule.getActivity();
        android.content.SharedPreferences p=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(a);
        Spinner silence=a.getWindow().getDecorView().findViewWithTag(PetalVoiceTiming.SILENCE);
        Spinner size=a.getWindow().getDecorView().findViewWithTag(PetalTypingOptions.SUGGESTION_SIZE);
        assertNotNull(silence);assertNotNull(size);
        for(int index=0;index<3;index++) {
            final int n=index;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{silence.setSelection(n);size.setSelection(n);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(new int[]{2000,4000,8000}[n],p.getInt(PetalVoiceTiming.SILENCE,2000));
            assertEquals(new int[]{100,125,150}[n],p.getInt(PetalTypingOptions.SUGGESTION_SIZE,125));
        }
        boolean large=p.getBoolean("petal_large_keys",false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(a::recreate);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(8000,p.getInt(PetalVoiceTiming.SILENCE,0));assertEquals(150,p.getInt(PetalTypingOptions.SUGGESTION_SIZE,0));
        assertEquals(large,p.getBoolean("petal_large_keys",false));
    }
}
