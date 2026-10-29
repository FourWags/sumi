package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;

/** Decorative preview; selection and its spoken name remain in the native theme selector. */
public final class PetalThemePreview extends android.view.View {
    private final int[] palette=new int[6];
    private static final int[] ATTRS={R.attr.petalSurface,R.attr.petalFunction,R.attr.sumiKey,R.attr.petalFunctionOutline,R.attr.sumiKeyOutline,R.attr.petalInk};
    private static final String[][] LABELS={{"↑","q","w","e","r","⌫"},{"?","a","s","d","f","↵"}};
    private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    public PetalThemePreview(android.content.Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void setSelection(int value){android.content.Context c=new android.view.ContextThemeWrapper(getContext(),PetalThemeCatalog.STYLES[value]);for(int i=0;i<ATTRS.length;i++)palette[i]=color(c,ATTRS[i]);invalidate();}
    private int color(android.content.Context context,int attr){android.util.TypedValue v=new android.util.TypedValue();context.getTheme().resolveAttribute(attr,v,true);return v.data;}
    @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density,gap=6*d,w=getWidth(),h=getHeight();
        paint.setColor(palette[0]);canvas.drawRoundRect(0,0,w,h,12*d,12*d,paint);
        float keyW=(w-7*gap)/6,keyH=(h-3*gap)/2;
        for(int row=0;row<2;row++)for(int col=0;col<6;col++){
            float x=gap+col*(keyW+gap),y=gap+row*(keyH+gap);boolean function=col==0||col==5;
            paint.setColor(palette[function?1:2]);paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawRoundRect(x,y,x+keyW,y+keyH,5*d,5*d,paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(palette[function?3:4]);canvas.drawRoundRect(x,y,x+keyW,y+keyH,5*d,5*d,paint);paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(palette[5]);paint.setTextAlign(android.graphics.Paint.Align.CENTER);paint.setTextSize(15*d);String label=LABELS[row][col];canvas.drawText(label,x+keyW/2,y+(keyH-paint.ascent()-paint.descent())/2,paint);
        }
    }
}
