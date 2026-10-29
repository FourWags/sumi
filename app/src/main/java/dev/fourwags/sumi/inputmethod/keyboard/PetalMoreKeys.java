package dev.fourwags.sumi.inputmethod.keyboard;

/** Explicit text-only alternatives shared by both QWERTY sizes and symbol pages.
 * No editor actions, media codes, emoji sequences or whitespace are admitted. */
public final class PetalMoreKeys {
    private PetalMoreKeys() { }
    public static String charactersFor(int code) {
        switch (Character.toLowerCase(code)) {
            case 113: return "1";
            case 119: return "2";
            case 101: return "3èéêëēę";
            case 114: return "4ř";
            case 116: return "5ť";
            case 121: return "6ýÿ";
            case 117: return "7ùúûüū";
            case 105: return "8ìíîïī";
            case 111: return "9òóôöõøœ";
            case 112: return "0";
            case 97: return "@àáâäãåæā";
            case 115: return "#ßśš";
            case 100: return "$ď";
            case 102: return "%";
            case 103: return "&ğ";
            case 104: return "-";
            case 106: return "+";
            case 107: return "(";
            case 108: return ")ł";
            case 122: return "*žźż";
            case 120: return "\"";
            case 99: return "çćč";
            case 118: return "'";
            case 98: return ":";
            case 110: return "ñń";
            case 109: return "?";
            case 44: return ";:!?";
            case 46: return "…·";
            case 33: return "¡";
            case 63: return "¿";
            case 39: return "‘’";
            case 34: return "“”";
            case 45: return "–—_";
            case 95: return "–—";
            case 47: return "\\";
            case 92: return "/";
            case 40: return "[{<";
            case 41: return "]}>";
            case 91: return "{<";
            case 93: return "}>";
            case 36: return "€£¥¢₩";
            case 8364: return "$£¥¢₩";
            case 37: return "‰";
            case 43: return "±";
            case 61: return "≠≈";
            case 42: return "×";
            case 49: return "¹½⅓¼";
            case 50: return "²⅔";
            case 51: return "³¾";
            case 48: return "⁰";
            default: return null;
        }
    }
    public static String[] specsFor(int code) {
        String characters=charactersFor(code);
        if(characters==null)return null;
        return characters.codePoints().mapToObj(cp -> {
            String text=new String(Character.toChars(cp));
            return cp=='\\' || cp=='|' || cp==',' ? "\\"+text : text;
        }).toArray(String[]::new);
    }
}
