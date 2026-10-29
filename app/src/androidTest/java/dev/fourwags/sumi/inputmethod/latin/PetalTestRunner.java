package dev.fourwags.sumi.inputmethod.latin;

import android.app.Activity;
import android.content.Intent;
import androidx.test.runner.AndroidJUnitRunner;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;

/** Fixtures are loaded by instrumentation only; none is shipped in the target APK. */
public final class PetalTestRunner extends AndroidJUnitRunner {
    private static final String FIXTURE = "petal.instrumentation.fixture";

    @Override public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        // Keep only the foreground test window awake. Never change device
        // timeout/security settings or dismiss an existing keyguard.
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override public void callActivityOnPause(Activity activity) {
        activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.callActivityOnPause(activity);
    }

    static Intent fixtureIntent(String kind) {
        return new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                PetalSettingsActivity.class).putExtra(FIXTURE, kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static ActivityTestRule<PetalTestActivity> editorRule() {
        return new ActivityTestRule<PetalTestActivity>(PetalTestActivity.class) {
            @Override protected Intent getActivityIntent() { return fixtureIntent("editor"); }
        };
    }

    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (PetalSettingsActivity.class.getName().equals(name)) {
            String kind = intent.getStringExtra(FIXTURE);
            if ("editor".equals(kind)) {
                return super.newActivity(PetalTestActivity.class.getClassLoader(),
                        PetalTestActivity.class.getName(), intent);
            }
            if ("large-font".equals(kind)) {
                return super.newActivity(PetalLargeFontSettingsActivity.class.getClassLoader(),
                        PetalLargeFontSettingsActivity.class.getName(), intent);
            }
        }
        return super.newActivity(loader, name, intent);
    }
}
