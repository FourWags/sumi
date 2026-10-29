package dev.fourwags.sumi.inputmethod.latin;
import org.junit.Test;import static org.junit.Assert.*;
public class PetalDictationSessionTest {
 @Test public void protectedFieldCannotStart(){PetalDictationSession g=new PetalDictationSession();assertEquals(-1,g.begin(new Object(),false));assertFalse(g.active());}
 @Test public void absentEditorCannotStart(){assertEquals(-1,new PetalDictationSession().begin(null,true));}
 @Test public void resultIsAcceptedExactlyOnce(){PetalDictationSession g=new PetalDictationSession();Object editor=new Object();long t=g.begin(editor,true);assertEquals("A synthetic phrase.",g.finish(t,editor,true,"A synthetic phrase."));assertNull(g.finish(t,editor,true,"duplicate"));}
 @Test public void cancelDropsLateResult(){PetalDictationSession g=new PetalDictationSession();Object e=new Object();long t=g.begin(e,true);g.cancel();assertNull(g.finish(t,e,true,"late"));}
 @Test public void oldCallbackCannotConsumeNewSession(){PetalDictationSession g=new PetalDictationSession();Object e=new Object();long old=g.begin(e,true);long current=g.begin(e,true);assertFalse(g.isCurrent(old));assertNull(g.finish(old,e,true,"old"));assertTrue(g.isCurrent(current));assertEquals("current",g.finish(current,e,true,"current"));}
 @Test public void editorSwitchDiscardsResult(){PetalDictationSession g=new PetalDictationSession();long t=g.begin(new Object(),true);assertNull(g.finish(t,new Object(),true,"wrong editor"));assertFalse(g.active());}
 @Test public void protectedTransitionDiscardsResult(){PetalDictationSession g=new PetalDictationSession();Object e=new Object();long t=g.begin(e,true);assertNull(g.finish(t,e,false,"protected"));}
 @Test public void blankOrOversizedResultsNeverInsert(){for(String result:new String[]{null," ",new String(new char[10001]).replace('\0','x')}){PetalDictationSession g=new PetalDictationSession();Object e=new Object();long t=g.begin(e,true);assertNull(g.finish(t,e,true,result));}}
}
