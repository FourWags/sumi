package dev.fourwags.sumi.inputmethod.latin;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.widget.*;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;

/** Two independent controls; previews use the same native layout builder as the IME. */
public final class PetalHeightActivity extends Activity {
    private SharedPreferences prefs;
    private final SeekBar[] sliders=new SeekBar[2];
    private final TextView[] labels=new TextView[2];
    private final PetalHeightPreview[] previews=new PetalHeightPreview[2];
    private final String[] keys={SumiKeyboardHeight.PORTRAIT,SumiKeyboardHeight.LANDSCAPE};
    private final String[] names={"Portrait","Landscape"};
    private boolean refreshing;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->refresh();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=DeviceProtectedUtils.getSharedPreferences(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(24),dp(20),dp(24));box.setBackgroundColor(0xfff2eeeb);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{box.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(16)+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=new TextView(this);title.setText("Keyboard height");title.setTextSize(28);title.setTextColor(0xff211b22);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));title.setAccessibilityHeading(true);box.addView(title);
        TextView help=new TextView(this);help.setText("Adjust each orientation separately. 100% keeps your selected layout’s original size. Previews update as you drag and are scaled to fit. Extra height is limited on short screens.");help.setTextSize(14);help.setTextColor(0xff211b22);help.setPadding(0,dp(8),0,dp(8));help.setLineSpacing(dp(2),1);box.addView(help);
        for(int i=0;i<2;i++){
            final int index=i;
            labels[i]=new TextView(this);labels[i].setTextSize(18);labels[i].setPadding(0,dp(16),0,0);labels[i].setAccessibilityHeading(true);box.addView(labels[i]);
            sliders[i]=new SeekBar(this);sliders[i].setTag(keys[i]);sliders[i].setMax(SumiKeyboardHeight.MAX-SumiKeyboardHeight.MIN);sliders[i].setMinimumHeight(dp(48));sliders[i].setContentDescription(names[i]+" keyboard height");box.addView(sliders[i],new LinearLayout.LayoutParams(-1,dp(48)));
            previews[i]=new PetalHeightPreview(this,i==1?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT);previews[i].setTag(keys[i]+"_preview");box.addView(previews[i],new LinearLayout.LayoutParams(-1,dp(160)));
            sliders[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(!refreshing)prefs.edit().putInt(keys[index],value+SumiKeyboardHeight.MIN).apply();}
                public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
            });
        }
        Button reset=new Button(this);reset.setText("Reset to default");reset.setTag("petal_height_reset");reset.setMinHeight(dp(48));reset.setAllCaps(false);reset.setTextColor(0xff211b22);reset.setBackground(PetalUi.control(this,0xfff1e5e8,0xffd8c4ca,0xff724151));reset.setElevation(0);reset.setStateListAnimator(null);reset.setOnClickListener(v->prefs.edit().remove(keys[0]).remove(keys[1]).apply());box.addView(reset);
        prefs.registerOnSharedPreferenceChangeListener(listener);refresh();
    }
    private void refresh(){
        if(prefs==null)return;refreshing=true;
        for(int i=0;i<2;i++){
            int value=SumiKeyboardHeight.clamp(prefs.getInt(keys[i],100));labels[i].setText(names[i]+" · "+value+"%");sliders[i].setProgress(value-SumiKeyboardHeight.MIN);
            if(android.os.Build.VERSION.SDK_INT>=30)sliders[i].setStateDescription(value+" percent");
            previews[i].update(value);
        }
        refreshing=false;
    }
    @Override protected void onDestroy(){if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(listener);super.onDestroy();}
}
