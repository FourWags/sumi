package dev.fourwags.sumi.inputmethod.latin;

/** Orientation-specific height policy. Percentages are relative to the unchanged layout baseline. */
public final class SumiKeyboardHeight {
    private SumiKeyboardHeight() {}
    public static final String PORTRAIT="petal_height_portrait", LANDSCAPE="petal_height_landscape";
    public static final int MIN=90, MAX=130, DEFAULT=100;
    public static int clamp(int percent) { return Math.max(MIN,Math.min(MAX,percent)); }
    public static int pixels(int baseline,int percent,int screenHeight,int systemInsets,float density) {
        int value=clamp(percent);
        if(value==DEFAULT)return baseline; // Preserve the exact existing geometry at reset/default.
        int available=Math.max(1,screenHeight-Math.max(0,systemInsets));
        // Reserve the suggestion row and at least a quarter of usable height for the editor.
        int maximum=Math.max(baseline,Math.round(available*.75f-40*density));
        return Math.max(1,Math.min(Math.round(baseline*value/100f),maximum));
    }
}
