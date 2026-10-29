package dev.fourwags.sumi.inputmethod.latin.inputlogic;
import dev.fourwags.sumi.inputmethod.latin.*;
import dev.fourwags.sumi.inputmethod.latin.common.InputPointers;
/** Executes the actual production InputLogicHandler against deterministic test collaborators. */
public final class CallbackRegression {
 interface Check {void run();}
 static int failures;
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void test(String name,Check body){try{body.run();System.out.println("PASS "+name);}catch(Throwable error){failures++;System.out.println("FAIL "+name+": "+error);}}
 static final class Fixture {
  final LatinIME ime=new LatinIME();final InputLogic logic=new InputLogic();
  final InputLogicHandler handler=new InputLogicHandler(ime,logic);
  Suggest.OnGetSuggestedWordsCallback request(boolean tail){
   if(tail)handler.updateTailBatchInput(new InputPointers(),1);else handler.onUpdateBatchInput(new InputPointers(),1);
   handler.mNonUIThreadHandler.runAll();return ime.callbacks.get(ime.callbacks.size()-1);
  }
  void empty(){check(ime.mHandler.previews.isEmpty()&&ime.mHandler.tails.isEmpty(),"stale result reached UI");}
 }
 public static void main(String[] args){
  test("live_partial",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();f.request(false).onGetSuggestedWords(new SuggestedWords(1));check(f.ime.mHandler.previews.size()==1&&f.ime.mHandler.tails.isEmpty()&&f.handler.isInBatchInput(),"live partial lost");});
  test("live_tail",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();f.request(true).onGetSuggestedWords(new SuggestedWords(1));check(f.ime.mHandler.previews.size()==1&&f.ime.mHandler.tails.size()==1&&!f.handler.isInBatchInput(),"live tail lost");});
  test("cancel_drops_late_result",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();Suggest.OnGetSuggestedWordsCallback old=f.request(false);f.handler.onCancelBatchInput();old.onGetSuggestedWords(new SuggestedWords(2));f.empty();});
  test("reset_drops_late_result",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();Suggest.OnGetSuggestedWordsCallback old=f.request(false);f.handler.reset();old.onGetSuggestedWords(new SuggestedWords(2));f.empty();});
  test("reset_ends_batch",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();f.handler.reset();check(!f.handler.isInBatchInput(),"reset retained active batch");});
  test("old_result_cannot_enter_new_batch",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();Suggest.OnGetSuggestedWordsCallback old=f.request(false);f.handler.reset();f.handler.onStartBatchInput();old.onGetSuggestedWords(new SuggestedWords(2));f.empty();f.request(false).onGetSuggestedWords(new SuggestedWords(3));check(f.ime.mHandler.previews.size()==1&&f.ime.mHandler.previews.get(0).marker==3,"new result lost");});
  test("old_tail_cannot_finish_new_batch",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();Suggest.OnGetSuggestedWordsCallback old=f.request(true);f.handler.onCancelBatchInput();f.handler.onStartBatchInput();old.onGetSuggestedWords(new SuggestedWords(2));f.empty();check(f.handler.isInBatchInput(),"old tail ended new batch");f.request(true).onGetSuggestedWords(new SuggestedWords(3));check(f.ime.mHandler.tails.size()==1,"current tail lost");});
  test("destroy_drops_late_result",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();Suggest.OnGetSuggestedWordsCallback old=f.request(true);f.handler.destroy();old.onGetSuggestedWords(new SuggestedWords(2));f.empty();});
  test("duplicate_tail_is_ignored",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();Suggest.OnGetSuggestedWordsCallback cb=f.request(true);cb.onGetSuggestedWords(new SuggestedWords(2));cb.onGetSuggestedWords(new SuggestedWords(2));check(f.ime.mHandler.tails.size()==1&&f.ime.mHandler.previews.size()==1,"tail applied twice");});
  test("reset_removes_queued_request",()->{Fixture f=new Fixture();f.handler.onStartBatchInput();f.handler.onUpdateBatchInput(new InputPointers(),1);f.handler.reset();f.handler.mNonUIThreadHandler.runAll();check(f.ime.callbacks.isEmpty(),"queued request survived reset");});
  System.out.println("cases=10 failures="+failures);if(failures!=0)System.exit(1);
 }
}
