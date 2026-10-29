package dev.fourwags.sumi.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import dev.fourwags.sumi.inputmethod.keyboard.*;
import dev.fourwags.sumi.inputmethod.latin.settings.*;
import dev.fourwags.sumi.inputmethod.latin.utils.*;

/** Noninteractive, scaled rendering of the actual native QWERTY geometry. Never opens an editor. */
public final class PetalHeightPreview extends View {
    private final int orientation;
    private int percent=100, systemInsets;
    private String signature="";
    private KeyboardView renderer;
    private Keyboard keyboard;
    public PetalHeightPreview(Context context,int orientation){super(context);this.orientation=orientation;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    public Keyboard getPreviewKeyboard(){return keyboard;}
    public void update(int value){
        SharedPreferences p=DeviceProtectedUtils.getSharedPreferences(getContext());
        String next=value+":"+p.getBoolean("petal_large_keys",false)+":"+PetalThemeCatalog.selected(p)+":"+systemInsets;
        if(next.equals(signature)&&renderer!=null)return;
        signature=next;percent=value;build();requestLayout();invalidate();
    }
    @Override public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets insets){
        systemInsets=insets.getSystemWindowInsetTop()+insets.getSystemWindowInsetBottom();update(percent);return super.onApplyWindowInsets(insets);
    }
    private void releaseRenderer(){
        if(renderer!=null)renderer.deallocateMemory();
        renderer=null;keyboard=null;
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();update(percent);
    }
    @Override protected void onDetachedFromWindow(){
        super.onDetachedFromWindow();releaseRenderer();signature="";
    }
    private void build(){
        // This renderer is drawn manually and never receives child-view detach.
        // Free its software bitmap before replacing it instead of waiting for GC.
        releaseRenderer();
        Configuration config=new Configuration(getResources().getConfiguration());
        if(config.orientation!=orientation){int width=config.screenWidthDp;config.screenWidthDp=config.screenHeightDp;config.screenHeightDp=width;}
        config.orientation=orientation;
        SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(getContext());
        Context context=new ContextThemeWrapper(getContext().createConfigurationContext(config),PetalThemeCatalog.STYLES[PetalThemeCatalog.selected(prefs)]);
        android.content.res.Resources res=context.getResources();
        int width=Math.round(config.screenWidthDp*res.getDisplayMetrics().density);
        boolean large=prefs.getBoolean("petal_large_keys",false);
        int baseline=ResourceUtils.getPetalBaseKeyboardHeight(res,large,prefs.getBoolean(DebugSettings.PREF_RESIZE_KEYBOARD,false),Settings.readKeyboardHeight(prefs,1f));
        int bars=systemInsets;
        int height=SumiKeyboardHeight.pixels(baseline,percent,Math.round(config.screenHeightDp*res.getDisplayMetrics().density),bars,res.getDisplayMetrics().density);
        SubtypeLocaleUtils.init(context);
        RichInputMethodSubtype subtype=new RichInputMethodSubtype(new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeLocale("en_US").setSubtypeMode("keyboard").setSubtypeExtraValue("KeyboardLayoutSet=qwerty,AsciiCapable").setIsAsciiCapable(true).build());
        EditorInfo editor=new EditorInfo();editor.inputType=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        keyboard=new KeyboardLayoutSet.Builder(context,editor).setKeyboardGeometry(width,height).setSubtype(subtype).setPetalLargeKeys(large).setNumberRowEnabled(dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(context).getBoolean(dev.fourwags.sumi.inputmethod.latin.settings.Settings.PREF_SHOW_NUMBER_ROW,false)).setVoiceInputKeyEnabled(false).setLanguageSwitchKeyEnabled(false).setEmojiKeyEnabled(false).build().getKeyboard(KeyboardId.ELEMENT_ALPHABET);
        renderer=new KeyboardView(context,null);renderer.setKeyboard(keyboard);
        int w=keyboard.mOccupiedWidth+renderer.getPaddingLeft()+renderer.getPaddingRight(),h=keyboard.mOccupiedHeight+renderer.getPaddingTop()+renderer.getPaddingBottom();
        renderer.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));renderer.layout(0,0,w,h);
        setContentDescription((orientation==Configuration.ORIENTATION_LANDSCAPE?"Landscape":"Portrait")+" keyboard preview, "+percent+" percent, "+(large?"Large keys":"Standard QWERTY"));
    }
    @Override protected void onMeasure(int w,int h){if(renderer==null)build();int width=MeasureSpec.getSize(w);setMeasuredDimension(width,Math.round(width*renderer.getHeight()/(float)renderer.getWidth()));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(renderer==null)return;canvas.save();float scale=getWidth()/(float)renderer.getWidth();canvas.scale(scale,scale);renderer.draw(canvas);canvas.restore();}
}
