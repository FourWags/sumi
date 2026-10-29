package dev.fourwags.sumi.inputmethod.latin;
import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.*;
import android.widget.*;
/** Instrumentation-only isolated test surface. Synthetic text only; never accesses external applications. */
public final class PetalTestActivity extends Activity {
    public EditText editor;
    public Button send;
    public boolean acceptsGif;
    public boolean refusesSurroundingText;
    public String receivedMime;
    public String[] acceptedImageTypes;
    public int receivedMediaCount;
    public int options;
    public Integer actionOverride;
    public String privateOptions;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        if(android.os.Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            int ime=insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
            root.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime));return insets;
        }); else root.setFitsSystemWindows(true);
        TextView title=new TextView(this);title.setText("Sumi · Synthetic test editor");root.addView(title);
        editor=new EditText(this) {
            @Override public InputConnection onCreateInputConnection(EditorInfo out) {
                InputConnection base=super.onCreateInputConnection(out);
                out.imeOptions|=options;out.privateImeOptions=privateOptions;
                if(actionOverride!=null)out.imeOptions=(out.imeOptions & ~EditorInfo.IME_MASK_ACTION)|actionOverride;
                if(acceptsGif)out.contentMimeTypes=new String[]{"image/gif"};
                if(acceptedImageTypes!=null)out.contentMimeTypes=acceptedImageTypes;
                return new InputConnectionWrapper(base,false){
                    @Override public CharSequence getTextBeforeCursor(int n,int flags){return refusesSurroundingText?null:super.getTextBeforeCursor(n,flags);}
                    @Override public CharSequence getTextAfterCursor(int n,int flags){return refusesSurroundingText?null:super.getTextAfterCursor(n,flags);}
                    @Override public boolean commitContent(InputContentInfo info,int flags,Bundle opts){
                        if(!acceptsGif&&acceptedImageTypes==null)return false;
                        try {
                            info.requestPermission();
                            try(java.io.InputStream in=getContentResolver().openInputStream(info.getContentUri())){
                                byte[] header=new byte[6];int count=in.read(header);
                                if(count!=6)return false;
                                String type=info.getDescription().getMimeType(0);
                                if("image/gif".equals(type)&&!new String(header,java.nio.charset.StandardCharsets.US_ASCII).startsWith("GIF8"))return false;
                                if("image/png".equals(type)&&(header[0]!=(byte)137||header[1]!=80||header[2]!=78||header[3]!=71))return false;
                            }
                            receivedMime=info.getDescription().getMimeType(0);receivedMediaCount++;info.releasePermission();return true;
                        }catch(Exception e){return false;}
                    }
                };
            }
        };
        editor.setId(View.generateViewId());editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editor.setHint("Synthetic test text only");editor.setMinHeight(Math.round(56*getResources().getDisplayMetrics().density));editor.setMaxLines(3);
        Space spacer=new Space(this);root.addView(spacer,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composer=new LinearLayout(this);composer.setGravity(android.view.Gravity.BOTTOM);
        composer.addView(editor,new LinearLayout.LayoutParams(0,-2,1));
        send=new Button(this);send.setText("Send (test)");send.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));composer.addView(send);root.addView(composer);
        TextView help=new TextView(this);help.setText("This editor comes from the test APK only. No message is sent.");root.addView(help);setContentView(root);editor.requestFocus();
        editor.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT),400);
    }
}
