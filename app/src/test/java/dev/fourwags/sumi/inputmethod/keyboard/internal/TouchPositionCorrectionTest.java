package dev.fourwags.sumi.inputmethod.keyboard.internal;
import org.junit.Test;
import static org.junit.Assert.*;
public class TouchPositionCorrectionTest {
    @Test public void emptyTabletCalibrationDisablesOffsets() {
        TouchPositionCorrection c=new TouchPositionCorrection();
        c.load(new String[]{"0","0.1","0.2"});assertTrue(c.isValid());
        c.load(new String[0]);assertFalse(c.isValid());assertEquals(0,c.getRows());
    }
    @Test public void invalidReloadCannotRetainOldCalibration() {
        for(String[] bad:new String[][]{{"0","0.1"},{"0","oops","0.2"},{"0","NaN","0.2"},{"0","0.1","Infinity"},{"0","0.1","-0.2"}}) {
            TouchPositionCorrection c=new TouchPositionCorrection();c.load(new String[]{"0","0.1","0.2"});
            c.load(bad);assertFalse(c.isValid());assertEquals(0,c.getRows());
        }
    }
}
