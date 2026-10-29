package dev.fourwags.sumi.inputmethod.latin;

/** Bounded local UI preferences. No text or audio is stored here. */
public final class PetalTypingOptions {
    private PetalTypingOptions() {}
    public static final String PREVIEW_SIZE="petal_preview_size", PREVIEW_DURATION="petal_preview_duration";
    public static final String SUGGESTION_SIZE="petal_suggestion_size";
    public static int suggestionSize(int value){return value==100||value==150?value:125;}
    public static int suggestionTextSp(int value){return 18*suggestionSize(value)/100;}
    public static int suggestionHeightDp(int value){return 48*suggestionSize(value)/100;}
    public static int previewSize(int value){return value==125||value==150?value:100;}
    public static int previewDuration(int value){return value==120||value==250?value:0;}
}
