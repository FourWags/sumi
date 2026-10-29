package android.os;
public final class Message {
 public int what,arg1,arg2; public Object obj; Handler target;
 public void sendToTarget(){target.enqueue(this);}
}
