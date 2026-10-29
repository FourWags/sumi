package dev.fourwags.sumi.inputmethod.keyboard;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalMoreKeysTest {
    @Test public void enterWhitespaceAndActionCodesHaveNoAlternatives() {
        for(int code=-1000;code<=32;code++)assertNull(PetalMoreKeys.specsFor(code));
        assertNull(PetalMoreKeys.specsFor(0x1f600));
    }
    @Test public void lettersKeepNumberAndAccentConventions() {
        assertEquals("1",PetalMoreKeys.charactersFor('q'));
        assertEquals("3èéêëēę",PetalMoreKeys.charactersFor('e'));
        assertEquals(PetalMoreKeys.charactersFor('e'),PetalMoreKeys.charactersFor('E'));
        assertTrue(PetalMoreKeys.charactersFor('n').contains("ñ"));
    }
    @Test public void alternativesContainOnlyTextAndEscapeSpecSeparators() {
        for(int base=33;base<128;base++) {
            String text=PetalMoreKeys.charactersFor(base);
            if(text==null)continue;
            for(int cp:text.codePoints().toArray()) {
                assertTrue(cp>32 && cp<0x2500);assertFalse(Character.isISOControl(cp));
                assertFalse(Character.isWhitespace(cp));
            }
        }
        assertArrayEquals(new String[]{"\\\\"},PetalMoreKeys.specsFor('/'));
    }
}
