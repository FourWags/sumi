package dev.fourwags.sumi.inputmethod.latin;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.*;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
import dev.fourwags.sumi.R;
import java.lang.reflect.Field;
import java.util.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Phone-safe fixture: own synthetic editor, only dog/size preferences; never clipboard/audio. */
public class PetalPhoneShibaTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private PetalTestActivity activity;
    private LatinIME ime;
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void ownActivity(){
        boolean found=false;
        for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
            if(a==activity)found=true;
        assertTrue("Synthetic editor must remain foreground",found);
    }
    private void editorGuard(){
        ownActivity();EditorInfo info=ime.getCurrentInputEditorInfo();assertNotNull(info);
        assertEquals(activity.getPackageName(),info.packageName);assertEquals(activity.editor.getId(),info.fieldId);
        assertNotNull(ime.getCurrentInputConnection());
    }
    private void ready()throws Exception{
        Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
        for(int n=0;n<100;n++){
            final boolean[] ok={false};final boolean nudge=n%5==0;
            main(()->{
                ownActivity();
                try{ime=(LatinIME)owner.get(KeyboardSwitcher.getInstance());}catch(Exception e){throw new RuntimeException(e);}
                EditorInfo info=ime==null?null:ime.getCurrentInputEditorInfo();
                ok[0]=ime!=null&&ime.isInputViewShown()&&ime.getCurrentInputConnection()!=null&&info!=null
                    &&activity.getPackageName().equals(info.packageName)&&info.fieldId==activity.editor.getId()
                    &&KeyboardSwitcher.getInstance().getMainKeyboardView()!=null;
                if(!ok[0]&&nudge){activity.editor.requestFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,InputMethodManager.SHOW_IMPLICIT);}
            });if(ok[0])return;SystemClock.sleep(100);
        }fail("Synthetic IME readiness timeout");
    }
    private void shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)) {byte[] buffer=new byte[256];while(in.read(buffer)!=-1){}}
    }
    private View label(View root,String text){
        if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=label(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;
    }
    private android.widget.PopupWindow tools()throws Exception{
        Field owner=LatinIME.class.getDeclaredField("petalToolsPopup");owner.setAccessible(true);Object popup=owner.get(ime);assertNotNull(popup);
        Field active=PetalToolsPopup.class.getDeclaredField("active");active.setAccessible(true);return (android.widget.PopupWindow)active.get(popup);
    }
    private void checkImage(ImageButton button,int selected){
        Bitmap actual=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        try{
            Drawable d=button.getDrawable().getConstantState().newDrawable(button.getResources()).mutate();d.setBounds(0,0,64,64);d.draw(new Canvas(actual));
            long[] errors=new long[3];for(int choice=0;choice<3;choice++){
                expected.eraseColor(Color.TRANSPARENT);Drawable e=activity.getDrawable(PetalButtonIcon.drawable(choice));e.setBounds(0,0,64,64);e.draw(new Canvas(expected));
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                    int a=actual.getPixel(x,y),b=expected.getPixel(x,y),difference=0;
                    for(int shift:new int[]{0,8,16,24})difference=Math.max(difference,Math.abs(((a>>>shift)&255)-((b>>>shift)&255)));
                    errors[choice]+=difference;
                }
            }
            assertTrue("Live vector must closely match selected head",errors[selected]<4096L*12);
            for(int choice=0;choice<3;choice++)if(choice!=selected)assertTrue("Head identity must be unique",errors[selected]<errors[choice]);
        }finally{actual.recycle();expected.recycle();}
    }
    @Test public void allHeadsBothSizesThenLeaveBright()throws Exception{
        activity=rule.getActivity();SharedPreferences p=DeviceProtectedUtils.getSharedPreferences(activity);
        Map<String,?> before=new HashMap<>(p.getAll());JSONArray variants=new JSONArray();
        main(()->{ownActivity();activity.options=0;activity.editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);activity.editor.setText("Synthetic Shiba check");activity.editor.requestFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.editor);});
        try{
            main(this::ownActivity);
            shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");ready();
            for(boolean large:new boolean[]{false,true}){
                main(()->{editorGuard();assertTrue(p.edit().putBoolean("petal_large_keys",large).commit());ime.loadKeyboard();});ready();
                for(int choice=0;choice<3;choice++){
                    final int selected=choice;main(()->{editorGuard();PetalButtonIcon.select(p,selected);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    main(()->{
                        editorGuard();assertEquals("Synthetic Shiba check",activity.editor.getText().toString());
                        ImageButton button=ime.getWindow().getWindow().getDecorView().findViewById(R.id.suggestions_strip_other_key);
                        assertTrue(button.isShown());assertTrue(button.isEnabled());assertEquals(activity.getString(R.string.petal_tools_description),button.getContentDescription());
                        assertTrue(button.getWidth()>=Math.round(48*button.getResources().getDisplayMetrics().density));assertTrue(button.getHeight()>=Math.round(48*button.getResources().getDisplayMetrics().density));
                        checkImage(button,selected);
                        Bitmap crop=Bitmap.createBitmap(button.getWidth(),button.getHeight(),Bitmap.Config.ARGB_8888);
                        try{button.draw(new Canvas(crop));try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"phone-dog-"+selected+"-"+large+".png"))){crop.compress(Bitmap.CompressFormat.PNG,100,out);}}catch(java.io.IOException e){throw new RuntimeException(e);}finally{crop.recycle();}
                        long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,button.getWidth()/2f,button.getHeight()/2f,0),up=MotionEvent.obtain(time,time+40,MotionEvent.ACTION_UP,button.getWidth()/2f,button.getHeight()/2f,0);
                        try{assertTrue(button.dispatchTouchEvent(down));assertTrue(button.dispatchTouchEvent(up));}finally{down.recycle();up.recycle();}
                    });SystemClock.sleep(150);
                    android.widget.PopupWindow popup=tools();assertNotNull(popup);assertTrue(popup.isShowing());
                    main(()->{editorGuard();assertNotNull(label(popup.getContentView(),"Settings"));View close=label(popup.getContentView(),"Close tools");assertNotNull(close);assertTrue(close.performClick());assertEquals("Synthetic Shiba check",activity.editor.getText().toString());});assertFalse(popup.isShowing());
                    variants.put(new JSONObject().put("head",selected).put("large_keys",large).put("identity_match",true).put("touch_opens_tools",true).put("minimum_target_dp",48));
                }
            }
        }finally{
            main(()->{
                SharedPreferences.Editor e=p.edit().putInt(PetalButtonIcon.PREFERENCE,0);
                if(before.containsKey("petal_large_keys"))e.putBoolean("petal_large_keys",(Boolean)before.get("petal_large_keys"));else e.remove("petal_large_keys");assertTrue(e.commit());
                Map<String,Object> expected=new HashMap<>(before);expected.put(PetalButtonIcon.PREFERENCE,0);assertEquals(expected,p.getAll());
                activity.editor.setText("");if(ime!=null)ime.loadKeyboard();
            });
        }
        main(()->assertEquals(0,PetalButtonIcon.selected(p)));
        Bundle result=new Bundle();result.putString("petal_phone_shiba_result",new JSONObject().put("variants",variants).put("final_head",0).put("other_preferences_unchanged",true).put("clipboard_access",false).put("audio_capture",false).toString());InstrumentationRegistry.getInstrumentation().addResults(result);
    }
}
