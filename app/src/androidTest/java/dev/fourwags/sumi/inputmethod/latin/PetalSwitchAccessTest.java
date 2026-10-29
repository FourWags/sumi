package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import dev.fourwags.sumi.inputmethod.keyboard.*;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;

/** Hardware-switch end-to-end fixture. The external driver sends only Next/Select key events. */
public class PetalSwitchAccessTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private PetalTestActivity activity; private LatinIME ime; private UiAutomation automation;
    private File stateFile; private final JSONArray completed=new JSONArray();
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void shell(String cmd)throws Exception{try(android.os.ParcelFileDescriptor fd=automation.executeShellCommand(cmd);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[512];while(in.read(b)!=-1){}}}
    private String text(){final String[] s={null};main(()->s[0]=activity.editor.getText().toString().toLowerCase(java.util.Locale.US));return s[0];}
    private void putTarget(JSONArray items,View view,String label,int code)throws Exception{
        android.graphics.Rect visible=new android.graphics.Rect();android.graphics.Point origin=new android.graphics.Point();if(!view.isShown()||!view.getGlobalVisibleRect(visible,origin))return;int[] xy=new int[2];view.getLocationOnScreen(xy);visible.offset(xy[0]-origin.x,xy[1]-origin.y);
        items.put(new JSONObject().put("label",label).put("code",code).put("bounds",new JSONArray(new int[]{visible.left,visible.top,visible.right,visible.bottom})));
    }
    private void viewTargets(JSONArray items,View view)throws Exception{
        if(!view.isShown())return;
        if(view.isClickable()&&view.isEnabled()) {String label=view.getContentDescription()!=null?view.getContentDescription().toString():view instanceof android.widget.TextView?((android.widget.TextView)view).getText().toString():"";if(!label.isEmpty())putTarget(items,view,label,view.getId()==R.id.emoji_keyboard_delete?-5:view.getId()==R.id.emoji_keyboard_alphabet_left?-15:view.getId()==R.id.emoji_keyboard_space?32:0);}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)viewTargets(items,((ViewGroup)view).getChildAt(i));
    }
    private PetalToolsPopup popup()throws Exception{Field f=LatinIME.class.getDeclaredField("petalToolsPopup");f.setAccessible(true);return (PetalToolsPopup)f.get(ime);}
    private android.widget.PopupWindow window()throws Exception{PetalToolsPopup p=popup();if(p==null)return null;Field f=PetalToolsPopup.class.getDeclaredField("active");f.setAccessible(true);return (android.widget.PopupWindow)f.get(p);}
    private android.widget.PopupWindow voiceWindow()throws Exception {
        Field active=LatinIME.class.getDeclaredField("petalVoice");active.setAccessible(true);Object voice=active.get(ime);if(voice==null)return null;
        Field popup=PetalVoiceInput.class.getDeclaredField("popup");popup.setAccessible(true);return (android.widget.PopupWindow)popup.get(voice);
    }
    private boolean voiceShowing(){try{return voiceWindow()!=null&&voiceWindow().isShowing();}catch(Exception e){throw new RuntimeException(e);}}
    private void dumpNodes(AccessibilityNodeInfo node,JSONArray nodes,int depth)throws Exception {
        if(node==null||depth>16||nodes.length()>250)return;
        android.graphics.Rect b=new android.graphics.Rect();node.getBoundsInScreen(b);
        nodes.put(new JSONObject().put("class",String.valueOf(node.getClassName())).put("text",String.valueOf(node.getText()))
            .put("description",String.valueOf(node.getContentDescription())).put("children",node.getChildCount())
            .put("visible",node.isVisibleToUser()).put("bounds",new JSONArray(new int[]{b.left,b.top,b.right,b.bottom})));
        for(int n=0;n<node.getChildCount();n++)dumpNodes(node.getChild(n),nodes,depth+1);
    }
    private boolean standardChoiceVisible(AccessibilityNodeInfo node,JSONArray targets)throws Exception {
        if(node==null)return false;boolean found=false;
        if(node.isVisibleToUser()&&node.getText()!=null&&node.getText().toString().startsWith("Standard QWERTY")){
            android.graphics.Rect b=new android.graphics.Rect();node.getBoundsInScreen(b);
            if(targets!=null)targets.put(new JSONObject().put("label","Standard QWERTY").put("code",0).put("bounds",new JSONArray(new int[]{b.left,b.top,b.right,b.bottom})));found=true;
        }
        for(int i=0;i<node.getChildCount();i++)found=standardChoiceVisible(node.getChild(i),targets)||found;return found;
    }
    private boolean standardChoiceVisible(){try{for(AccessibilityWindowInfo window:automation.getWindows())if(standardChoiceVisible(window.getRoot(),null))return true;return false;}catch(Exception e){throw new RuntimeException(e);}}
    private boolean treeSaved;
    private void snapshot(String phase)throws Exception{
        final JSONObject[] output={null};main(()->{try{
            JSONArray targets=new JSONArray();MainKeyboardView keyboard=KeyboardSwitcher.getInstance().getMainKeyboardView();
            if(keyboard!=null&&keyboard.isShown()) {int[] xy=new int[2];keyboard.getLocationOnScreen(xy);for(Key key:keyboard.getKeyboard().getSortedKeys())if(key.isEnabled()&&!key.isSpacer()){
                android.graphics.Rect b=key.getHitBox();targets.put(new JSONObject().put("label",String.valueOf(key.getCode())).put("code",key.getCode()).put("bounds",new JSONArray(new int[]{b.left+xy[0],b.top+xy[1],b.right+xy[0],b.bottom+xy[1]})));}}
            android.view.View decor=ime.getWindow().getWindow().getDecorView();
            androidx.recyclerview.widget.RecyclerView list=decor.findViewById(R.id.emoji_keyboard_list);
            if(list!=null&&list.isShown()&&list.getChildCount()>0) {
                KeyboardView page=(KeyboardView)list.getChildAt(0);int[] xy=new int[2];page.getLocationOnScreen(xy);
                android.graphics.Rect clip=new android.graphics.Rect();list.getLocalVisibleRect(clip);int[] listXY=new int[2];list.getLocationOnScreen(listXY);clip.offset(listXY[0],listXY[1]);
                for(Key key:page.getKeyboard().getSortedKeys())if(key.isEnabled()&&!key.isSpacer()
                    &&clip.contains(key.getHitBox().centerX()+xy[0],key.getHitBox().centerY()+xy[1])) {
                    android.graphics.Rect b=key.getHitBox();targets.put(new JSONObject().put("label","emoji-first").put("code",key.getCode()).put("bounds",new JSONArray(new int[]{b.left+xy[0],b.top+xy[1],b.right+xy[0],b.bottom+xy[1]})));break;
                }
            }
            viewTargets(targets,decor);
            for(android.app.Activity resumed:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                if(resumed instanceof PetalSettingsActivity)viewTargets(targets,resumed.getWindow().getDecorView());android.widget.PopupWindow popup=window();if(popup!=null&&popup.isShowing())viewTargets(targets,popup.getContentView());android.widget.PopupWindow voice=voiceWindow();if(voice!=null&&voice.isShowing())viewTargets(targets,voice.getContentView());
            output[0]=new JSONObject().put("phase",phase).put("text",activity.editor.getText().toString()).put("targets",targets).put("completed",completed).put("updated",SystemClock.uptimeMillis());
        }catch(Exception e){throw new RuntimeException(e);}});
        for(AccessibilityWindowInfo win:automation.getWindows()) {
            AccessibilityNodeInfo root=win.getRoot();if(root==null||root.getPackageName()==null||!activity.getPackageName().contentEquals(root.getPackageName()))continue;
            if(phase.equals("emoji-insert")&&!treeSaved){
                JSONArray nodes=new JSONArray();dumpNodes(root,nodes,0);
                try(FileWriter out=new FileWriter(new File(stateFile.getParentFile(),"emoji-service-tree.json"))){out.write(nodes.toString(2));}
                treeSaved=true;
            }
            if(phase.equals("settings-standard"))standardChoiceVisible(root,output[0].getJSONArray("targets"));
            AccessibilityNodeInfo focused=root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if(focused!=null) {
                output[0].put("focusedDescription",String.valueOf(focused.getContentDescription()));
                android.graphics.Rect b=new android.graphics.Rect();focused.getBoundsInScreen(b);
                output[0].put("focusedBounds",new JSONArray(new int[]{b.left,b.top,b.right,b.bottom}));
            }
        }
        File temporary=new File(stateFile.getParentFile(),"switch-state.tmp");try(FileWriter out=new FileWriter(temporary)){out.write(output[0].toString());}assertTrue(temporary.renameTo(stateFile));
    }
    private void await(String phase,java.util.function.BooleanSupplier condition)throws Exception{
        long deadline=SystemClock.uptimeMillis()+90000;
        while(SystemClock.uptimeMillis()<deadline){snapshot(phase);if(condition.getAsBoolean()){completed.put(phase);return;}SystemClock.sleep(100);}
        fail("Assistive service timed out at "+phase+"; text="+text());
    }
    private boolean emojiKeysReady() {
        final boolean[] ready={false};main(()->{
            androidx.recyclerview.widget.RecyclerView list=ime.getWindow().getWindow().getDecorView().findViewById(R.id.emoji_keyboard_list);
            if(list!=null&&list.isShown()&&list.getChildCount()>0) {
                KeyboardView page=(KeyboardView)list.getChildAt(0);
                ready[0]=page.getKeyboard()!=null&&!page.getKeyboard().getSortedKeys().isEmpty();
            }
        });return ready[0];
    }
    private View labelled(View root,String label) {
        if(root instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)root).getText()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=labelled(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;
    }
    private void resetField(int type,boolean acceptsGif) {
        main(()->{activity.acceptsGif=acceptsGif;activity.receivedMime=null;activity.editor.setInputType(type);activity.editor.setText("");activity.editor.requestFocus();
            ((android.view.inputmethod.InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).restartInput(activity.editor);ime.loadKeyboard();
        });SystemClock.sleep(500);
    }
    @Test public void talkBackVoiceIconAndStandardThemes()throws Exception{voiceThemes("talkback");}
    @Test public void hardwareSwitchVoiceIconAndStandardThemes()throws Exception{voiceThemes("switchaccess");}
    private void voiceThemes(String serviceName)throws Exception {
        assertTrue("Owned synthetic emulator only",android.os.Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT).contains("generic")||android.os.Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk"));
        automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        activity=rule.getActivity();stateFile=new File(activity.getExternalFilesDir(null),"switch-state.json");stateFile.delete();
        AccessibilityManager manager=(AccessibilityManager)activity.getSystemService(activity.ACCESSIBILITY_SERVICE);boolean connected=false;
        for(AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))if(service.getId().contains(serviceName))connected=true;
        assertTrue("Actual assistive service connected",connected);if(serviceName.equals("talkback"))assertTrue(manager.isTouchExplorationEnabled());
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());int[] aliases=aliasStates();
        shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        main(()->{activity.editor.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,0);});
        for(int n=0;n<100;n++){Field f=KeyboardSwitcher.class.getDeclaredField("mLatinIME");f.setAccessible(true);ime=(LatinIME)f.get(KeyboardSwitcher.getInstance());if(ime!=null&&ime.getCurrentInputConnection()!=null)break;SystemClock.sleep(100);}assertNotNull(ime);
        Field active=LatinIME.class.getDeclaredField("petalVoice");active.setAccessible(true);
        try {
            for(int theme:new int[]{16,17})for(boolean large:new boolean[]{false,true}) {
                final String prefix=(theme==16?"light":"dark")+(large?"-large-":"-regular-");
                main(()->{prefs.edit().putInt("petal_theme",PetalThemeCatalog.LEGACY_INDICES[theme]).putBoolean("petal_large_keys",large).putBoolean("petal_literal",false).putBoolean("petal_personal_dictionary",false).apply();});
                resetField(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,false);SystemClock.sleep(500);
                assertEquals(theme,PetalThemeCatalog.selected(prefs));
                main(()->PetalAccessibilityChecks.checkQwerty(ime.getWindow().getWindow().getDecorView().findViewById(R.id.main_keyboard_frame),prefix+"live"));
                class Fake implements PetalVoiceInput.Factory,PetalVoiceInput.Engine {
                    volatile int starts,stops,closes;android.speech.RecognitionListener listener;
                    public boolean available(android.content.Context c){return true;}public boolean permitted(android.content.Context c){return true;}
                    public PetalVoiceInput.Engine create(android.content.Context c){return this;}
                    public void start(android.content.Intent request,android.speech.RecognitionListener value){assertTrue(request.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,false));assertEquals("en-US",request.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE));listener=value;starts++;listener.onReadyForSpeech(new android.os.Bundle());listener.onBeginningOfSpeech();listener.onRmsChanged(8);}
                    public void stop(){stops++;android.os.Bundle result=new android.os.Bundle();result.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("synthetic voice")));listener.onResults(result);}
                    public void close(){closes++;}
                }
                Fake fake=new Fake();PetalVoiceInput voice=new PetalVoiceInput(ime,fake);Object original=active.get(ime);
                main(()->{try{active.set(ime,voice);}catch(Exception e){throw new RuntimeException(e);}});
                try {
                    await(prefix+"q",()->text().equals("q"));await(prefix+"delete",()->text().isEmpty());
                    await(prefix+"voice-start",()->fake.starts==1&&voiceShowing());
                    main(()->{try{PetalAccessibilityChecks.check(voiceWindow().getContentView(),prefix+"voice-panel");Field status=PetalVoiceInput.class.getDeclaredField("status");status.setAccessible(true);assertEquals(activity.getString(R.string.petal_voice_speech),((android.widget.TextView)status.get(voice)).getText().toString());}catch(Exception e){throw new RuntimeException(e);}});
                    await(prefix+"voice-stop",()->fake.stops==1&&text().equals("synthetic voice")&&!voiceShowing());
                    await(prefix+"voice-again",()->fake.starts==2&&voiceShowing());
                    await(prefix+"voice-cancel",()->fake.closes==2&&!voiceShowing());
                    assertEquals(1,fake.stops);assertEquals("synthetic voice",text());
                }finally{main(()->{voice.cancel();try{active.set(ime,original);}catch(Exception e){throw new RuntimeException(e);}});}
            }
            snapshot("PASS");assertEquals(24,completed.length());
        }finally{main(()->{restorePreferences(prefs,before);restoreAliases(aliases);});}
    }
    private int[] aliasStates(){int[] result=new int[PetalThemeCatalog.ALIAS_COUNT];for(int i=0;i<result.length;i++)result[i]=activity.getPackageManager().getComponentEnabledSetting(new android.content.ComponentName(activity,activity.getPackageName()+".Icon"+i));return result;}
    private void restoreAliases(int[] states){for(int i=0;i<states.length;i++)activity.getPackageManager().setComponentEnabledSetting(new android.content.ComponentName(activity,activity.getPackageName()+".Icon"+i),states[i],android.content.pm.PackageManager.DONT_KILL_APP);assertArrayEquals(states,aliasStates());}
    @SuppressWarnings("unchecked") private void restorePreferences(android.content.SharedPreferences prefs,java.util.Map<String,?> before) {
        android.content.SharedPreferences.Editor edit=prefs.edit().clear();
        for(java.util.Map.Entry<String,?> entry:before.entrySet()) {
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof java.util.Set)edit.putStringSet(key,new java.util.HashSet<>((java.util.Set<String>)value));else throw new AssertionError("Unsupported synthetic preference type");
        }edit.commit();assertEquals(before,prefs.getAll());
    }
    @Test public void hardwareSwitchKeyboardAndTools()throws Exception{workflow("switchaccess");}
    @Test public void talkBackTouchKeyboardAndTools()throws Exception{workflow("talkback");}
    private void workflow(String serviceName)throws Exception{workflow(serviceName,false);}
    private void workflow(String serviceName,boolean mediaOnly)throws Exception{
        assertTrue("Clipboard fixture is restricted to owned emulators",android.os.Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT).contains("generic")||android.os.Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk"));
        automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);activity=rule.getActivity();stateFile=new File(activity.getExternalFilesDir(null),"switch-state.json");stateFile.delete();
        AccessibilityManager manager=(AccessibilityManager)activity.getSystemService(activity.ACCESSIBILITY_SERVICE);boolean connected=false;
        for(AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))if(service.getId().contains(serviceName))connected=true;
        assertTrue("Actual "+serviceName+" service must be connected",connected);
        if(serviceName.equals("talkback"))assertTrue(manager.isTouchExplorationEnabled());
        android.content.SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());int[] aliases=aliasStates();
        shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        main(()->{activity.editor.setText("");activity.editor.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,0);});
        for(int n=0;n<100;n++){Field f=KeyboardSwitcher.class.getDeclaredField("mLatinIME");f.setAccessible(true);ime=(LatinIME)f.get(KeyboardSwitcher.getInstance());if(ime!=null&&ime.getCurrentInputConnection()!=null)break;SystemClock.sleep(100);}assertNotNull(ime);main(()->ime.loadKeyboard());SystemClock.sleep(700);
        activity.getSystemService(android.content.ClipboardManager.class).clearPrimaryClip();
        try{
            main(()->{prefs.edit().putBoolean("petal_large_keys",true).putBoolean("petal_literal",false).putBoolean("petal_personal_dictionary",false).putBoolean("pref_key_auto_correction",true).putInt("petal_theme",PetalThemeCatalog.LEGACY_INDICES[serviceName.equals("talkback")?16:17]).apply();ime.loadKeyboard();});SystemClock.sleep(700);
            int normal=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
            if(!mediaOnly){
            await("q",()->text().equals("q"));await("space",()->text().equals("q "));await("delete-space",()->text().equals("q"));await("delete-q",()->text().isEmpty());
            await("symbols",()->!KeyboardSwitcher.getInstance().getKeyboard().mId.isAlphabetKeyboard());await("underscore",()->text().equals("_"));await("alphabet",()->KeyboardSwitcher.getInstance().getKeyboard().mId.isAlphabetKeyboard());
            await("tools",()->{try{return window()!=null&&window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            await("close-tools",()->{try{return window()==null||!window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            resetField(normal,false);
            await("type-t",()->text().equals("t"));await("type-e",()->text().equals("te"));await("type-h",()->text().equals("teh"));await("correct-space",()->text().equals("the "));await("backspace-undo",()->text().equals("teh"));
            resetField(normal,false);
            await("again-t",()->text().equals("t"));await("again-e",()->text().equals("te"));await("again-h",()->text().equals("teh"));await("again-correct-space",()->text().equals("the "));await("visible-revert",()->text().equals("teh"));
            resetField(0x81,false);assertFalse(ime.petalMediaAllowed());
            await("password-t",()->text().equals("t"));await("password-e",()->text().equals("te"));await("password-h",()->text().equals("teh"));await("password-space",()->text().equals("teh "));
            }
            resetField(normal,false);
            main(()->{activity.editor.setText("synthetic tools");activity.editor.setSelection(activity.editor.length());});
            await("tools-edit",()->{try{return window()!=null&&window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            await("select-all",()->activity.editor.getSelectionStart()==0&&activity.editor.getSelectionEnd()==activity.editor.length());
            await("tools-copy",()->{try{return window()!=null&&window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            await("copy",()->{android.content.ClipboardManager c=activity.getSystemService(android.content.ClipboardManager.class);android.content.ClipData data=c.getPrimaryClip();return data!=null&&"synthetic tools".contentEquals(data.getItemAt(0).getText());});
            resetField(normal,false);
            await("tools-clipboard",()->{try{return window()!=null&&window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            await("clipboard-open",()->{try{return labelled(window().getContentView(),"Current system clipboard only. Nothing is saved by Sumi.")!=null;}catch(Exception e){throw new RuntimeException(e);}});
            await("clipboard-paste",()->text().equals("synthetic tools"));
            await("tools-editing",()->{try{return window()!=null&&window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            await("editing-open",()->{try{return labelled(window().getContentView(),"Cursor left")!=null;}catch(Exception e){throw new RuntimeException(e);}});
            await("editing-left",()->activity.editor.getSelectionStart()==activity.editor.length()-1);
            await("editing-select",()->Math.abs(activity.editor.getSelectionEnd()-activity.editor.getSelectionStart())==1);
            await("editing-back",()->{try{return labelled(window().getContentView(),"Settings")!=null;}catch(Exception e){throw new RuntimeException(e);}});
            await("tools-settings",()->{try{return window()!=null&&window().isShowing();}catch(Exception e){throw new RuntimeException(e);}});
            await("settings-open",()->{final boolean[] found={false};main(()->{for(android.app.Activity resumed:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(resumed instanceof PetalSettingsActivity)found[0]=true;});return found[0];});
            await("settings-layout",this::standardChoiceVisible);
            await("settings-standard",()->!DeviceProtectedUtils.getSharedPreferences(activity).getBoolean("petal_large_keys",false));
            snapshot("PASS");assertEquals(39,completed.length());
        }finally{main(()->{activity.getSystemService(android.content.ClipboardManager.class).clearPrimaryClip();restorePreferences(prefs,before);restoreAliases(aliases);});}
    }
}
