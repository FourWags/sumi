package dev.fourwags.sumi.inputmethod.latin;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalTypingOptionsTest {
    @Test public void invalidPreviewOptionsFallBackSafely(){for(int n:new int[]{Integer.MIN_VALUE,-1,0,99,101,Integer.MAX_VALUE})assertEquals(100,PetalTypingOptions.previewSize(n));for(int n:new int[]{Integer.MIN_VALUE,-1,1,999,Integer.MAX_VALUE})assertEquals(0,PetalTypingOptions.previewDuration(n));}
    @Test public void supportedOptionsRemainExact(){assertEquals(125,PetalTypingOptions.previewSize(125));assertEquals(150,PetalTypingOptions.previewSize(150));assertEquals(120,PetalTypingOptions.previewDuration(120));assertEquals(250,PetalTypingOptions.previewDuration(250));}
}
