package dev.fourwags.sumi.inputmethod.latin;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit private file import/removal; never receives insertion destinations or editor text. */
public final class PetalPersonalMediaActivity extends Activity {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final CancellationSignal importCancellation=new CancellationSignal();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PetalPersonalMediaGrid grid;private Button add;private TextView status;private boolean busy;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(16),dp(16),dp(16));box.setBackgroundColor(0xfffffaf2);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        box.setOnApplyWindowInsetsListener((v,insets)->{box.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(16)+insets.getSystemWindowInsetBottom());return insets;});ScrollView page=new ScrollView(this);page.setFillViewport(true);page.addView(box);setContentView(page);
        TextView title=new TextView(this);title.setText("Personal GIFs & images");title.setTextColor(0xff211b22);title.setTextSize(22);title.setAccessibilityHeading(true);box.addView(title);
        TextView note=new TextView(this);note.setText("Only on this phone. Originals stay untouched. GIFs keep animation; other images become PNG stickers without photo metadata. Not included in backup, device transfer, or GitHub. Uninstalling or clearing app data removes this collection.");note.setTextColor(0xff211b22);box.addView(note);
        add=new Button(this);add.setText("Import from this phone");add.setAllCaps(false);add.setMinHeight(dp(48));box.addView(add);add.setOnClickListener(v->{
            if(busy)return;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY,true).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/gif","image/png","image/jpeg","image/webp"});
            try{startActivityForResult(intent,1);}catch(ActivityNotFoundException e){status.setText("No local file picker is available.");}
        });
        status=new TextView(this);status.setTextColor(0xff211b22);status.setText("Up to 128 items, 8 MB per import, 64 MB total. GIFs: up to 2048 px and 300 frames. Still images: up to 4096 px, saved at up to 1024 px. Tap an item to remove its keyboard copy.");status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);box.addView(status);
        grid=new PetalPersonalMediaGrid(this,this::remove);box.addView(grid,new LinearLayout.LayoutParams(-1,dp(420)));
        Button done=new Button(this);done.setText("Done");done.setMinHeight(dp(48));done.setOnClickListener(v->finish());box.addView(done);
    }
    private void remove(String id){if(busy)return;new AlertDialog.Builder(this).setTitle("Remove from keyboard?").setMessage("Only the keyboard’s copy is removed. The original and anything already sent stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->run(()->{PetalPersonalMediaStore.remove(getApplicationContext(),id);return "Removed from keyboard.";})).show();}
    private interface Work{String run()throws java.io.IOException;}
    private void run(Work task){if(busy||cancelled.get())return;busy=true;add.setEnabled(false);status.setText("Working…");worker.execute(()->{
        String message;try{message=task.run();}catch(java.io.IOException|RuntimeException e){message="Could not finish. Use a valid local image within the size limits and check free storage.";}final String result=message;
        handler.post(()->{if(cancelled.get())return;busy=false;add.setEnabled(true);status.setText(result);grid.reload();});
    });}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=1||result!=RESULT_OK||data==null)return;
        Uri uri=data.getData();if(uri==null||!"content".equals(uri.getScheme())){status.setText("Choose a local image file.");return;}
        // Copy during the temporary picker grant; never persist the source URI or take a durable grant.
        run(()->{PetalPersonalMediaStore.importUri(getApplicationContext(),uri,cancelled,importCancellation);return "Imported. Return to the keyboard’s emoji page and choose My media.";});
    }
    @Override protected void onDestroy(){synchronized(cancelled){cancelled.set(true);}importCancellation.cancel();worker.shutdownNow();handler.removeCallbacksAndMessages(null);if(grid!=null)grid.close();super.onDestroy();}
}
