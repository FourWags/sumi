package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import dev.fourwags.sumi.inputmethod.latin.common.Constants;
import java.io.*;

/** Small offline collection. No search service, URI fetch, content history, or keyboard long press. */
public final class PetalMediaPopup {
    private final LatinIME ime;
    private PopupWindow active;
    public void dismiss(){if(active!=null){active.dismiss();active=null;}}
    private int dp(int size){return Math.round(size*ime.getResources().getDisplayMetrics().density);}
    public PetalMediaPopup(LatinIME ime) { this.ime = ime; }
    public void show(View anchor) {
        if (!ime.petalMediaAllowed()) return;
        dismiss();
        LinearLayout box=new LinearLayout(ime);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(16),dp(16),dp(16));box.setBackgroundColor(0xfff7f0e4);
        box.setAccessibilityPaneTitle(ime.getString(R.string.petal_media_description));
        PopupWindow popup=new PopupWindow(box,Math.min(anchor.getRootView().getWidth(),dp(360)),android.view.ViewGroup.LayoutParams.WRAP_CONTENT,true);
        popup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        active=popup;
        popup.setOnDismissListener(()->{
            stopAnimations(box);box.removeAllViews();if(active==popup)active=null;
            popup.setOnDismissListener(null);
        });
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xfff7f0e4));popup.setOutsideTouchable(true);
        Button emoji=new Button(ime);emoji.setText("Emoji");emoji.setMinHeight(dp(48));box.addView(emoji);
        emoji.setOnClickListener(v->{popup.dismiss();if(ime.petalMediaAllowed())ime.onCodeInput(Constants.CODE_EMOJI,Constants.NOT_A_COORDINATE,Constants.NOT_A_COORDINATE,false);});
        TextView label=new TextView(ime);label.setText("Offline GIFs · bundled Petal animations");box.addView(label);
        LinearLayout gifs=new LinearLayout(ime);box.addView(gifs);
        for(String name:new String[]{"bloom","wave"}) {
            LinearLayout card=new LinearLayout(ime);card.setOrientation(LinearLayout.VERTICAL);
            gifs.addView(card,new LinearLayout.LayoutParams(0,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,1));
            ImageView preview=new ImageView(ime);preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(preview,new LinearLayout.LayoutParams(-1,dp(88)));
            try {
                android.graphics.drawable.Drawable drawable=android.graphics.ImageDecoder.decodeDrawable(
                    android.graphics.ImageDecoder.createSource(ime.getAssets(),name+".gif"));
                preview.setImageDrawable(drawable);
                if(drawable instanceof android.graphics.drawable.AnimatedImageDrawable)((android.graphics.drawable.AnimatedImageDrawable)drawable).start();
            } catch(IOException ignored) {}
            Button gif=new Button(ime);gif.setText(name.equals("bloom")?"Bloom":"Wave");gif.setContentDescription(ime.getString(R.string.petal_insert_gif_description,name));gif.setMinHeight(dp(48));card.addView(gif);
            gif.setOnClickListener(v->{popup.dismiss();sendGif(name);});
        }
        Button close=new Button(ime);close.setText(R.string.petal_close_media);close.setMinHeight(dp(48));close.setOnClickListener(v->popup.dismiss());box.addView(close);
        popup.showAsDropDown(anchor,0,-dp(250));
    }
    private void stopAnimations(View view) {
        if(view instanceof ImageView){android.graphics.drawable.Drawable drawable=((ImageView)view).getDrawable();if(drawable instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)drawable).stop();((ImageView)view).setImageDrawable(null);}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)stopAnimations(group.getChildAt(i));}
    }
    private void sendGif(String name) {
        if(!ime.petalMediaAllowed())return;
        EditorInfo editor=ime.getCurrentInputEditorInfo();InputConnection connection=ime.getCurrentInputConnection();
        if(editor==null || connection==null)return;
        boolean supported=false;
        for(String mime:EditorInfoCompat.getContentMimeTypes(editor)) if(new ClipDescription("GIF",new String[]{"image/gif"}).hasMimeType(mime))supported=true;
        if(!supported){Toast.makeText(ime,"This field does not accept GIFs",Toast.LENGTH_SHORT).show();return;}
        try {
            File folder=new File(ime.getCacheDir(),"media");folder.mkdirs();File file=new File(folder,name+".gif");
            try(InputStream in=ime.getAssets().open(name+".gif");OutputStream out=new FileOutputStream(file)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
            Uri uri=FileProvider.getUriForFile(ime,ime.getPackageName()+".media",file);
            boolean accepted=connection.commitContent(new InputContentInfo(uri,new ClipDescription("Petal "+name,new String[]{"image/gif"}),null),InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null);
            if(!accepted)Toast.makeText(ime,"The receiving app declined this GIF",Toast.LENGTH_SHORT).show();
        } catch(Exception e) {Toast.makeText(ime,"Unable to insert bundled GIF",Toast.LENGTH_SHORT).show();}
    }
}
