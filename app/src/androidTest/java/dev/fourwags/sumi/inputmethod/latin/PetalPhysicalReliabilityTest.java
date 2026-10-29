package dev.fourwags.sumi.inputmethod.latin;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import dev.fourwags.sumi.inputmethod.keyboard.*;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
import java.util.*;
import java.lang.reflect.Field;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Scoped synthetic observations; no clipboard, microphone, heap dump or personal editor. */
public class PetalPhysicalReliabilityTest {
    @Rule public ActivityTestRule<PetalTestActivity> rule=PetalTestRunner.editorRule();
    private PetalTestActivity activity;
    private LatinIME ime;
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void ready()throws Exception {
        Field owner=KeyboardSwitcher.class.getDeclaredField("mLatinIME");owner.setAccessible(true);
        for(int n=0;n<100;n++){
            final boolean[] ok={false},nudge={n%5==0};main(()->{
                for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof PetalTestActivity)activity=(PetalTestActivity)a;
                try{ime=(LatinIME)owner.get(KeyboardSwitcher.getInstance());ok[0]=ime!=null&&ime.isInputViewShown()&&ime.getCurrentInputConnection()!=null&&KeyboardSwitcher.getInstance().getMainKeyboardView()!=null&&KeyboardSwitcher.getInstance().getMainKeyboardView().isShown();}catch(Exception e){throw new RuntimeException(e);}
                if(!ok[0]&&nudge[0]){activity.editor.requestFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,InputMethodManager.SHOW_IMPLICIT);}
            });if(ok[0])return;SystemClock.sleep(100);
        }throw new AssertionError("Synthetic keyboard did not become ready");
    }
    private JSONObject memory()throws Exception{
        Runtime runtime=Runtime.getRuntime();String[] fd=new java.io.File("/proc/self/fd").list();
        return new JSONObject().put("java_used_bytes",runtime.totalMemory()-runtime.freeMemory()).put("native_allocated_bytes",Debug.getNativeHeapAllocatedSize()).put("total_pss_kb",Debug.getPss()).put("fd_count",fd==null?-1:fd.length);
    }
    private JSONArray array(List<Long> values){JSONArray out=new JSONArray();for(long n:values)out.put(n);return out;}
    private void restore(SharedPreferences p,Map<String,?> original){
        SharedPreferences.Editor edit=p.edit();String key="petal_large_keys";if(original.containsKey(key))edit.putBoolean(key,(Boolean)original.get(key));else edit.remove(key);assertTrue(edit.commit());
    }
    @Test public void syntheticRecoveryOnly()throws Exception {
        activity=rule.getActivity();SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);Map<String,?> original=new HashMap<>(prefs.getAll());
        Bundle phase=new Bundle();phase.putString("petal_physical_phase","synthetic_editor_open");InstrumentationRegistry.getInstrumentation().sendStatus(2,phase);
        try{ready();main(()->{assertEquals(0,activity.editor.length());ime.onCodeInput('q',-1,-1,false);});SystemClock.sleep(200);main(()->{assertEquals(1,activity.editor.length());activity.editor.setText("");});assertTrue(original.equals(prefs.getAll()));Bundle result=new Bundle();result.putString("petal_recovery_result","{\"synthetic_editor_recovered\":true,\"all_preferences_unchanged\":true}");InstrumentationRegistry.getInstrumentation().addResults(result);}
        finally{main(()->{if(activity!=null&&!activity.isDestroyed())activity.editor.setText("");});}
    }
    @Test public void syntheticCyclesLatencyAndRecoveryPreserveSettings()throws Exception {
        activity=rule.getActivity();SharedPreferences prefs=DeviceProtectedUtils.getSharedPreferences(activity);Map<String,?> original=new HashMap<>(prefs.getAll());
        JSONObject report=new JSONObject().put("synthetic_only",true).put("audio_capture",false).put("clipboard_access",false).put("measurement_build_debuggable",true);
        List<Long> frames=Collections.synchronizedList(new ArrayList<>());int[] dropped={0};Window[] window={null};
        Window.OnFrameMetricsAvailableListener listener=(w,m,drop)->{synchronized(frames){if(frames.size()<4096)frames.add(m.getMetric(FrameMetrics.TOTAL_DURATION));}dropped[0]+=drop;};
        int orientation=activity.getRequestedOrientation();
        try {
            Bundle phase=new Bundle();phase.putString("petal_physical_phase","synthetic_editor_open");InstrumentationRegistry.getInstrumentation().sendStatus(2,phase);
            ready();main(()->{window[0]=ime.getWindow().getWindow();window[0].addOnFrameMetricsAvailableListener(listener,new Handler(Looper.getMainLooper()));});
            report.put("baseline",memory());JSONArray layouts=new JSONArray();
            for(boolean large:new boolean[]{false,true}){
                main(()->{prefs.edit().putBoolean("petal_large_keys",large).apply();ime.loadKeyboard();});SystemClock.sleep(300);ready();
                List<Long> readiness=new ArrayList<>(),dispatch=new ArrayList<>(),visible=new ArrayList<>();
                for(int cycle=0;cycle<24;cycle++){
                    main(()->((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(activity.editor.getWindowToken(),0));
                    boolean hidden=false;for(int wait=0;wait<30;wait++){final boolean[] off={false};main(()->off[0]=!ime.isInputViewShown());if(off[0]){hidden=true;break;}SystemClock.sleep(50);}assertTrue("Keyboard must actually hide before show measurement",hidden);
                    long started=SystemClock.elapsedRealtimeNanos();main(()->{activity.editor.requestFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.editor,InputMethodManager.SHOW_IMPLICIT);});ready();readiness.add(SystemClock.elapsedRealtimeNanos()-started);
                }
                main(()->activity.editor.setText(""));SystemClock.sleep(150);
                for(int n=0;n<100;n++){
                    final int expected=n+1;final long[] cost={0};long start=SystemClock.elapsedRealtimeNanos();
                    main(()->{
                        MainKeyboardView view=KeyboardSwitcher.getInstance().getMainKeyboardView();Key key=view.getKeyboard().getKey('q');assertNotNull(key);float x=key.getX()+key.getWidth()/2f,y=key.getY()+key.getHeight()/2f;long t=SystemClock.uptimeMillis();long begin=SystemClock.elapsedRealtimeNanos();
                        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,x,y,0);view.dispatchTouchEvent(e);e.recycle();}cost[0]=SystemClock.elapsedRealtimeNanos()-begin;
                    });
                    boolean arrived=false;for(int wait=0;wait<200;wait++){final boolean[] found={false};main(()->found[0]=activity.editor.length()==expected);if(found[0]){arrived=true;break;}SystemClock.sleep(5);}assertTrue("One synthetic key must reach only this editor",arrived);dispatch.add(cost[0]);visible.add(SystemClock.elapsedRealtimeNanos()-start);
                }
                main(()->activity.editor.setText(""));Runtime.getRuntime().gc();SystemClock.sleep(300);
                layouts.put(new JSONObject().put("large",large).put("show_ready_ns",array(readiness)).put("dispatch_ns",array(dispatch)).put("key_to_editor_observed_ns",array(visible)).put("memory_after",memory()));
            }
            report.put("layouts",layouts);
            for(int requested:new int[]{ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}){main(()->activity.setRequestedOrientation(requested));SystemClock.sleep(1000);ready();}
            report.put("rotation_recovered",true);
            main(()->ime.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));SystemClock.sleep(300);ready();report.put("low_memory_callback_recovered",true);
            main(()->{if(window[0]!=null){Window old=window[0];window[0]=null;try{old.removeOnFrameMetricsAvailableListener(listener);}catch(IllegalArgumentException alreadyDetached){}}});
            report.put("ime_frame_total_ns",array(frames)).put("dropped_frame_reports",dropped[0]);
            long cpu=android.os.Process.getElapsedCpuTime();SystemClock.sleep(5000);report.put("five_second_idle_process_cpu_ms",android.os.Process.getElapsedCpuTime()-cpu).put("settled_memory",memory());
            if(Build.VERSION.SDK_INT>=29)report.put("thermal_status",((PowerManager)activity.getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus());
            report.put("charge_counter_uah",((BatteryManager)activity.getSystemService(Context.BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
        }finally{
            main(()->{if(window[0]!=null){Window old=window[0];window[0]=null;try{old.removeOnFrameMetricsAvailableListener(listener);}catch(IllegalArgumentException alreadyDetached){}}restore(prefs,original);assertTrue("All preferences must be preserved",original.equals(prefs.getAll()));if(activity!=null&&!activity.isDestroyed()){activity.editor.setText("");activity.setRequestedOrientation(orientation);}});
            report.put("all_preferences_unchanged",original.equals(prefs.getAll()));Bundle result=new Bundle();result.putString("petal_physical_result",report.toString());InstrumentationRegistry.getInstrumentation().addResults(result);
        }
    }
}
