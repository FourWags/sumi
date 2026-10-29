package dev.fourwags.sumi.inputmethod.latin;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalClipboardPreviewTest {
    @Test public void nullIsEmpty(){assertEquals("",PetalClipboardPreview.visible(null));}
    @Test public void normalTextUnchanged(){assertEquals("synthetic text",PetalClipboardPreview.visible("synthetic text"));}
    @Test public void oversizedPreviewIsBounded(){String text=new String(new char[10000]).replace('\0','x');assertEquals(301,PetalClipboardPreview.visible(text).length());assertTrue(PetalClipboardPreview.visible(text).endsWith("…"));assertEquals(10000,text.length());}
    @Test public void truncationDoesNotSplitSurrogatePair(){String text=new String(new char[299]).replace('\0','x')+"\uD83C\uDF38more";String preview=PetalClipboardPreview.visible(text);assertEquals(300,preview.length());assertFalse(Character.isHighSurrogate(preview.charAt(298)));}
}
