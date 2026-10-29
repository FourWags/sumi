package dev.fourwags.sumi.inputmethod.latin;
import java.util.ArrayList;
public final class LatinIME {
 public final UI mHandler=new UI();
 public final ArrayList<Suggest.OnGetSuggestedWordsCallback> callbacks=new ArrayList<>();
 public void getSuggestedWords(int style,int sequence,Suggest.OnGetSuggestedWordsCallback callback){callbacks.add(callback);}
 public static final class UI {
  public final ArrayList<SuggestedWords> previews=new ArrayList<>(),tails=new ArrayList<>();
  public void showGesturePreviewAndSuggestionStrip(SuggestedWords words,boolean tail){previews.add(words);}
  public void showTailBatchInputResult(SuggestedWords words){tails.add(words);}
 }
}
