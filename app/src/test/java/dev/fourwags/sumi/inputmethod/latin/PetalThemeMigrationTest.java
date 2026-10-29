package dev.fourwags.sumi.inputmethod.latin;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetalThemeMigrationTest {
    private static SharedPreferences prefs(Map<String,Integer> values, AtomicBoolean failCommit) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "contains": return values.containsKey((String) args[0]);
                    case "getInt": return values.getOrDefault((String) args[0], (Integer) args[1]);
                    case "edit":
                        Map<String,Integer> pending = new HashMap<>();
                        return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (editor, editMethod, editArgs) -> {
                                switch (editMethod.getName()) {
                                    case "putInt": pending.put((String) editArgs[0], (Integer) editArgs[1]); return editor;
                                    case "commit": if (failCommit.get()) return false; values.putAll(pending); return true;
                                    default: throw new UnsupportedOperationException(editMethod.getName());
                                }
                            });
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
    }
    private static SharedPreferences prefs(Map<String,Integer> values) {
        return prefs(values, new AtomicBoolean(false));
    }

    @Test public void unsetStartsBlackTanWithoutChangingSavedChoices() {
        assertEquals(19, PetalThemeCatalog.selected(prefs(Map.of())));
        assertEquals(0, PetalThemeCatalog.selected(prefs(Map.of("petal_theme", 0))));
        assertEquals(0, PetalThemeCatalog.selected(prefs(Map.of("petal_icon", 0))));
        assertEquals(19, PetalThemeCatalog.selected(prefs(Map.of("petal_theme", 43))));
        assertEquals(20, PetalThemeCatalog.selected(prefs(Map.of("petal_theme", 44))));
    }

    @Test public void resetRunsOnceAndPreservesOtherSettings() {
        Map<String,Integer> values = new HashMap<>(Map.of("petal_theme", 0, "petal_icon", 7, "petal_large_keys", 1));
        SharedPreferences p = prefs(values);
        assertTrue(PetalThemeCatalog.migrateSelection(p));
        assertEquals(43, p.getInt("petal_theme", -1));
        assertEquals(1, p.getInt(PetalThemeCatalog.SHIBA_RESET_MARKER, 0));
        assertEquals(7, p.getInt("petal_icon", -1));
        assertEquals(1, p.getInt("petal_large_keys", 0));
        values.put("petal_theme", 44);
        assertTrue(PetalThemeCatalog.migrateSelection(p));
        assertEquals(44, p.getInt("petal_theme", -1));
    }

    @Test public void failedCommitRetriesWithoutPartialReset() {
        Map<String,Integer> values = new HashMap<>(Map.of("petal_theme", 2));
        AtomicBoolean fail = new AtomicBoolean(true);
        SharedPreferences p = prefs(values, fail);
        assertFalse(PetalThemeCatalog.migrateSelection(p));
        assertEquals(2, p.getInt("petal_theme", -1));
        assertFalse(p.contains(PetalThemeCatalog.SHIBA_RESET_MARKER));
        fail.set(false);
        assertTrue(PetalThemeCatalog.migrateSelection(p));
        assertEquals(43, p.getInt("petal_theme", -1));
    }

    @Test public void legacySelectionsRemainStableWithShibaOptions() {
        int[] expected={0,1,6,5,3,8,11,2,2,6,0,1,4,12,5,5,7,6,1,7,8,9,13,7,10,11,12,13,1,0,7,10,14,12,1,0,12,4,15,0};
        assertEquals(21,PetalThemeCatalog.IDS.length);
        for(int n=0;n<40;n++)assertEquals(expected[n],PetalThemeCatalog.indexForLegacy(n));
        assertEquals(0,PetalThemeCatalog.indexForLegacy(-1));
        assertEquals(0,PetalThemeCatalog.indexForLegacy(999));
        for(int n=0;n<21;n++)assertEquals(n,PetalThemeCatalog.indexForLegacy(PetalThemeCatalog.LEGACY_INDICES[n]));
    }
}
