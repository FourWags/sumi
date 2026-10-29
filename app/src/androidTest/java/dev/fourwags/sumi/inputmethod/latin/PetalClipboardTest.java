package dev.fourwags.sumi.inputmethod.latin;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher;
import dev.fourwags.sumi.inputmethod.latin.common.Constants;
import java.lang.reflect.Field;
import org.junit.*;
import static org.junit.Assert.*;
/** Opt-in emulator-only clipboard fixture. Never reads or overwrites a phone clipboard. */
public class PetalClipboardTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private LatinIME ime;private PetalTestActivity activity;
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void shell(String cmd)throws Exception{try(ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(cmd);java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[512];while(in.read(b)!=-1){}}}
    private View find(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),label);if(v!=null)return v;}return null;}
    private PopupWindow popup()throws Exception{Field f=LatinIME.class.getDeclaredField("petalToolsPopup");f.setAccessible(true);Object p=f.get(ime);f=PetalToolsPopup.class.getDeclaredField("active");f.setAccessible(true);return (PopupWindow)f.get(p);}
    private void open() {main(()->ime.petalShowTools(ime.getWindow().getWindow().getDecorView()));SystemClock.sleep(300);}
    @Test public void explicitClipboardPasteIsLiteralAndNeverSaved()throws Exception {
        assertTrue("Owned emulator only",Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT).contains("generic")||Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk"));activity=rule.getActivity();
        shell("ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME");shell("ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME");
        main(()->{activity.editor.setText("");activity.editor.requestFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,0);});
        Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
        for(int n=0;n<100;n++){ime=(LatinIME)owner.get(KeyboardSwitcher.getInstance());if(ime!=null&&ime.getCurrentInputConnection()!=null&&KeyboardSwitcher.getInstance().getMainKeyboardView()!=null)break;SystemClock.sleep(100);}assertNotNull(ime);SystemClock.sleep(500);
        ClipboardManager clipboard=activity.getSystemService(ClipboardManager.class);String marker=" synthetic_clipboard_marker .com\nline two";
        try {
            main(()->{for(char c:"teh".toCharArray())ime.onCodeInput(c,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false);clipboard.setPrimaryClip(ClipData.newPlainText("Synthetic fixture",marker));});SystemClock.sleep(500);
            open();PopupWindow window=popup();main(()->find(window.getContentView(),"Clipboard").performClick());SystemClock.sleep(300);
            main(()->{assertNotNull(find(window.getContentView(),marker));PetalAccessibilityChecks.check(window.getContentView(),"clipboard");find(window.getContentView(),"Paste").performClick();});SystemClock.sleep(500);
            main(()->{assertEquals("teh"+marker,activity.editor.getText().toString());ime.onCodeInput('z',Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false);});SystemClock.sleep(300);
            main(()->assertEquals("teh"+marker+"z",activity.editor.getText().toString()));
            open();final PopupWindow second=popup();main(()->find(second.getContentView(),"Select all").performClick());SystemClock.sleep(300);
            open();final PopupWindow third=popup();main(()->find(third.getContentView(),"Copy").performClick());SystemClock.sleep(300);main(()->{assertEquals("teh"+marker+"z",activity.editor.getText().toString());assertEquals("teh"+marker+"z",clipboard.getPrimaryClip().getItemAt(0).getText().toString());});
            main(()->{clipboard.setPrimaryClip(ClipData.newUri(activity.getContentResolver(),"Synthetic URI",android.net.Uri.parse("content://synthetic.invalid/item")));});
            open();final PopupWindow uri=popup();main(()->find(uri.getContentView(),"Clipboard").performClick());SystemClock.sleep(200);
            main(()->{assertNotNull(find(uri.getContentView(),"No text on the clipboard"));assertFalse(find(uri.getContentView(),"Paste").isEnabled());uri.dismiss();});
            for(java.io.File dir:new java.io.File[]{activity.getFilesDir(),activity.getCacheDir(),activity.createDeviceProtectedStorageContext().getFilesDir()})scan(dir,"synthetic_clipboard_marker");
        } finally {main(()->{clipboard.clearPrimaryClip();try{PopupWindow window=popup();if(window!=null)window.dismiss();}catch(Exception e){throw new RuntimeException(e);}});}
    }
    private void scan(java.io.File dir,String marker)throws Exception{java.io.File[] files=dir.listFiles();if(files==null)return;for(java.io.File file:files)if(file.isDirectory())scan(file,marker);else assertFalse("Clipboard persisted: "+file.getName(),new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains(marker));}
}
