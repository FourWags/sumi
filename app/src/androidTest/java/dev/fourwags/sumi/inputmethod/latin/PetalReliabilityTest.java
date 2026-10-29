package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.view.inputmethod.InputMethodSubtype;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.fourwags.sumi.inputmethod.compat.InputMethodSubtypeCompatUtils;
import dev.fourwags.sumi.inputmethod.compat.UserManagerCompatUtils;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardLayoutSet;
import dev.fourwags.sumi.inputmethod.keyboard.internal.BundledKeyboardResources;
import dev.fourwags.sumi.inputmethod.keyboard.internal.KeyboardTextsSet;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
import dev.fourwags.sumi.inputmethod.latin.utils.DictionaryInfoUtils;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetalReliabilityTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void resourceContractsAreAvailableAndUnknownNamesAreSafe() throws Exception {
        assertEquals(R.xml.keyboard_layout_set_qwerty, BundledKeyboardResources.layout("keyboard_layout_set_qwerty"));
        assertEquals(R.string.subtype_en_US, BundledKeyboardResources.string("string/subtype_en_US"));
        assertEquals(0, BundledKeyboardResources.layout("keyboard_layout_set_missing_synthetic"));
        java.lang.reflect.Method lookup=KeyboardLayoutSet.Builder.class.getDeclaredMethod("getXmlId",android.content.res.Resources.class,String.class);
        lookup.setAccessible(true);
        assertEquals(R.xml.keyboard_layout_set_qwerty, ((Integer)lookup.invoke(null,context.getResources(),"keyboard_layout_set_missing_synthetic")).intValue());
        KeyboardTextsSet texts=new KeyboardTextsSet();texts.setLocale(Locale.US,context);
        assertEquals(context.getString(R.string.label_done_key),texts.resolveTextReference("!string/label_done_key"));
        assertNull(texts.resolveTextReference("!string/missing_synthetic_label"));
        assertEquals(R.raw.main_en,DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(context.getResources(),Locale.US));
        assertEquals(0,DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(context.getResources(),Locale.FRENCH));
        assertEquals(R.raw.main,DictionaryInfoUtils.getMainDictionaryResourceId(context.getResources(),Locale.FRENCH));
    }

    @Test public void subtypeLocaleUsesPublicTagAndLegacyFallback() {
        InputMethodSubtype tagged=new InputMethodSubtype.InputMethodSubtypeBuilder().setLanguageTag("en-US").setSubtypeLocale("fr_FR").build();
        InputMethodSubtype legacy=new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeLocale("en_US").build();
        assertEquals(Locale.US,InputMethodSubtypeCompatUtils.getLocaleObject(tagged));
        assertEquals(Locale.US,InputMethodSubtypeCompatUtils.getLocaleObject(legacy));
        assertEquals(context.getSystemService(UserManager.class).isUserUnlocked()
                ? UserManagerCompatUtils.LOCK_STATE_UNLOCKED : UserManagerCompatUtils.LOCK_STATE_LOCKED,
                UserManagerCompatUtils.getUserLockState(context));
    }

    @Test public void localeJobNeverMutatesSharedConfigurationEvenOnFailure() {
        android.content.res.Resources shared=context.getResources();
        android.content.res.Configuration original=new android.content.res.Configuration(shared.getConfiguration());
        Locale requested=Locale.FRANCE;
        dev.fourwags.sumi.inputmethod.latin.utils.RunInLocale<String> job=new dev.fourwags.sumi.inputmethod.latin.utils.RunInLocale<String>() {
            @Override protected String job(android.content.res.Resources local) {
                assertEquals(original,shared.getConfiguration());
                assertEquals(requested,local.getConfiguration().getLocales().get(0));
                throw new IllegalStateException("synthetic locale job failure");
            }
        };
        try { job.runInLocale(context,requested); fail("Expected synthetic failure"); }
        catch (IllegalStateException expected) { assertEquals("synthetic locale job failure",expected.getMessage()); }
        assertEquals(original,shared.getConfiguration());
    }

    /** A separate preferences name prevents the migration regression from changing user settings. */
    private static final class MigrationContext extends ContextWrapper {
        MigrationContext(Context base) { super(base); }
        @Override public String getPackageName() { return super.getPackageName()+".synthetic_migration"; }
        @Override public Context createDeviceProtectedStorageContext() {
            return new MigrationContext(super.createDeviceProtectedStorageContext());
        }
    }
    @Test public void migrationReturnsMovedPreferencesAndPreservesExistingDeviceValues() {
        Context ce=new MigrationContext(context),de=ce.createDeviceProtectedStorageContext();
        SharedPreferences old=PreferenceManager.getDefaultSharedPreferences(ce);
        SharedPreferences device=PreferenceManager.getDefaultSharedPreferences(de);
        assertTrue(old.edit().clear().commit());assertTrue(device.edit().clear().commit());
        try {
            assertTrue(old.edit().putBoolean("synthetic_setting",true).commit());
            SharedPreferences migrated=DeviceProtectedUtils.getSharedPreferences(ce);
            assertTrue("First call must return migrated values",migrated.getBoolean("synthetic_setting",false));
            assertTrue(migrated.edit().putBoolean("synthetic_setting",false).commit());
            assertTrue(PreferenceManager.getDefaultSharedPreferences(ce).edit().putBoolean("synthetic_setting",true).commit());
            assertFalse("Existing device settings must win",DeviceProtectedUtils.getSharedPreferences(ce).getBoolean("synthetic_setting",true));
            assertFalse(DeviceProtectedUtils.getSharedPreferences(de).getBoolean("synthetic_setting",true));
        } finally {
            assertTrue(PreferenceManager.getDefaultSharedPreferences(ce).edit().clear().commit());
            assertTrue(PreferenceManager.getDefaultSharedPreferences(de).edit().clear().commit());
        }
    }
}
