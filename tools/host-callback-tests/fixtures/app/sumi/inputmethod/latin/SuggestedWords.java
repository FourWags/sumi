package dev.fourwags.sumi.inputmethod.latin;
public final class SuggestedWords {
 public static final int INPUT_STYLE_TAIL_BATCH=1,INPUT_STYLE_UPDATE_BATCH=2;
 public final int marker;
 public SuggestedWords(int marker){this.marker=marker;}
 public boolean isEmpty(){return marker==0;}
}
