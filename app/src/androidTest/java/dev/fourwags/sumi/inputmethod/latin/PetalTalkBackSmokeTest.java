package dev.fourwags.sumi.inputmethod.latin;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.UiAutomation;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.SystemClock;
import org.junit.*;
import static org.junit.Assert.*;
import dev.fourwags.sumi.inputmethod.keyboard.*;

/** Run only in an isolated emulator with TalkBack enabled and external settings restoration. */
public class PetalTalkBackSmokeTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private void shell(UiAutomation automation,String command) throws Exception {
        try(android.os.ParcelFileDescriptor fd=automation.executeShellCommand(command);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){
            byte[] buffer=new byte[512];while(in.read(buffer)!=-1){}
        }
    }
    @Test public void talkBackEnabledLiftToTypeAndFocus() throws Exception {
        UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityManager manager=(AccessibilityManager)rule.getActivity().getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
        for(int n=0;n<60&&!manager.isTouchExplorationEnabled();n++)SystemClock.sleep(100);
        assertTrue("TalkBack must enable touch exploration",manager.isTouchExplorationEnabled());
        boolean talkBack=false;
        for(AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN))if(service.getId().contains("com.google.android.marvin.talkback"))talkBack=true;
        assertTrue("Actual TalkBack spoken service must be connected",talkBack);
        shell(automation,"ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell(automation,"ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        final PetalTestActivity activity=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            activity.editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);activity.editor.setText("");activity.editor.requestFocus();
            ((android.view.inputmethod.InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).restartInput(activity.editor);
            ((android.view.inputmethod.InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,0);
        });
        for(int n=0;n<60;n++) {MainKeyboardView v=KeyboardSwitcher.getInstance().getMainKeyboardView();if(v!=null&&v.isShown())break;SystemClock.sleep(100);}
        SystemClock.sleep(500);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();assertNotNull(view);assertTrue(view.isShown());
            Key key=view.getKeyboard().getKey('q');assertNotNull(key);
            float x=key.getHitBox().centerX(),y=key.getHitBox().centerY();long now=SystemClock.uptimeMillis();
            MotionEvent enter=MotionEvent.obtain(now,now,MotionEvent.ACTION_HOVER_ENTER,x,y,0);
            assertTrue(view.onHoverEvent(enter));enter.recycle();
            MotionEvent exit=MotionEvent.obtain(now,now+100,MotionEvent.ACTION_HOVER_EXIT,x,y,0);
            assertTrue(view.onHoverEvent(exit));exit.recycle();assertFalse(view.isShowingMoreKeysPanel());
        });SystemClock.sleep(500);
        final String[] text={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->text[0]=activity.editor.getText().toString());
        assertEquals("TalkBack touch-exploration lift must insert one synthetic letter","q",text[0].toLowerCase(java.util.Locale.US));
    }
}
