package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;

import android.os.SystemClock;
import android.widget.*;
import android.content.*;
import android.content.pm.PackageManager;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Human-operated synthetic editor. Never starts a microphone or grants permission. */
public class PetalPhoneThemeTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void save(File file,JSONObject object)throws Exception{try(FileWriter out=new FileWriter(file)){out.write(object.toString(2));}}
    @Test public void humanVoiceAndNeutralThemesRestoreSettings()throws Exception {
        PetalTestActivity a=rule.getActivity();SharedPreferences p=DeviceProtectedUtils.getSharedPreferences(a);
        Map<String,?> original=new HashMap<>(p.getAll());int originalTheme=PetalThemeCatalog.selected(p);
        int[] aliases=new int[PetalThemeCatalog.ALIAS_COUNT];for(int i=0;i<aliases.length;i++)aliases[i]=a.getPackageManager().getComponentEnabledSetting(new ComponentName(a,a.getPackageName()+".Icon"+i));
        File state=new File(a.getExternalFilesDir(null),"phone-neutral-state.json");JSONArray checks=new JSONArray();JSONObject result=new JSONObject().put("original_theme",PetalThemeCatalog.NAMES[originalTheme]).put("checks",checks).put("automatic_audio_capture",false);
        boolean rehearsal="true".equals(InstrumentationRegistry.getArguments().getString("rehearsal"));
        if(rehearsal)assertTrue(android.os.Build.MODEL.toLowerCase(Locale.ROOT).contains("sdk")||android.os.Build.FINGERPRINT.toLowerCase(Locale.ROOT).contains("generic"));
        if(rehearsal)for(String command:new String[]{"ime enable dev.fourwags.sumi.inputmethod.latin/.LatinIME","ime set dev.fourwags.sumi.inputmethod.latin/.LatinIME"}) {
            try(android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[512];while(in.read(buffer)!=-1){}}
        }
        TextView info=new TextView(a);CheckBox confirmed=new CheckBox(a);confirmed.setText("Voice icon, waveform and voice typing worked");AtomicBoolean next=new AtomicBoolean();
        main(()->{LinearLayout root=(LinearLayout)a.editor.getParent().getParent();info.setTextSize(16);root.addView(info,1);root.addView(confirmed,2);a.send.setOnClickListener(v->next.set(true));});
        try {
            for(int n=0;n<100;n++){if(KeyboardSwitcher.getInstance().getMainKeyboardView()!=null&&KeyboardSwitcher.getInstance().getMainKeyboardView().isShown())break;SystemClock.sleep(100);}
            for(int theme:new int[]{16,17}) {
                next.set(false);
                main(()->{confirmed.setChecked(false);a.editor.setText("");info.setText(PetalThemeCatalog.NAMES[theme]+" — say only: Sumi is ready. Tap the speech-bubble button to speak; tap again to stop. Check the box if it works, then tap "+(theme==16?"Next theme.":"Finish & restore."));a.send.setText(theme==16?"Next theme":"Finish & restore");PetalSettingsActivity.selectTheme(a,theme);});
                final boolean[] ready={false};
                for(int wait=0;wait<40&&!ready[0];wait++) {
                    main(()->{if(KeyboardSwitcher.getInstance().getMainKeyboardView()!=null){android.util.TypedValue color=new android.util.TypedValue();KeyboardSwitcher.getInstance().getMainKeyboardView().getContext().getTheme().resolveAttribute(R.attr.petalSurface,color,true);ready[0]=color.data==(theme==16?0xffe6e6e6:0xff181818);}});SystemClock.sleep(100);
                }
                main(()->{android.util.TypedValue color=new android.util.TypedValue();KeyboardSwitcher.getInstance().getMainKeyboardView().getContext().getTheme().resolveAttribute(R.attr.petalSurface,color,true);assertEquals(theme==16?0xffe6e6e6:0xff181818,color.data);assertEquals(theme,PetalThemeCatalog.selected(p));});
                result.put("phase",PetalThemeCatalog.IDS[theme]);save(state,result);android.os.Bundle phase=new android.os.Bundle();phase.putString("petal_phone_phase",result.toString());InstrumentationRegistry.getInstrumentation().sendStatus(2,phase);
                long end=SystemClock.uptimeMillis()+150000;
                if(rehearsal)main(()->{a.editor.setText("Sumi is ready");next.set(true);});
                while(!next.get()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(200);
                final boolean[] human={false},text={false};main(()->{human[0]=confirmed.isChecked();text[0]=a.editor.getText().toString().toLowerCase(Locale.US).contains("sumi is ready");});
                checks.put(new JSONObject().put("theme",PetalThemeCatalog.NAMES[theme]).put("theme_binding_verified",true).put("human_voice_confirmed",human[0]&&!rehearsal).put("synthetic_phrase_present",text[0]).put("advanced",next.get()));
                if(!next.get()){result.put("timed_out",true);break;}
            }
        } finally {
            main(()->{
                SharedPreferences.Editor edit=p.edit();
                for(String key:new String[]{"petal_theme","petal_icon"}){if(original.containsKey(key))edit.putInt(key,(Integer)original.get(key));else edit.remove(key);}assertTrue(edit.commit());
                for(int i=0;i<aliases.length;i++)a.getPackageManager().setComponentEnabledSetting(new ComponentName(a,a.getPackageName()+".Icon"+i),aliases[i],PackageManager.DONT_KILL_APP);
                assertTrue("All app preferences preserved",original.equals(p.getAll()));
                for(int i=0;i<aliases.length;i++)assertEquals(aliases[i],a.getPackageManager().getComponentEnabledSetting(new ComponentName(a,a.getPackageName()+".Icon"+i)));
                a.editor.setText("");
            });
            result.put("phase","restored").put("original_theme_restored",true).put("all_preferences_unchanged",true).put("launcher_aliases_restored",true).put("rehearsal",rehearsal);save(state,result);android.os.Bundle report=new android.os.Bundle();report.putString("petal_phone_result",result.toString());InstrumentationRegistry.getInstrumentation().addResults(report);
        }
    }
}
