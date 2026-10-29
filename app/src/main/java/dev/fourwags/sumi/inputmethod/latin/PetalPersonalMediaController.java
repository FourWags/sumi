package dev.fourwags.sumi.inputmethod.latin;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit current-editor insertion. Never holds a destination across opening the import picker. */
public final class PetalPersonalMediaController {
    private final LatinIME ime;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.AbortPolicy());
    private final AtomicInteger generation=new AtomicInteger();
    private PopupWindow popup;private PetalPersonalMediaGrid grid;
    private boolean destroyed;
    public PetalPersonalMediaController(LatinIME ime){this.ime=ime;worker.allowCoreThreadTimeOut(true);}
    private int dp(int n){return Math.round(n*ime.getResources().getDisplayMetrics().density);}
    public void dismiss(){if(popup!=null){popup.dismiss();popup=null;}if(grid!=null){grid.close();grid=null;}}
    public void cancel(){generation.incrementAndGet();worker.getQueue().clear();dismiss();}
    public void destroy(){destroyed=true;cancel();worker.shutdownNow();}
    public void show(View anchor){
        if(destroyed||!ime.petalMediaAllowed()||ime.getCurrentInputConnection()==null)return;cancel();
        LinearLayout panel=new LinearLayout(ime);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(4),dp(8),dp(4));panel.setBackgroundColor(0xfffffaf2);panel.setAccessibilityPaneTitle("Personal media");
        Button manage=new Button(ime);manage.setText("Import & manage");manage.setAllCaps(false);manage.setMinHeight(dp(48));panel.addView(manage);
        grid=new PetalPersonalMediaGrid(ime,id->{dismiss();send(id);});panel.addView(grid,new LinearLayout.LayoutParams(-1,dp(280)));
        Button close=new Button(ime);close.setText("Back to emoji");close.setMinHeight(dp(48));panel.addView(close);close.setOnClickListener(v->dismiss());
        View decor=ime.getWindow().getWindow().getDecorView();View emoji=decor.findViewById(dev.fourwags.sumi.R.id.emoji_palettes_view);View frame=emoji!=null&&emoji.isShown()?emoji:decor.findViewById(dev.fourwags.sumi.R.id.main_keyboard_frame);int height=frame.getHeight(),width=frame.getWidth();if(height<1||width<1){dismiss();return;}
        ScrollView scroll=new ScrollView(ime);scroll.addView(panel);PopupWindow shown=new PopupWindow(scroll,width,height,false);popup=shown;shown.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xfffffaf2));shown.setOutsideTouchable(true);shown.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);shown.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        shown.setOnDismissListener(()->{if(popup==shown){popup=null;if(grid!=null){grid.close();grid=null;}}shown.setOnDismissListener(null);});
        manage.setOnClickListener(v->{cancel();ime.requestHideSelf(0);ime.startActivity(new Intent(ime,PetalPersonalMediaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
        int[] location=new int[2];frame.getLocationInWindow(location);shown.showAtLocation(decor,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,location[1]);
    }
    private boolean valid(int ticket,InputConnection connection,EditorInfo editor){return !destroyed&&generation.get()==ticket&&connection!=null&&editor!=null&&ime.getCurrentInputConnection()==connection&&ime.getCurrentInputEditorInfo()==editor&&ime.isInputViewShown()&&ime.petalMediaAllowed();}
    void send(String id){
        if(destroyed||!ime.petalMediaAllowed())return;
        InputConnection connection=ime.getCurrentInputConnection();EditorInfo editor=ime.getCurrentInputEditorInfo();if(connection==null||editor==null)return;
        String mime=PetalPersonalMediaStore.mime(id);boolean supported=false;
        String[] types=EditorInfoCompat.getContentMimeTypes(editor);
        for(int n=0;n<Math.min(types.length,32);n++){
            String offered=types[n];if(offered==null||offered.length()>64)continue;
            if(new ClipDescription("Image",new String[]{mime}).hasMimeType(offered.toLowerCase(java.util.Locale.ROOT))){supported=true;break;}
        }
        if(!supported){Toast.makeText(ime,"This field does not accept this image type",Toast.LENGTH_SHORT).show();return;}
        final int ticket=generation.incrementAndGet();WeakReference<InputConnection> input=new WeakReference<>(connection);WeakReference<EditorInfo> info=new WeakReference<>(editor);WeakReference<PetalPersonalMediaController> owner=new WeakReference<>(this);Context app=ime.getApplicationContext();
        try{worker.execute(()->{
            File file=null;try{file=PetalPersonalMediaStore.prepareShare(app,id);}catch(IOException|RuntimeException ignored){}
            final File shared=file;new Handler(Looper.getMainLooper()).post(()->{
                PetalPersonalMediaController current=owner.get();if(current==null||!current.valid(ticket,input.get(),info.get())){if(shared!=null)PetalPersonalMediaStore.discardShare(app,shared);return;}
                if(shared==null){Toast.makeText(current.ime,"Unable to prepare image. Check free storage.",Toast.LENGTH_SHORT).show();return;}
                boolean accepted=false;try{
                    Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".media",shared);
                    accepted=input.get().commitContent(new InputContentInfo(uri,new ClipDescription("Personal media",new String[]{mime}),null),InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null);
                }catch(RuntimeException ignored){}
                if(!accepted){PetalPersonalMediaStore.discardShare(app,shared);Toast.makeText(current.ime,"The receiving app declined this image",Toast.LENGTH_SHORT).show();}
                else current.handler.postDelayed(()->{try{PetalPersonalMediaStore.cleanupShares(app);}catch(IOException ignored){}},PetalPersonalMediaStore.SHARE_LIFETIME+1000);
            });
        });}catch(RejectedExecutionException e){Toast.makeText(ime,"Please wait for the current image",Toast.LENGTH_SHORT).show();}
    }
}
