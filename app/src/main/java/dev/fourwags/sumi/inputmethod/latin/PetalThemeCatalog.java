package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;

/** Generated from the approved offline palette map; regenerate with tools/generate_petal_themes.py. */
public final class PetalThemeCatalog {
    private PetalThemeCatalog() {}
    public static final String[] IDS = {"rose", "sage", "navy", "ocean", "blush", "arctic", "violet", "berry", "vermilion", "lemon", "cobalt", "pistachio", "marigold", "coral", "chartreuse", "amethyst", "standardlight", "standarddark", "shiba_apricot", "shiba_black_tan", "shiba_sesame"};
    public static final String[] NAMES = {"Rose Porcelain", "Sage & Linen", "Harbor Jade", "Tidepool Glass", "Blush Denim", "Arctic Sail", "Moonlit Orchid", "Berry Sorbet", "Vermilion Ink", "Lemon Grove", "Cobalt Bloom", "Pistachio Plum", "Marigold Sail", "Coral Reef", "Chartreuse Smoke", "Amethyst Ice", "Standard Light", "Standard Dark", "Apricot & Cream", "Black & Tan", "Sesame Dusk"};
    public static final int[] STYLES = {R.style.PetalTheme_Rose, R.style.PetalTheme_Sage, R.style.PetalTheme_Navy, R.style.PetalTheme_Ocean, R.style.PetalTheme_Blush, R.style.PetalTheme_Arctic, R.style.PetalTheme_Violet, R.style.PetalTheme_Berry, R.style.PetalTheme_Vermilion, R.style.PetalTheme_Lemon, R.style.PetalTheme_Cobalt, R.style.PetalTheme_Pistachio, R.style.PetalTheme_Marigold, R.style.PetalTheme_Coral, R.style.PetalTheme_Chartreuse, R.style.PetalTheme_Amethyst, R.style.PetalTheme_Standardlight, R.style.PetalTheme_Standarddark, R.style.PetalTheme_Shiba_Apricot, R.style.PetalTheme_Shiba_Black_Tan, R.style.PetalTheme_Shiba_Sesame};
    public static final int[] ICONS = {R.drawable.petal_icon_0, R.drawable.petal_icon_1, R.drawable.petal_icon_8, R.drawable.petal_icon_4, R.drawable.petal_icon_12, R.drawable.petal_icon_15, R.drawable.petal_icon_9, R.drawable.petal_icon_16, R.drawable.petal_icon_20, R.drawable.petal_icon_21, R.drawable.petal_icon_24, R.drawable.petal_icon_25, R.drawable.petal_icon_26, R.drawable.petal_icon_27, R.drawable.petal_icon_32, R.drawable.petal_icon_38, R.drawable.petal_icon_40, R.drawable.petal_icon_41, R.mipmap.fw_sumi_a, R.mipmap.fw_sumi_b, R.mipmap.fw_sumi_c};
    public static final int[] LEGACY_INDICES = {0, 1, 8, 4, 12, 15, 9, 16, 20, 21, 24, 25, 26, 27, 32, 38, 40, 41, 42, 43, 44};
    private static final int[] LEGACY_TO_SELECTION = {0, 1, 6, 5, 3, 8, 11, 2, 2, 6, 0, 1, 4, 12, 5, 5, 7, 6, 1, 7, 8, 9, 13, 7, 10, 11, 12, 13, 1, 0, 7, 10, 14, 12, 1, 0, 12, 4, 15, 0, 16, 17, 18, 19, 20};
    public static final int ALIAS_COUNT = 42;
    public static int indexForLegacy(int value) { return value>=0 && value<LEGACY_TO_SELECTION.length ? LEGACY_TO_SELECTION[value] : 0; }
    public static int selected(android.content.SharedPreferences prefs) { return prefs.contains("petal_theme") ? indexForLegacy(prefs.getInt("petal_theme",0)) : prefs.contains("petal_icon") ? indexForLegacy(prefs.getInt("petal_icon",0)) : 19; }
    public static final String SHIBA_RESET_MARKER = "petal_shiba_theme_reset_version";
    public static boolean migrateSelection(android.content.SharedPreferences prefs) { return prefs.getInt(SHIBA_RESET_MARKER,0)>=1 || prefs.edit().putInt("petal_theme",43).putInt(SHIBA_RESET_MARKER,1).commit(); }
    public static void ensureSelection(android.content.Context context) { android.content.SharedPreferences prefs=dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(context); if(!migrateSelection(prefs))return; int index=selected(prefs), legacy=LEGACY_INDICES[index]; if(prefs.getInt("petal_theme",0)!=legacy || (legacy<ALIAS_COUNT && prefs.getInt("petal_icon",0)!=legacy)) PetalSettingsActivity.selectTheme(context,index); }
}
