package dev.fourwags.sumi.inputmethod.latin;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher;
import android.os.*;
import android.content.*;
import android.view.inputmethod.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Locale;
import org.junit.*;
import static org.junit.Assert.*;
/** Dedicated owned-emulator tmpfs ENOSPC probe. Never fills a personal device. */
public class PetalStorageRecoveryTest {
 @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
 private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 private void phase(String p){Bundle b=new Bundle();b.putString("petal_storage_phase",p);InstrumentationRegistry.getInstrumentation().sendStatus(2,b);}
 private void waitFile(File p)throws Exception{for(int n=0;n<300&&!p.exists();n++)SystemClock.sleep(100);assertTrue("Scoped pressure/recovery handshake",p.exists());}
 @Test public void boundedEnospcMediaFailureAndRecovery()throws Exception {
  assertTrue("Emulator only",Build.FINGERPRINT.toLowerCase(Locale.ROOT).contains("generic")||Build.MODEL.toLowerCase(Locale.ROOT).contains("sdk"));
  PetalTestActivity a=rule.getActivity();File ready=new File(a.getCacheDir(),"petal-enospc-ready"),restored=new File(a.getCacheDir(),"petal-enospc-recovered");assertFalse(ready.exists());assertFalse(restored.exists());
  for(String command:new String[]{"ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME","ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME"})try(android.os.ParcelFileDescriptor f=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(f)){byte[] b=new byte[512];while(in.read(b)!=-1){}}
  main(()->{a.acceptsGif=true;((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(a.editor);((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(a.editor,0);});
  Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);LatinIME ime=null;
  for(int n=0;n<100;n++){ime=(LatinIME)owner.get(KeyboardSwitcher.getInstance());if(ime!=null&&ime.getCurrentInputConnection()!=null&&ime.getCurrentInputEditorInfo()!=null&&ime.getCurrentInputEditorInfo().contentMimeTypes!=null)break;SystemClock.sleep(100);}assertNotNull(ime);
  final LatinIME target=ime;Method send=PetalMediaPopup.class.getDeclaredMethod("sendGif",String.class);send.setAccessible(true);
  try {
   phase("ready_for_pressure");waitFile(ready);
   boolean enospc=false;File probe=new File(new File(a.getCacheDir(),"media"),"petal-enospc-probe");try(OutputStream out=new FileOutputStream(probe)){out.write(new byte[4096]);}catch(IOException e){enospc=e.getMessage()!=null&&e.getMessage().contains("ENOSPC");}finally{probe.delete();}assertTrue("Actual bounded filesystem ENOSPC",enospc);
   main(()->{try{send.invoke(new PetalMediaPopup(target),"bloom");}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(300);assertNull("Failed write must not send partial GIF",a.receivedMime);
   main(()->target.onCodeInput('q',-1,-1,false));SystemClock.sleep(200);main(()->assertEquals(1,a.editor.length()));phase("failure_handled");waitFile(restored);
   main(()->{try{send.invoke(new PetalMediaPopup(target),"bloom");}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(300);assertEquals("image/gif",a.receivedMime);
   Bundle b=new Bundle();b.putString("petal_storage_result","{\"enospc_confirmed\":true,\"partial_gif_not_sent\":true,\"typing_remained_usable\":true,\"gif_recovered\":true}");InstrumentationRegistry.getInstrumentation().addResults(b);
  }finally{ready.delete();restored.delete();main(()->a.editor.setText(""));}
 }
}
