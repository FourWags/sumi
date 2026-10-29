package dev.fourwags.sumi.inputmethod.latin;
/** Bounded plain-text display only; never changes the actual explicit paste operation. */
public final class PetalClipboardPreview {
    private PetalClipboardPreview() {}
    public static String visible(CharSequence text){if(text==null)return "";int end=Math.min(300,text.length());if(end<text.length()&&end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;return text.subSequence(0,end).toString()+(end<text.length()?"…":"");}
}
