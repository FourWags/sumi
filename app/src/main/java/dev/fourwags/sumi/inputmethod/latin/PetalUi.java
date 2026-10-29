package dev.fourwags.sumi.inputmethod.latin;

/** Small drawable factory; retains no views, activities or theme contexts. */
final class PetalUi {
    private PetalUi() { }
    static void setupControl(android.widget.Button button,boolean complete) {
        button.setEnabled(!complete);
        button.setBackgroundTintList(null);
        button.setTextColor(complete?0xff615b61:0xff211b22);
        button.setBackground(control(button.getContext(),complete?0xffe4e0dd:0xfff1e5e8,
                complete?0xffc3bcbe:0xffd8c4ca,0xff724151));
        button.setElevation(0);button.setStateListAnimator(null);
    }
    static android.graphics.drawable.Drawable control(android.content.Context context,int fill,int outline,int ink) {
        float density=context.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();
        surface.setColor(fill);surface.setCornerRadius(12*density);surface.setStroke(Math.max(1,Math.round(density)),outline);
        android.graphics.drawable.GradientDrawable mask=new android.graphics.drawable.GradientDrawable();
        mask.setColor(android.graphics.Color.WHITE);mask.setCornerRadius(12*density);
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((ink&0x00ffffff)|0x24000000),surface,mask);
    }
}
