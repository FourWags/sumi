package dev.fourwags.sumi.inputmethod.latin;
import android.content.Context;
import android.content.res.Configuration;
/** QA-only Settings host. Changes only its own configuration, never device settings. */
public final class PetalLargeFontSettingsActivity extends PetalSettingsActivity {
    @Override protected void attachBaseContext(Context base) {
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.fontScale=2f;
        super.attachBaseContext(base.createConfigurationContext(config));
    }
}
