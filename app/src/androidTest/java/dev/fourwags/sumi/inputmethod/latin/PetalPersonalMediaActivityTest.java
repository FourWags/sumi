package dev.fourwags.sumi.inputmethod.latin;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.widget.*;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import static org.junit.Assert.*;
public class PetalPersonalMediaActivityTest {
    @Rule public ActivityTestRule<PetalPersonalMediaActivity> rule=new ActivityTestRule<>(PetalPersonalMediaActivity.class);
    private android.view.View find(android.view.View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.view.View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    @Test public void pickerRequestsOnlyLocalFilesAndResultImportsPrivately()throws Exception{
        PetalPersonalMediaActivity activity=rule.getActivity();Context app=activity.getApplicationContext();Set<String> before=new HashSet<>(PetalPersonalMediaStore.list(app));
        AtomicReference<Intent> picked=new AtomicReference<>();Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){picked.set(new Intent(intent));return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
        instrumentation.addMonitor(monitor);File folder=new File(app.getCacheDir(),"media");folder.mkdirs();File source=new File(folder,"synthetic-picker-"+UUID.randomUUID()+".png");
        try{
            instrumentation.runOnMainSync(()->assertTrue(find(activity.getWindow().getDecorView(),"Import from this phone").performClick()));
            assertNotNull(picked.get());assertTrue(picked.get().getBooleanExtra(Intent.EXTRA_LOCAL_ONLY,false));assertTrue(picked.get().hasCategory(Intent.CATEGORY_OPENABLE));assertEquals("image/*",picked.get().getType());
            Bitmap image=Bitmap.createBitmap(3,3,Bitmap.Config.ARGB_8888);try(OutputStream out=new FileOutputStream(source)){image.eraseColor(0xffd48899);assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
            android.net.Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".media",source);int grants=app.getContentResolver().getPersistedUriPermissions().size();
            instrumentation.runOnMainSync(()->activity.onActivityResult(1,Activity.RESULT_OK,new Intent().setData(uri)));
            java.lang.reflect.Field field=PetalPersonalMediaActivity.class.getDeclaredField("status");field.setAccessible(true);final boolean[] done={false};
            for(int n=0;n<100&&!done[0];n++){instrumentation.runOnMainSync(()->{try{done[0]=((TextView)field.get(activity)).getText().toString().startsWith("Imported.");}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(50);}
            assertTrue("Import must finish with a useful status",done[0]);Set<String> after=new HashSet<>(PetalPersonalMediaStore.list(app));after.removeAll(before);assertEquals(1,after.size());assertTrue(source.isFile());assertEquals(grants,app.getContentResolver().getPersistedUriPermissions().size());
        }finally{instrumentation.removeMonitor(monitor);Set<String> added=new HashSet<>(PetalPersonalMediaStore.list(app));added.removeAll(before);for(String id:added)PetalPersonalMediaStore.remove(app,id);source.delete();}
    }
}
