package dev.fourwags.sumi.inputmethod.latin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Transient recognizer-reported audio level. No audio buffers, timers or idle animation. */
// Constructed in code with the current keyboard palette; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class PetalVoiceWaveform extends View {
    private static final float[] HEIGHTS = {0.35f, 0.6f, 0.85f, 1f, 0.85f, 0.6f, 0.35f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean listening;
    private float level;

    PetalVoiceWaveform(Context context, int ink) {
        super(context);
        paint.setColor(ink);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        setClickable(false);
    }

    void setListening(boolean value) {
        listening = value;
        level = 0;
        invalidate();
    }

    void reportLevel(float rms) {
        if (!listening) return;
        // Provider scales vary. This is a relative level, not a calibrated sound meter.
        level = Float.isNaN(rms) || Float.isInfinite(rms)
                ? 0 : Math.max(0, Math.min(1, (rms + 2f) / 12f));
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = Math.min(getWidth(), 160 * density);
        float step = width / HEIGHTS.length;
        float barWidth = Math.min(10 * density, step * 0.55f);
        float baseline = Math.min(getHeight(), 4 * density);
        float usable = Math.max(0, getHeight() - 8 * density - baseline);
        float left = (getWidth() - width) / 2;
        paint.setAlpha(listening ? 255 : 100);
        for (int i = 0; i < HEIGHTS.length; i++) {
            float height = baseline + usable * level * HEIGHTS[i];
            float x = left + step * (i + 0.5f);
            canvas.drawRoundRect(x - barWidth / 2, (getHeight() - height) / 2,
                    x + barWidth / 2, (getHeight() + height) / 2,
                    barWidth / 2, barWidth / 2, paint);
        }
    }
}
