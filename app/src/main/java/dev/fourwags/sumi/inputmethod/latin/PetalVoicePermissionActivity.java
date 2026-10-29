package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
/** User-initiated permission flow; granting permission never starts recording. */
public final class PetalVoicePermissionActivity extends Activity {
    private Button allow;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(24*getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);
        TextView explanation=new TextView(this);explanation.setText(R.string.petal_voice_permission_info);box.addView(explanation);
        allow=new Button(this);allow.setText(R.string.petal_voice_permission_button);allow.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));box.addView(allow);
        allow.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)finish();else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);});
        Button cancel=new Button(this);cancel.setText(android.R.string.cancel);box.addView(cancel);cancel.setOnClickListener(v->finish());ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
    }
    @Override protected void onResume(){
        super.onResume();
        boolean granted=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        allow.setText(granted?R.string.petal_microphone_allowed:R.string.petal_voice_permission_button);
        allow.setAllCaps(false);PetalUi.setupControl(allow,granted);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==1){Toast.makeText(this,results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?R.string.petal_voice_granted:R.string.petal_voice_denied,Toast.LENGTH_LONG).show();finish();}}
}
