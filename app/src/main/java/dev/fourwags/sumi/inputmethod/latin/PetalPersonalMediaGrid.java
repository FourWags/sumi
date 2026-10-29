package dev.fourwags.sumi.inputmethod.latin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Six static thumbnails per page; bounded worker queue, no animation or permanent bitmap cache. */
public final class PetalPersonalMediaGrid extends LinearLayout {
    public interface Selection {void select(String id);}
    private final Selection selection;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),new ThreadPoolExecutor.DiscardPolicy());
    private final LinearLayout cells;
    private final TextView status;
    private final Button previous,next;
    private int page,generation;private boolean closed;private List<String> ids=new ArrayList<>();
    public PetalPersonalMediaGrid(Context context,Selection selection){super(context);this.selection=selection;setOrientation(VERTICAL);
        status=new TextView(context);status.setTextColor(0xff211b22);status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);addView(status);
        ScrollView scroll=new ScrollView(context);cells=new LinearLayout(context);cells.setOrientation(VERTICAL);scroll.addView(cells);addView(scroll,new LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(context);previous=button("‹",v->{page--;render();});next=button("›",v->{page++;render();});previous.setContentDescription("Previous media page");next.setContentDescription("Next media page");nav.addView(previous,new LayoutParams(0,-2,1));nav.addView(next,new LayoutParams(0,-2,1));addView(nav);reload();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private Button button(String text,OnClickListener action){Button b=new Button(getContext());b.setText(text);b.setAllCaps(false);b.setTextColor(0xff211b22);b.setMinHeight(dp(48));b.setOnClickListener(action);return b;}
    public void reload(){if(closed)return;final int request=++generation;status.setText("Loading personal media…");worker.getQueue().clear();Context app=getContext().getApplicationContext();worker.execute(()->{
        List<String> found;try{found=PetalPersonalMediaStore.list(app);}catch(java.io.IOException|RuntimeException e){found=null;}final List<String> result=found;
        handler.post(()->{if(closed||generation!=request)return;if(result==null){status.setText("Personal media unavailable. Unlock your phone or free some storage.");return;}ids=result;page=Math.min(page,Math.max(0,(ids.size()-1)/6));render();});
    });}
    private void releaseImages(View view){if(view instanceof ImageView){ImageView image=(ImageView)view;android.graphics.drawable.Drawable d=image.getDrawable();image.setImageDrawable(null);if(d instanceof BitmapDrawable)((BitmapDrawable)d).getBitmap().recycle();}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)releaseImages(g.getChildAt(i));}}
    private void render(){if(closed)return;final int request=++generation;worker.getQueue().clear();releaseImages(cells);cells.removeAllViews();previous.setEnabled(page>0);next.setEnabled((page+1)*6<ids.size());
        status.setText(ids.isEmpty()?"No personal media yet. Use Import & manage to add a file.":"Page "+(page+1)+" / "+((ids.size()+5)/6));
        LinearLayout row=null;for(int position=page*6;position<Math.min(ids.size(),page*6+6);position++){
            final String id=ids.get(position);if(position%2==0){row=new LinearLayout(getContext());cells.addView(row);}
            LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);row.addView(card,new LayoutParams(0,-2,1));
            ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(image,new LayoutParams(-1,dp(72)));
            Button action=button((id.endsWith("gif")?"GIF ":"Image ")+(position+1),v->selection.select(id));action.setContentDescription((getContext() instanceof android.app.Activity?"Remove ":"Insert ")+action.getText());card.addView(action);
            Context app=getContext().getApplicationContext();worker.execute(()->{Bitmap bitmap;try{bitmap=PetalPersonalMediaStore.thumbnail(app,id);}catch(java.io.IOException|RuntimeException e){bitmap=null;}final Bitmap loaded=bitmap;handler.post(()->{if(closed||request!=generation){if(loaded!=null)loaded.recycle();return;}if(loaded!=null)image.setImageBitmap(loaded);});});
        }
    }
    public void close(){if(closed)return;closed=true;generation++;worker.shutdownNow();releaseImages(cells);cells.removeAllViews();}
}
