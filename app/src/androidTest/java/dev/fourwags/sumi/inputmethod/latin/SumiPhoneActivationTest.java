package dev.fourwags.sumi.inputmethod.latin;

import android.app.Activity;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher;
import java.io.InputStream;
import java.lang.reflect.Field;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

/** Scoped activation probe: only this synthetic editor; no audio, clipboard, media or messages. */
public class SumiPhoneActivationTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private PetalTestActivity activity;
    private LatinIME ime;
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void ownEditor(){
        boolean resumed=false;
        for(Activity candidate:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(candidate==activity)resumed=true;
        assertTrue("Only the synthetic editor may be foreground",resumed);
        if(ime!=null){EditorInfo info=ime.getCurrentInputEditorInfo();assertNotNull(info);assertEquals(activity.getPackageName(),info.packageName);assertEquals(activity.editor.getId(),info.fieldId);}
    }
    private void shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[256];while(in.read(buffer)!=-1){}}
    }
    @Test public void activateAndTypeSyntheticText()throws Exception{
        activity=rule.getActivity();
        main(()->{ownEditor();activity.options=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;activity.editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);activity.editor.setText("");activity.editor.requestFocus();});
        try{
            shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");
            shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
            Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
            boolean ready=false;
            for(int n=0;n<100&&!ready;n++){
                final boolean[] result={false};
                main(()->{try{ime=(LatinIME)owner.get(KeyboardSwitcher.getInstance());}catch(Exception e){throw new RuntimeException(e);}EditorInfo info=ime==null?null:ime.getCurrentInputEditorInfo();result[0]=ime!=null&&ime.isInputViewShown()&&ime.getCurrentInputConnection()!=null&&info!=null&&activity.getPackageName().equals(info.packageName)&&info.fieldId==activity.editor.getId();if(!result[0]){activity.editor.requestFocus();((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,InputMethodManager.SHOW_IMPLICIT);}});
                ready=result[0];if(!ready)SystemClock.sleep(100);
            }
            assertTrue("Sumi must be ready in the synthetic editor",ready);
            main(()->{ownEditor();ime.onCodeInput('s',-1,-1,false);ime.onCodeInput('u',-1,-1,false);ime.onCodeInput('n',-1,-1,false);});
            SystemClock.sleep(200);
            main(()->{ownEditor();assertEquals("sun",activity.editor.getText().toString());});
            Bundle result=new Bundle();result.putString("sumi_activation_result","{\"synthetic_characters\":3,\"own_editor_only\":true,\"audio\":false,\"clipboard\":false,\"personal_media\":false}");InstrumentationRegistry.getInstrumentation().addResults(result);
        }finally{main(()->activity.editor.setText(""));}
    }
}
