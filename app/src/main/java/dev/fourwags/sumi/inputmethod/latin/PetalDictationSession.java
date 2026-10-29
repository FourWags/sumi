package dev.fourwags.sumi.inputmethod.latin;
/** Memory-only gate: a late result can never cross an editor/session boundary. */
public final class PetalDictationSession {
    private long serial;
    private Object editor;
    public long begin(Object connection, boolean allowed) {
        cancel();
        if(!allowed || connection==null)return -1;
        editor=connection;return serial;
    }
    public boolean isCurrent(long ticket){return serial==ticket && editor!=null;}
    public boolean active(){return editor!=null;}
    public void cancel(){editor=null;serial++;}
    public String finish(long ticket,Object connection,boolean allowed,String text){
        if(ticket!=serial || editor==null)return null;
        boolean accept=allowed && connection==editor && text!=null && !text.trim().isEmpty() && text.length()<=10000;
        cancel();return accept?text:null;
    }
}
