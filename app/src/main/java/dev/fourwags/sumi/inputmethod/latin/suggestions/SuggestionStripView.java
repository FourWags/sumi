/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.fourwags.sumi.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import dev.fourwags.sumi.inputmethod.accessibility.AccessibilityUtils;
import dev.fourwags.sumi.inputmethod.keyboard.Keyboard;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardSwitcher;
import dev.fourwags.sumi.inputmethod.keyboard.MainKeyboardView;
import dev.fourwags.sumi.inputmethod.keyboard.MoreKeysPanel;
import dev.fourwags.sumi.inputmethod.latin.AudioAndHapticFeedbackManager;
import dev.fourwags.sumi.R;
import dev.fourwags.sumi.inputmethod.latin.SuggestedWords;
import dev.fourwags.sumi.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import dev.fourwags.sumi.inputmethod.latin.common.Constants;
import dev.fourwags.sumi.inputmethod.latin.define.DebugFlags;
import dev.fourwags.sumi.inputmethod.latin.settings.Settings;
import dev.fourwags.sumi.inputmethod.latin.settings.SettingsValues;
import dev.fourwags.sumi.inputmethod.latin.suggestions.MoreSuggestionsView.MoreSuggestionsListener;

import java.util.ArrayList;

import androidx.core.view.ViewCompat;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        void pickSuggestionManually(SuggestedWordInfo word);
        void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);
        void onTextInput(final String rawText);
        CharSequence getSelection();
        void petalRevertCorrection();
        void petalShowTools(View anchor);
        default boolean petalVoiceAllowed(){return false;}
        default void petalToggleVoice(View anchor){}
        default void petalStartHeldVoice(View anchor){}
        default void petalStopHeldVoice(){}
        default void petalCancelHeldVoice(){}
    }

    private boolean mVoiceTouchGesture,mVoiceHolding;
    private int mVoicePointer=-1;
    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;

    private final ViewGroup mSuggestionsStrip;
    private final PetalMicrophoneButton mVoiceKey;
    private final ImageButton mClipboardKey;
    private final ImageButton mOtherKey;
    private final TextView mRevertKey;
    MainKeyboardView mMainKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;

        public StripVisibilityGroup(final View suggestionStripView,
                final ViewGroup suggestionsStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
        }

    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = findViewById(R.id.suggestions_strip);
        mVoiceKey = findViewById(R.id.suggestions_strip_voice_key);
        mClipboardKey = findViewById(R.id.suggestions_strip_clipboard_key);
        mOtherKey = findViewById(R.id.suggestions_strip_other_key);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip);
        mRevertKey = new TextView(context);
        android.util.TypedValue petalInk=new android.util.TypedValue();
        context.getTheme().resolveAttribute(R.attr.petalStripInk,petalInk,true);
        mRevertKey.setTextColor(petalInk.data);
        mRevertKey.setGravity(android.view.Gravity.CENTER);
        mRevertKey.setTextSize(18);
        int targetHeight=getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
        mRevertKey.setMinHeight(targetHeight);
        int revertPadding=Math.round(8*getResources().getDisplayMetrics().density);
        mRevertKey.setPadding(revertPadding,revertPadding,revertPadding,revertPadding);
        mRevertKey.setFocusable(true);
        mRevertKey.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        mRevertKey.setVisibility(GONE);
        ((android.widget.LinearLayout)mSuggestionsStrip.getParent()).addView(mRevertKey, 1,
            new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        mRevertKey.setOnClickListener(v -> {
            if (mListener != null) mListener.petalRevertCorrection();
        });

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, mSuggestionsStrip, false);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, this, false);
        mMoreSuggestionsView = mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        final Drawable iconVoice = context.getDrawable(R.drawable.petal_voice_bubble);
        final Drawable iconIncognito = keyboardAttr.getDrawable(R.styleable.Keyboard_iconIncognitoKey);
        final Drawable iconClipboard = keyboardAttr.getDrawable(R.styleable.Keyboard_iconClipboardNormalKey);
        keyboardAttr.recycle();
        mVoiceKey.setImageDrawable(iconVoice);
        mVoiceKey.setVisibility(GONE);
        mVoiceKey.setEnabled(true);mVoiceKey.setClickable(true);mVoiceKey.setLongClickable(false);
        mVoiceKey.setOnClickListener(v->{if(mListener!=null)mListener.petalToggleVoice(mVoiceKey);});
        mVoiceKey.setOnTouchListener((view,event)->{
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN){
                mVoiceTouchGesture=true;mVoiceHolding=true;mVoicePointer=event.getPointerId(0);view.setPressed(true);
                android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);mMoreSuggestionsSlidingDetector.onTouchEvent(cancel);cancel.recycle();
                return true;
            }
            if(action==android.view.MotionEvent.ACTION_MOVE){
                int index=event.findPointerIndex(mVoicePointer);
                if(mVoiceHolding&&(index<0||event.getX(index)<0||event.getX(index)>=view.getWidth()||event.getY(index)<0||event.getY(index)>=view.getHeight())){
                    mVoiceHolding=false;view.setPressed(false);
                }return true;
            }
            if(action==android.view.MotionEvent.ACTION_POINTER_DOWN||action==android.view.MotionEvent.ACTION_CANCEL){
                mVoiceHolding=false;view.setPressed(false);
                if(action==android.view.MotionEvent.ACTION_CANCEL){mVoiceTouchGesture=false;mVoicePointer=-1;}return true;
            }
            if(action==android.view.MotionEvent.ACTION_UP){
                boolean activate=mVoiceHolding;mVoiceHolding=false;view.setPressed(false);
                // Touch and assistive activation share the same start/stop action.
                if(activate)view.performClick();mVoiceTouchGesture=false;mVoicePointer=-1;return true;
            }return true;
        });
        android.util.TypedValue voiceInk=new android.util.TypedValue();context.getTheme().resolveAttribute(R.attr.petalStripInk,voiceInk,true);mVoiceKey.setImageTintList(android.content.res.ColorStateList.valueOf(voiceInk.data));
        mVoiceKey.setContentDescription(context.getString(R.string.petal_voice_start));
        android.view.ViewGroup.LayoutParams voiceSize=mVoiceKey.getLayoutParams();voiceSize.width=Math.round(64*context.getResources().getDisplayMetrics().density);voiceSize.height=Math.round(56*context.getResources().getDisplayMetrics().density);mVoiceKey.setLayoutParams(voiceSize);
        mClipboardKey.setImageDrawable(iconClipboard);
        mClipboardKey.setVisibility(GONE);
        mClipboardKey.setEnabled(false);mClipboardKey.setClickable(false);mClipboardKey.setLongClickable(false);

        // A theme-aware Shiba head identifies the tools menu independently of launcher artwork.
        refreshToolsIcon();
        refreshSuggestionSize();
        // A first layout or a newly visible action can change the width available to words.
        // Refit labels after child geometry is known; keep all three choices and their full labels.
        mSuggestionsStrip.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            if(right>left && right-left!=oldRight-oldLeft)setSuggestions(mSuggestedWords,false);
        });
        mOtherKey.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int mediaPadding=Math.round(8*context.getResources().getDisplayMetrics().density);
        mOtherKey.setPadding(mediaPadding,mediaPadding,mediaPadding,mediaPadding);
        mOtherKey.setContentDescription(getResources().getString(R.string.petal_tools_description));
        mOtherKey.setOnClickListener(v -> {
            if (mListener != null) mListener.petalShowTools(mOtherKey);
        });
    }

    /** Keep font metrics and actual hit areas in sync, independently of keyboard size. */
    public void refreshSuggestionSize() {
        int selected = dev.fourwags.sumi.inputmethod.latin.PetalTypingOptions.suggestionSize(
                dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(getContext())
                        .getInt(dev.fourwags.sumi.inputmethod.latin.PetalTypingOptions.SUGGESTION_SIZE,125));
        int sp = dev.fourwags.sumi.inputmethod.latin.PetalTypingOptions.suggestionTextSp(selected);
        int minimum = Math.round(dev.fourwags.sumi.inputmethod.latin.PetalTypingOptions
                .suggestionHeightDp(selected)*getResources().getDisplayMetrics().density);
        for (TextView word : mWordViews) {
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
            word.setSingleLine(true);
        }
        mRevertKey.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        mRevertKey.setSingleLine(true);mRevertKey.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sample=mWordViews.get(0);
        android.graphics.Paint.FontMetricsInt metrics=sample.getPaint().getFontMetricsInt();
        int height=Math.max(minimum,metrics.bottom-metrics.top+sample.getCompoundPaddingTop()
                +sample.getCompoundPaddingBottom()+Math.round(16*getResources().getDisplayMetrics().density));
        View wrapper=findViewById(R.id.suggestions_strip_wrapper);
        wrapper.setMinimumHeight(height);
        android.view.ViewGroup.LayoutParams size=wrapper.getLayoutParams();size.height=android.view.ViewGroup.LayoutParams.WRAP_CONTENT;wrapper.setLayoutParams(size);
        mSuggestionsStrip.getLayoutParams().height=android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        mRevertKey.setMinHeight(height);
        for (View control : new View[]{mOtherKey,mVoiceKey}) {
            android.view.ViewGroup.LayoutParams lp=control.getLayoutParams();lp.height=height;control.setLayoutParams(lp);
        }
        if(mLayoutHelper!=null)mLayoutHelper.mSuggestionsStripHeight=height;
        if(mSuggestedWords!=null)setSuggestions(mSuggestedWords,false);
        requestLayout();
    }

    public void refreshToolsIcon() {
        mOtherKey.setImageResource(dev.fourwags.sumi.inputmethod.latin.PetalButtonIcon.drawable(
                dev.fourwags.sumi.inputmethod.latin.PetalButtonIcon.selected(
                        dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils.getSharedPreferences(getContext()))));
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = inputView.findViewById(R.id.keyboard_view);
        mVoiceKey.setVisibility(mListener!=null && mListener.petalVoiceAllowed()?VISIBLE:GONE);
    }

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        mVoiceKey.setVisibility(mListener!=null && mListener.petalVoiceAllowed()?VISIBLE:GONE);
        mClipboardKey.setVisibility(GONE);
        mOtherKey.setVisibility(VISIBLE);
    }

    public void showRevert(String original, boolean available) {
        mRevertKey.setText(available ? getResources().getString(R.string.petal_revert_label, original) : "");
        mRevertKey.setContentDescription(available ? getResources().getString(R.string.petal_revert_description, original) : null);
        mRevertKey.setVisibility(available ? VISIBLE : GONE);
        mSuggestionsStrip.setVisibility(available ? GONE : VISIBLE);
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
        mStripVisibilityGroup.showSuggestionsStrip();
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        mSuggestedWords = SuggestedWords.getEmptyInstance();
        mStartIndexOfMoreSuggestions = 0;
        for (final TextView word : mWordViews) {
            word.setText("");
            word.setContentDescription(null);
            word.setTag(null);
        }
        for (final TextView info : mDebugInfoViews) info.setText("");
        mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
    }

    /** Clear retained editor-derived data even when the strip is hidden. */
    public void clearInputSession() {
        clear();
        showRevert("", false);
        mMoreSuggestionsBuilder.clearInputSession();
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup)parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel() {
            mMainKeyboardView.onDismissMoreKeysPanel();
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        if (view == mClipboardKey || view == mVoiceKey) return false;
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = mMainKeyboardView.getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < mStartIndexOfMoreSuggestions; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for {@link onInterceptTouchEvent(MotionEvent)} and
    // {@link onTouchEvent(MotionEvent)}.
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private boolean mNeedsToTransformTouchEventToHoverEvent;
    private boolean mIsDispatchingHoverEventToMoreSuggestions;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        // The voice button owns its tap until release or cancellation.
        if (mVoiceTouchGesture) { return false; }
        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent =
                    AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled();
            mIsDispatchingHoverEventToMoreSuggestions = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            mMoreSuggestionsView.setModalMode();
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with suggested words and voice key.
        return true;
    }

    // Only forwards sliding gestures to the virtual suggestion buttons, which expose ACTION_CLICK.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            // Detecting sliding up is done at {@link #onInterceptTouchEvent}.
            return true;
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        final int index = me.getActionIndex();
        final int x = mMoreSuggestionsView.translateX((int)me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int)me.getY(index));
        me.setLocation(x, y);
        if (!mNeedsToTransformTouchEventToHoverEvent) {
            mMoreSuggestionsView.onTouchEvent(me);
            return true;
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        final int width = mMoreSuggestionsView.getWidth();
        final int height = mMoreSuggestionsView.getHeight();
        final boolean onMoreSuggestions = (x >= 0 && x < width && y >= 0 && y < height);
        if (!onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on {@link MoreSuggestionsView}.
            return true;
        }
        final int hoverAction;
        if (onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover
            // event to {@link MoreSuggestionsView}.
            mIsDispatchingHoverEventToMoreSuggestions = true;
            hoverAction = MotionEvent.ACTION_HOVER_ENTER;
        } else if (me.getActionMasked() == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event
            // after this.
            mIsDispatchingHoverEventToMoreSuggestions = false;
            mNeedsToTransformTouchEventToHoverEvent = false;
            hoverAction = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE;
        }
        me.setAction(hoverAction);
        mMoreSuggestionsView.onHoverEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        if(view == mVoiceKey || view == mClipboardKey)return;

        final Object tag = view.getTag();
        // {@link Integer} tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(wordInfo);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if(mVoiceHolding&&mListener!=null)mListener.petalCancelHeldVoice();
        mVoiceHolding=false;mVoiceTouchGesture=false;mVoicePointer=-1;
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
    }
}
