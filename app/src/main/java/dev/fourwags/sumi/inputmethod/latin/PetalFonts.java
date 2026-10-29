package dev.fourwags.sumi.inputmethod.latin;

import dev.fourwags.sumi.R;
import android.content.Context;
import android.graphics.Typeface;

public final class PetalFonts {
    private PetalFonts() {}
    public static Typeface sans(Context context,int weight){return Typeface.create(context.getResources().getFont(R.font.dm_sans),weight,false);}
    public static Typeface heading(Context context){return Typeface.create(context.getResources().getFont(R.font.fraunces),600,false);}
}
