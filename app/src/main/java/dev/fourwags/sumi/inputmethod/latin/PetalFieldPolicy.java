package dev.fourwags.sumi.inputmethod.latin;
import java.util.Locale;
/** Conservative allowlist: unknown and structured editors use literal input. No history anywhere. */
public final class PetalFieldPolicy {
    private PetalFieldPolicy() {}
    public static boolean isProtected(int inputType, int imeOptions, String privateOptions) {
        if ((inputType & 15) != 1) return true;
        if ((inputType & 0x80000) != 0 || (inputType & 0x10000) != 0) return true;
        if ((imeOptions & 0x1000000) != 0) return true;
        String options = privateOptions == null ? "" : privateOptions.toLowerCase(Locale.ROOT);
        if (options.contains("incognito") || options.contains("private") || options.contains("nolearning")
                || options.contains("nopersonalizedlearning") || options.contains("code")) return true;
        int variation = inputType & 0xff0;
        return variation != 0 && variation != 0x20 && variation != 0x30 && variation != 0x40;
    }
}
