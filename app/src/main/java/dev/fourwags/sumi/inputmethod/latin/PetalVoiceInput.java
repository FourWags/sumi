package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
/** Exclusively platform on-device recognition. No general/network recognizer or model downloads. */
public final class PetalVoiceInput {
    private final LatinIME ime;
    private final PetalDictationSession gate=new PetalDictationSession();
    interface Engine {void start(Intent request,RecognitionListener listener);void stop();void close();}
    interface Factory {boolean available(Context context);boolean permitted(Context context);Engine create(Context context);}
    private static final Factory PLATFORM=new Factory(){
        public boolean available(Context context){return PetalVoiceInput.available(context);}
        public boolean permitted(Context context){return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
        public Engine create(Context context){
            if(Build.VERSION.SDK_INT<31)throw new UnsupportedOperationException();
            SpeechRecognizer speech=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            return new Engine(){
                public void start(Intent intent,RecognitionListener listener){speech.setRecognitionListener(listener);speech.startListening(intent);}
                public void stop(){speech.stopListening();}
                public void close(){try{speech.cancel();}finally{speech.destroy();}}
            };
        }
    };
    private final Factory factory;
    private Engine recognizer;
    private PopupWindow popup;
    private TextView status;
    private boolean stopping;
    private boolean audioEnded;
    private boolean audioEndedByProvider;
    private PetalVoiceTiming timing;
    private int selectedSilence;
    private String lastPartial = "";
    private boolean segmentSpeech;
    private final Runnable silenceCheck = new Runnable() {
        @Override public void run() {
            if (!gate.active() || stopping) return;
            if (!validSession()) { cancel(); return; }
            if (timing.expired(SystemClock.uptimeMillis())) stop();
            else handler.postDelayed(this, 100);
        }
    };
    private long ticket, segment;
    private Object sessionEditor;
    private java.lang.ref.WeakReference<View> requestAnchor;
    // Text exists only until Stop/Cancel/editor change; never written to storage.
    private StringBuilder completed = new StringBuilder();
    private final Runnable resume = () -> { if (validSession()) startSegment(); else cancel(); };
    private PetalVoiceWaveform waveform;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->{stop();message(R.string.petal_voice_timeout);};
    private final Runnable finalizeTimeout=()->{if(gate.active())complete();};
    public PetalVoiceInput(LatinIME ime){this(ime,PLATFORM);}
    PetalVoiceInput(LatinIME ime,Factory factory){this.ime=ime;this.factory=factory;}
    public static boolean available(Context context){
        if(Build.VERSION.SDK_INT<31)return false;
        try{return SpeechRecognizer.isOnDeviceRecognitionAvailable(context);}catch(RuntimeException e){return false;}
    }
    public static void permission(Context context){if(!available(context)){Toast.makeText(context,R.string.petal_voice_unavailable,Toast.LENGTH_LONG).show();return;}context.startActivity(new Intent(context,PetalVoicePermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public void toggle(View anchor){
        if(gate.active()){stop();return;}
        startHold(anchor);
    }
    public void startHold(View anchor){
        if(gate.active())return;
        cancel(); // Dismiss a previous error notice before starting a new session.
        if(!ime.petalMediaAllowed()||ime.getCurrentInputConnection()==null||!ime.isInputViewShown())return;
        if(!factory.available(ime)){notice(anchor,R.string.petal_voice_unavailable);return;}
        if(!factory.permitted(ime)){permission(ime);return;}
        sessionEditor=ime.getCurrentInputConnection();ticket=gate.begin(sessionEditor,true);
        requestAnchor=new java.lang.ref.WeakReference<>(anchor);
        try {
            showPanel(anchor);
            selectedSilence = dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils
                    .getSharedPreferences(ime).getInt(PetalVoiceTiming.SILENCE, 2000);
            timing = new PetalVoiceTiming(selectedSilence, SystemClock.uptimeMillis());
            handler.postDelayed(silenceCheck, 100);
            // One user activation has a bounded lifetime, including provider restarts.
            handler.postDelayed(timeout,60000);
            startSegment();
        }catch(RuntimeException e){notice(anchor,R.string.petal_voice_failed);}
    }
    private boolean validSession(){
        View anchor=requestAnchor==null?null:requestAnchor.get();
        return gate.isCurrent(ticket) && sessionEditor==ime.getCurrentInputConnection()
                && ime.petalMediaAllowed() && ime.isInputViewShown()
                && anchor!=null && anchor.isAttachedToWindow();
    }
    private boolean current(long turn){
        if(!gate.isCurrent(ticket)||turn!=segment)return false;
        if(!validSession()){cancel();return false;}
        return true;
    }
    private void startSegment(){
        if(!validSession()||stopping){cancel();return;}
        final long turn=++segment;
        audioEnded=false;audioEndedByProvider=false;lastPartial="";segmentSpeech=false;
        status.setText(R.string.petal_voice_connecting);
        try {
            // Deliberately never use createSpeechRecognizer, which permits network recognition.
            recognizer=factory.create(ime);
            RecognitionListener listener=new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){if(current(turn)&&!stopping&&!audioEnded){waveform.setListening(true);status.setText(R.string.petal_voice_listening);}}
                public void onBeginningOfSpeech(){if(current(turn)&&!stopping&&!audioEnded){if(!segmentSpeech){timing.speech(SystemClock.uptimeMillis());segmentSpeech=true;}waveform.setListening(true);status.setText(R.string.petal_voice_speech);}}
                public void onRmsChanged(float rms){if(current(turn)&&!stopping&&!audioEnded){timing.level(rms,SystemClock.uptimeMillis());waveform.reportLevel(rms);}}
                public void onBufferReceived(byte[] data){}public void onEvent(int type,Bundle b){}
                public void onEndOfSpeech(){if(current(turn)&&!audioEnded){
                    // This callback already includes the provider's own pause; do not count it twice.
                    audioEndedByProvider=true;finishListening();status.setText(stopping?R.string.petal_voice_finishing:R.string.petal_voice_resuming);
                }}
                public void onError(int error){
                    if(!current(turn))return;
                    if(error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT||error==SpeechRecognizer.ERROR_NO_MATCH){
                        if(stopping)complete();else resumeAfterSegment();
                        return;
                    }
                    fail(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?R.string.petal_voice_denied:(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)?R.string.petal_voice_unavailable:R.string.petal_voice_failed);
                }
                public void onPartialResults(Bundle results){
                    if(!current(turn)||stopping||audioEnded||results==null)return;
                    ArrayList<String> words=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(words!=null&&!words.isEmpty()&&words.get(0)!=null&&words.get(0).length()<=10000){
                        String partial=words.get(0).trim();
                        if(!partial.isEmpty()&&!partial.equals(lastPartial)){
                            timing.speech(SystemClock.uptimeMillis());segmentSpeech=true;lastPartial=partial;
                        }
                        status.setText(words.get(0));
                    }
                }
                public void onResults(Bundle results){
                    if(!current(turn))return;
                    ArrayList<String> words=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text=words==null||words.isEmpty()?null:words.get(0);
                    if(text!=null&&!text.trim().isEmpty()){
                        if(!segmentSpeech&&!stopping)timing.speech(SystemClock.uptimeMillis());
                        int separator=completed.length()==0?0:1;
                        if(text.length()>10000||completed.length()+separator+text.length()>10000){fail(R.string.petal_voice_limit);return;}
                        if(separator!=0)completed.append(' ');
                        completed.append(text.trim());
                    }
                    if(stopping)complete();else resumeAfterSegment();
                }
            };
            Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                    // The watchdog enforces the preference even if the provider ignores this hint.
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                            (long)PetalVoiceTiming.silenceMillis(selectedSilence));
            if(Build.VERSION.SDK_INT>=33)request.putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT,false);
            recognizer.start(request,listener);
        }catch(RuntimeException e){fail(R.string.petal_voice_failed);}
    }
    private void resumeAfterSegment(){
        retireEngine();finishListening();status.setText(R.string.petal_voice_resuming);
        handler.removeCallbacks(resume);
        // Never loop synchronously or continuously retry provider/permission errors.
        handler.postDelayed(resume,750);
    }
    private void retireEngine(){
        ++segment;Engine old=recognizer;recognizer=null;
        if(old!=null){try{old.close();}catch(RuntimeException ignored){}}
    }
    private void fail(int text){
        View target=requestAnchor==null?null:requestAnchor.get();
        if(target!=null&&target.isAttachedToWindow())notice(target,text);else{cancel();message(text);}
    }
    private void complete(){
        String text=gate.finish(ticket,ime.getCurrentInputConnection(),validSession(),completed.toString());
        cleanup();if(text!=null)ime.petalPaste(text);
    }
    public void stop(){
        if(!gate.active()||stopping)return;
        if(!validSession()){cancel();return;}
        handler.postDelayed(finalizeTimeout,5000);
        stopping=true;handler.removeCallbacks(silenceCheck);handler.removeCallbacks(resume);finishListening();status.setText(R.string.petal_voice_finishing);
        if(recognizer==null){complete();return;}
        // onEndOfSpeech already ended audio; wait for its final result instead.
        if(audioEndedByProvider)return;
        try{recognizer.stop();}catch(RuntimeException e){fail(R.string.petal_voice_failed);}
    }
    public void cancel(){gate.cancel();cleanup();}
    private void finishListening(){audioEnded=true;if(waveform!=null)waveform.setListening(false);}
    private void cleanup(){
        handler.removeCallbacks(silenceCheck);handler.removeCallbacks(resume);handler.removeCallbacks(timeout);handler.removeCallbacks(finalizeTimeout);retireEngine();
        finishListening();waveform=null;audioEnded=false;audioEndedByProvider=false;stopping=false;
        sessionEditor=null;requestAnchor=null;timing=null;lastPartial="";segmentSpeech=false;completed=new StringBuilder();
        PopupWindow oldPopup=popup;popup=null;
        if(oldPopup!=null){oldPopup.setOnDismissListener(null);oldPopup.dismiss();}
        if(status!=null)status.setText("");status=null;
    }
    private int dp(int n){return Math.round(n*ime.getResources().getDisplayMetrics().density);}
    private int color(int attr){android.util.TypedValue v=new android.util.TypedValue();dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher.getInstance().getMainKeyboardView().getContext().getTheme().resolveAttribute(attr,v,true);return v.data;}
    private void notice(View anchor,int text){cancel();try{showPanel(anchor,false,text);}catch(RuntimeException e){cleanup();message(text);}}
    private void showPanel(View anchor){showPanel(anchor,true,R.string.petal_voice_connecting);}
    private void showPanel(View anchor,boolean listening,int initialText){
        LinearLayout panel=new LinearLayout(ime);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),dp(10),dp(8));panel.setBackgroundColor(color(R.attr.petalSurface));
        status=new TextView(ime);status.setText(initialText);status.setTextColor(color(R.attr.petalStripInk));status.setTextSize(16);status.setMaxLines(listening?1:4);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setMinHeight(dp(32));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(status);
        if(listening){waveform=new PetalVoiceWaveform(ime,color(R.attr.petalStripInk));panel.addView(waveform,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));}
        LinearLayout actions=new LinearLayout(ime);panel.addView(actions);
        for(boolean stop:new boolean[]{true,false}){if(stop&&!listening)continue;Button b=new Button(ime);b.setText(stop?R.string.petal_voice_stop:listening?android.R.string.cancel:android.R.string.ok);b.setMinHeight(dp(48));b.setTextColor(color(R.attr.petalInk));b.setAllCaps(false);b.setBackground(PetalUi.control(ime,color(R.attr.petalFunction),color(R.attr.petalFunctionOutline),color(R.attr.petalInk)));b.setElevation(0);b.setStateListAnimator(null);actions.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));b.setOnClickListener(v->{if(stop)stop();else cancel();});}
        int width=Math.min(dp(360),ime.getWindow().getWindow().getDecorView().getWidth()-dp(8));popup=new PopupWindow(panel,width,ViewGroup.LayoutParams.WRAP_CONTENT,false);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color(R.attr.petalSurface)));popup.setOutsideTouchable(false);popup.setOnDismissListener(this::cancel);popup.showAtLocation(anchor,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(8));
    }
    private void message(int text){Toast.makeText(ime,text,Toast.LENGTH_LONG).show();}
}
