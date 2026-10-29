package android.os;
import java.util.ArrayList;
public final class Handler {
 public interface Callback {boolean handleMessage(Message message);}
 private final Looper looper; private final Callback callback;
 private final ArrayList<Message> queue=new ArrayList<>();
 public Handler(Looper looper,Callback callback){this.looper=looper;this.callback=callback;}
 public Message obtainMessage(int what,int arg1,int arg2,Object obj){Message m=new Message();m.what=what;m.arg1=arg1;m.arg2=arg2;m.obj=obj;m.target=this;return m;}
 void enqueue(Message m){queue.add(m);}
 public void removeCallbacksAndMessages(Object token){queue.clear();}
 public Looper getLooper(){return looper;}
 public void runAll(){while(!queue.isEmpty())callback.handleMessage(queue.remove(0));}
}
