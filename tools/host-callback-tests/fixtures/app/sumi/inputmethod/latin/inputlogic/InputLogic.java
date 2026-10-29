package dev.fourwags.sumi.inputmethod.latin.inputlogic;
import dev.fourwags.sumi.inputmethod.latin.SuggestedWords;
import dev.fourwags.sumi.inputmethod.latin.common.InputPointers;
public final class InputLogic {
 public final Composer mWordComposer=new Composer();
 public SuggestedWords mSuggestedWords=new SuggestedWords(0);
 public static final class Composer {public void setBatchInputPointers(InputPointers pointers) {}}
}
