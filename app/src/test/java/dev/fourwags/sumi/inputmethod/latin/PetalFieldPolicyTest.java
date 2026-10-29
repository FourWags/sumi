package dev.fourwags.sumi.inputmethod.latin;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalFieldPolicyTest {
    @Test public void everydayTextIsAllowed() { for(int v:new int[]{0,0x20,0x30,0x40}) assertFalse(PetalFieldPolicy.isProtected(1|v|0x8000,0,null)); }
    @Test public void allPasswordTypesAreProtected() { for(int type:new int[]{0x81,0x91,0xe1,0x12})assertTrue(PetalFieldPolicy.isProtected(type,0,null)); }
    @Test public void structuredFieldsAreProtected() { for(int v:new int[]{0x10,0x60,0x70,0xa0,0xb0,0xc0,0xd0,0xf0})assertTrue(PetalFieldPolicy.isProtected(1|v|0x8000,0,null)); }
    @Test public void numericAndUnknownFieldsAreProtected() { for(int c:new int[]{0,2,3,4,5,15})assertTrue(PetalFieldPolicy.isProtected(c,0,null)); }
    @Test public void noSuggestionAndAppCompletionFlagsWin() { assertTrue(PetalFieldPolicy.isProtected(1|0x80000|0x8000,0,null));assertTrue(PetalFieldPolicy.isProtected(1|0x10000,0,null)); }
    @Test public void noLearningFlagWins() { assertTrue(PetalFieldPolicy.isProtected(1|0x8000,0x1000000,null)); }
    @Test public void privateAndCodeOptionsWin() { for(String s:new String[]{"incognito","app.private","noLearning","noPersonalizedLearning","code","CODE"})assertTrue(PetalFieldPolicy.isProtected(1|0x8000,0,s)); }
}
