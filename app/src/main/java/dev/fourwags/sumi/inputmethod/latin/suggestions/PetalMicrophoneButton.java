package dev.fourwags.sumi.inputmethod.latin.suggestions;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/** Touch hold and accessibility activation share an explicit click contract. */
public final class PetalMicrophoneButton extends ImageButton {
    public PetalMicrophoneButton(Context context,AttributeSet attrs){super(context,attrs);}
    public PetalMicrophoneButton(Context context,AttributeSet attrs,int style){super(context,attrs,style);}
    @Override public boolean performClick(){return super.performClick();}
}
