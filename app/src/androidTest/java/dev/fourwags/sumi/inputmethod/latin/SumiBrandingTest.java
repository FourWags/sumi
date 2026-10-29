package dev.fourwags.sumi.inputmethod.latin;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.fourwags.sumi.R;
import org.junit.Test;
import static org.junit.Assert.*;

/** System App info must use Sumi artwork even before the launcher picker is opened. */
public class SumiBrandingTest {
    private Bitmap render(Drawable drawable) {
        Bitmap bitmap=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,108,108);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
    @Test public void systemApplicationIconUsesApprovedSumiArtwork() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ApplicationInfo info=context.getPackageManager().getApplicationInfo(context.getPackageName(),0);
        assertEquals(R.mipmap.fw_sumi_c,info.icon);
        Bitmap actual=render(context.getPackageManager().getApplicationIcon(info));
        Bitmap expected=render(context.getDrawable(R.mipmap.fw_sumi_c));
        try { assertTrue("App info artwork must match the approved Sumi icon",actual.sameAs(expected)); }
        finally { actual.recycle(); expected.recycle(); }
    }
}
