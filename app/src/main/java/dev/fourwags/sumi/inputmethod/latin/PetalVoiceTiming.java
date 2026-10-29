package dev.fourwags.sumi.inputmethod.latin;

/** Monotonic, memory-only endpoint policy. Never receives audio or transcript text. */
public final class PetalVoiceTiming {
    public static final String SILENCE = "petal_voice_silence_ms";
    public static final long INITIAL_WAIT_MS = 10000;
    private final long silenceMs;
    private long lastActivity, loudSince = -1;
    private boolean speech;
    private float noiseFloor = Float.POSITIVE_INFINITY;
    public static int silenceMillis(int value) { return value == 4000 || value == 8000 ? value : 2000; }
    public PetalVoiceTiming(int selected, long now) {
        silenceMs = silenceMillis(selected); lastActivity = now;
    }
    public void speech(long now) {
        // A provider may send its first RMS sample only after speech begins. Do not learn
        // that first spoken level as the background floor and cut off a continuing phrase.
        if (!Float.isFinite(noiseFloor)) noiseFloor = 0f;
        speech = true; lastActivity = now;
    }
    public void level(float rms, long now) {
        if (!Float.isFinite(rms)) return;
        noiseFloor = Math.min(noiseFloor, rms);
        // RMS is provider-relative. Reject steady background levels and isolated spikes.
        // Only extend established speech; noise cannot extend the initial speech wait.
        if (!speech || rms < Math.max(3f, noiseFloor + 3f)) { loudSince = -1; return; }
        if (loudSince < 0) loudSince = now;
        if (now - loudSince >= 150) lastActivity = now;
    }
    public boolean expired(long now) { return now - lastActivity >= (speech ? silenceMs : INITIAL_WAIT_MS); }
}
