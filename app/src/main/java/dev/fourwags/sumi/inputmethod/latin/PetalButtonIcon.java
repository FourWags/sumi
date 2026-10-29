package dev.fourwags.sumi.inputmethod.latin;

import android.content.SharedPreferences;
import dev.fourwags.sumi.R;

/** Independent, local-only tools-button choice. No theme or launcher migration. */
public final class PetalButtonIcon {
    public static final String PREFERENCE = "petal_tools_dog";
    public static final String[] NAMES = {"Bright & curious", "Happy companion", "Gentle soul"};
    private static final int[] DRAWABLES = {R.drawable.petal_dog_bright, R.drawable.petal_dog_happy, R.drawable.petal_dog_gentle};
    private PetalButtonIcon() {}
    public static int selected(SharedPreferences prefs) {
        Object value = prefs.getAll().get(PREFERENCE);
        return value instanceof Integer ? bounded((Integer) value) : 0;
    }
    public static int bounded(int value) { return value >= 0 && value < DRAWABLES.length ? value : 0; }
    public static int drawable(int value) { return DRAWABLES[bounded(value)]; }
    public static void select(SharedPreferences prefs, int value) {
        if (value < 0 || value >= DRAWABLES.length) throw new IllegalArgumentException("Unknown keyboard button");
        prefs.edit().putInt(PREFERENCE, value).apply();
    }
}
