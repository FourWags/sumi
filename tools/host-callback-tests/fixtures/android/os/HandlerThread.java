package android.os;
public final class HandlerThread {
 private final Looper looper=new Looper();
 public HandlerThread(String name) {} public void start() {} public Looper getLooper(){return looper;}
}
