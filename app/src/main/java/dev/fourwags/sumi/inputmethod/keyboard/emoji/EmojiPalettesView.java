/*
 * Copyright (C) 2013 The Android Open Source Project
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

package dev.fourwags.sumi.inputmethod.keyboard.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import dev.fourwags.sumi.inputmethod.compat.TabHostCompat;
import dev.fourwags.sumi.inputmethod.keyboard.Key;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardActionListener;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardLayoutSet;
import dev.fourwags.sumi.inputmethod.keyboard.KeyboardView;
import dev.fourwags.sumi.inputmethod.keyboard.internal.KeyDrawParams;
import dev.fourwags.sumi.inputmethod.keyboard.internal.KeyVisualAttributes;
import dev.fourwags.sumi.inputmethod.keyboard.internal.KeyboardIconsSet;
import dev.fourwags.sumi.inputmethod.latin.AudioAndHapticFeedbackManager;
import dev.fourwags.sumi.R;
import dev.fourwags.sumi.inputmethod.latin.RichInputMethodSubtype;
import dev.fourwags.sumi.inputmethod.latin.common.Constants;
import dev.fourwags.sumi.inputmethod.latin.settings.Settings;
import dev.fourwags.sumi.inputmethod.latin.settings.SettingsValues;
import dev.fourwags.sumi.inputmethod.latin.utils.DeviceProtectedUtils;
import dev.fourwags.sumi.inputmethod.latin.utils.ResourceUtils;

import org.jetbrains.annotations.NotNull;

import static dev.fourwags.sumi.inputmethod.latin.common.Constants.NOT_A_COORDINATE;

/**
 * View class to implement Emoji palettes.
 * The Emoji keyboard consists of group of views layout/emoji_palettes_view.
 * <ol>
 * <li> Emoji category tabs.
 * <li> Delete button.
 * <li> Emoji keyboard pages that can be scrolled by swiping horizontally or by selecting a tab.
 * <li> Back to main keyboard button and enter button.
 * </ol>
 * Because of the above reasons, this class doesn't extend {@link KeyboardView}.
 */
@SuppressWarnings("deprecation")
public final class EmojiPalettesView extends LinearLayout
        implements OnTabChangeListener, View.OnClickListener,
        OnKeyEventListener {
    private final int mFunctionalKeyBackgroundId;
    private final int mSpacebarBackgroundId;
    private final boolean mCategoryIndicatorEnabled;
    private final int mCategoryIndicatorDrawableResId;
    private final int mCategoryIndicatorBackgroundResId;
    private final int mCategoryPageIndicatorColor;
    private final int mCategoryPageIndicatorBackground;
    private EmojiPalettesAdapter mEmojiPalettesAdapter;
    private EmojiLayoutParams mEmojiLayoutParams;
    private boolean mLargeKeys;
    private final KeyboardLayoutSet mEmojiLayoutSet;
    private final int mDefStyle;
    private final LinearLayoutManager mEmojiLayoutManager;

    private ImageButton mDeleteKey;
    private ImageButton mPersonalMediaKey;
    private TextView mAlphabetKeyLeft;
    private View mSpacebar;
    // TODO: Remove this workaround.
    private View mSpacebarIcon;
    private TabHostCompat mTabHost;
    private RecyclerView mEmojiRecyclerView;
    private EmojiCategoryPageIndicatorView mEmojiCategoryPageIndicatorView;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

    private EmojiCategory mEmojiCategory;

    public EmojiPalettesView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.emojiPalettesViewStyle);
    }

    public EmojiPalettesView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        mDefStyle = defStyle;
        mLargeKeys = DeviceProtectedUtils.getSharedPreferences(context).getBoolean("petal_large_keys", false);
        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        final int keyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyBackground, 0);
        mFunctionalKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_functionalKeyBackground, keyBackgroundId);
        mSpacebarBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_spacebarBackground, keyBackgroundId);
        keyboardViewAttr.recycle();
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                context, null /* editorInfo */);
        final Resources res = context.getResources();
        mEmojiLayoutParams = new EmojiLayoutParams(res, mLargeKeys);
        builder.setSubtype(RichInputMethodSubtype.getEmojiSubtype());
        builder.setKeyboardGeometry(ResourceUtils.getDefaultKeyboardWidth(res),
                mEmojiLayoutParams.mEmojiKeyboardHeight);
        mEmojiLayoutSet = builder.build();
        final TypedArray emojiPalettesViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.EmojiPalettesView, defStyle, R.style.EmojiPalettesView);
        mEmojiCategory = new EmojiCategory(DeviceProtectedUtils.getSharedPreferences(context),
                res, mEmojiLayoutSet, emojiPalettesViewAttr, mEmojiLayoutParams.mLargeTargetSize);
        mCategoryIndicatorEnabled = emojiPalettesViewAttr.getBoolean(
                R.styleable.EmojiPalettesView_categoryIndicatorEnabled, false);
        mCategoryIndicatorDrawableResId = emojiPalettesViewAttr.getResourceId(
                R.styleable.EmojiPalettesView_categoryIndicatorDrawable, 0);
        mCategoryIndicatorBackgroundResId = emojiPalettesViewAttr.getResourceId(
                R.styleable.EmojiPalettesView_categoryIndicatorBackground, 0);
        mCategoryPageIndicatorColor = emojiPalettesViewAttr.getColor(
                R.styleable.EmojiPalettesView_categoryPageIndicatorColor, 0);
        mCategoryPageIndicatorBackground = emojiPalettesViewAttr.getColor(
                R.styleable.EmojiPalettesView_categoryPageIndicatorBackground, 0);
        emojiPalettesViewAttr.recycle();
        mEmojiLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Resources res = getContext().getResources();
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = ResourceUtils.getDefaultKeyboardWidth(res)
                + getPaddingLeft() + getPaddingRight();
        final int height = mLargeKeys ? mEmojiLayoutParams.mCategoryHeight
                + mEmojiLayoutParams.mEmojiKeyboardHeight
                + res.getDimensionPixelSize(R.dimen.config_emoji_category_page_id_height)
                + mEmojiLayoutParams.getActionBarHeight()
                + getPaddingTop() + getPaddingBottom()
                : ResourceUtils.getRegularKeyboardHeight(res)
                + res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    private void addTab(final TabHost host, final int categoryId) {
        final String tabId = EmojiCategory.getCategoryName(categoryId, 0 /* categoryPageId */);
        final TabHost.TabSpec tspec = host.newTabSpec(tabId);
        tspec.setContent(R.id.emoji_keyboard_dummy);
        final ImageView iconView = (ImageView) LayoutInflater.from(getContext()).inflate(
                R.layout.emoji_keyboard_tab_icon, host.getTabWidget(), false);
        // TODO: Replace background color with its own setting rather than using the
        //       category page indicator background as a workaround.
        iconView.setBackgroundColor(mCategoryPageIndicatorBackground);
        iconView.setImageResource(mEmojiCategory.getCategoryTabIcon(categoryId));
        iconView.setContentDescription(mEmojiCategory.getAccessibilityDescription(categoryId));
        tspec.setIndicator(iconView);
        host.addTab(tspec);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabHost = findViewById(R.id.emoji_category_tabhost);
        mTabHost.setup();
        for (final EmojiCategory.CategoryProperties properties
                : mEmojiCategory.getShownCategories()) {
            addTab(mTabHost, properties.mCategoryId);
        }
        mTabHost.setOnTabChangedListener(this);
        final TabWidget tabWidget = mTabHost.getTabWidget();
        tabWidget.setStripEnabled(mCategoryIndicatorEnabled);
        if (mCategoryIndicatorEnabled) {
            // On TabWidget's strip, what looks like an indicator is actually a background.
            // And what looks like a background are actually left and right drawables.
            tabWidget.setBackgroundResource(mCategoryIndicatorDrawableResId);
            tabWidget.setLeftStripDrawable(mCategoryIndicatorBackgroundResId);
            tabWidget.setRightStripDrawable(mCategoryIndicatorBackgroundResId);
        }

        mEmojiPalettesAdapter = new EmojiPalettesAdapter(mEmojiCategory, this);

        mEmojiRecyclerView = findViewById(R.id.emoji_keyboard_list);
        mEmojiRecyclerView.setLayoutManager(mEmojiLayoutManager);
        mEmojiRecyclerView.setAdapter(mEmojiPalettesAdapter);
        mEmojiRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // Ignore this message. Only want the actual page selected.
            }

            @Override
            public void onScrolled(@NonNull @NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mEmojiPalettesAdapter.onPageScrolled();

                final int offset = recyclerView.computeVerticalScrollOffset();
                final int extent = recyclerView.computeVerticalScrollExtent();
                final int range = recyclerView.computeVerticalScrollRange();
                final float percentage = offset / (float) (range - extent);

                final int currentCategorySize = mEmojiCategory.getCurrentCategoryPageCount();
                final int a = (int) (percentage * currentCategorySize);
                final float b = percentage * currentCategorySize - a;
                mEmojiCategoryPageIndicatorView.setCategoryPageId(
                        currentCategorySize, a, b);

                final int firstCompleteVisibleBoard = mEmojiLayoutManager.findFirstCompletelyVisibleItemPosition();
                final int firstVisibleBoard = mEmojiLayoutManager.findFirstVisibleItemPosition();
                mEmojiCategory.setCurrentCategoryPageId(
                        firstCompleteVisibleBoard > 0 ? firstCompleteVisibleBoard : firstVisibleBoard);
            }
        });

        mEmojiRecyclerView.setPersistentDrawingCache(PERSISTENT_NO_CACHE);
        mEmojiLayoutParams.setEmojiListProperties(mEmojiRecyclerView);

        mEmojiCategoryPageIndicatorView =
                findViewById(R.id.emoji_category_page_id_view);
        mEmojiCategoryPageIndicatorView.setColors(
                mCategoryPageIndicatorColor, mCategoryPageIndicatorBackground);
        mEmojiLayoutParams.setCategoryPageIdViewProperties(mEmojiCategoryPageIndicatorView);

        setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), mEmojiCategory.getCurrentCategoryPageId(),
                true /* force */);
        // Enable reselection after the first setCurrentCategoryAndPageId() init call
        mTabHost.setFireOnTabChangeListenerOnReselection(true);

        final LinearLayout actionBar = findViewById(R.id.emoji_action_bar);
        mEmojiLayoutParams.setActionBarProperties(actionBar);

        // Standard click actions support TalkBack and Switch Access as well as touch.
        mDeleteKey = findViewById(R.id.emoji_keyboard_delete);
        mDeleteKey.setBackgroundResource(mFunctionalKeyBackgroundId);
        mDeleteKey.setTag(Constants.CODE_DELETE);
        mDeleteKey.setOnClickListener(this);

        // The framework click path supports touch and assistive-service activation.
        // {@link #mAlphabetKeyLeft} and spaceKey depend on
        // {@link View.OnClickListener} as well as {@link View.OnTouchListener}.
        // {@link View.OnTouchListener} is used as the trigger of key-press, while
        // {@link View.OnClickListener} is used as the trigger of key-release which does not occur
        // if the event is canceled by moving off the finger from the view.
        // The text on alphabet keys are set at
        // {@link #startEmojiPalettes(String,int,float,Typeface)}.
        mAlphabetKeyLeft = findViewById(R.id.emoji_keyboard_alphabet_left);
        mAlphabetKeyLeft.setBackgroundResource(mFunctionalKeyBackgroundId);
        mAlphabetKeyLeft.setTag(Constants.CODE_ALPHA_FROM_EMOJI);
        mAlphabetKeyLeft.setOnClickListener(this);
        mSpacebar = findViewById(R.id.emoji_keyboard_space);
        mSpacebar.setBackgroundResource(mSpacebarBackgroundId);
        mSpacebar.setTag(Constants.CODE_SPACE);
        mSpacebar.setOnClickListener(this);
        mEmojiLayoutParams.setKeyProperties(mSpacebar);
        mSpacebarIcon = findViewById(R.id.emoji_keyboard_space_icon);
        android.widget.ImageButton personal=new android.widget.ImageButton(getContext());mPersonalMediaKey=personal;personal.setId(R.id.emoji_keyboard_personal_media);personal.setImageResource(android.R.drawable.ic_menu_gallery);personal.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));personal.setContentDescription("My media");personal.setTooltipText("My media");personal.setBackgroundResource(mFunctionalKeyBackgroundId);personal.setPadding(8,8,8,8);
        actionBar.addView(personal,1,new LinearLayout.LayoutParams(Math.round(56*getResources().getDisplayMetrics().density),-1));
        personal.setOnClickListener(v->{if(mKeyboardActionListener!=null)mKeyboardActionListener.onPersonalMedia(personal);});
        applyTargetSizes();
    }

    private void applyTargetSizes() {
        final HorizontalScrollView scroll = findViewById(R.id.emoji_category_scroll);
        final android.view.ViewGroup.LayoutParams stripParams = scroll.getLayoutParams();
        stripParams.height = mEmojiLayoutParams.mCategoryHeight;
        scroll.setLayoutParams(stripParams);
        final TabWidget tabs = mTabHost.getTabWidget();
        final android.view.ViewGroup.LayoutParams hostParams = mTabHost.getLayoutParams();
        hostParams.width = mLargeKeys ? tabs.getChildCount() * mEmojiLayoutParams.mLargeTargetSize
                : android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        mTabHost.setLayoutParams(hostParams);
        for (int i = 0; i < tabs.getChildCount(); i++) {
            final View tab = tabs.getChildAt(i);
            tab.setLayoutParams(new LinearLayout.LayoutParams(mLargeKeys
                    ? mEmojiLayoutParams.mLargeTargetSize : 0,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, mLargeKeys ? 0 : 1));
        }
        mEmojiLayoutParams.setEmojiListProperties(mEmojiRecyclerView);
        mEmojiLayoutParams.setActionBarProperties(findViewById(R.id.emoji_action_bar));
        mEmojiLayoutParams.setKeyProperties(mAlphabetKeyLeft);
        mEmojiLayoutParams.setKeyProperties(mDeleteKey);
        mEmojiLayoutParams.setKeyProperties(mSpacebar);
        scroll.post(() -> tabs.getChildAt(mTabHost.getCurrentTab()).requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, tabs.getChildAt(mTabHost.getCurrentTab()).getWidth(),
                        mEmojiLayoutParams.mCategoryHeight), true));
        requestLayout();
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        // Add here to the stack trace to nail down the {@link IllegalArgumentException} exception
        // in MotionEvent that sporadically happens.
        // TODO: Remove this override method once the issue has been addressed.
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onTabChanged(final String tabId) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        final int categoryId = mEmojiCategory.getCategoryId(tabId);
        if (categoryId != mEmojiCategory.getCurrentCategoryId()) {
            setCurrentCategoryAndPageId(categoryId, 0, false /* force */);
            updateEmojiCategoryPageIdView();
        }
    }

    /** Native action-bar buttons activate through framework click dispatch for all input methods. */
    @Override
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return;
        }
        final int code = (Integer) tag;
        mKeyboardActionListener.onPressKey(code, 0, true);
        mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE,
                false /* isKeyRepeat */);
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link dev.fourwags.sumi.inputmethod.keyboard.emoji.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     */
    @Override
    public void onPressKey(final Key key) {
        final int code = key.getCode();
        mKeyboardActionListener.onPressKey(code, 0 /* repeatCount */, true /* isSinglePointer */);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link dev.fourwags.sumi.inputmethod.keyboard.emoji.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     * This may be called without any prior call to {@link OnKeyEventListener#onPressKey(Key)}.
     */
    @Override
    public void onReleaseKey(final Key key) {
        // Sumi: do not collect recently typed emoji, even in memory.
        mEmojiCategory.saveLastTypedCategoryPage();
        final int code = key.getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mKeyboardActionListener.onTextInput(key.getOutputText());
        } else {
            mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE,
                    false /* isKeyRepeat */);
        }
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private static void setupAlphabetKey(final TextView alphabetKey, final String label,
                                         final KeyDrawParams params) {
        alphabetKey.setText(label);
        alphabetKey.setTextColor(params.mFunctionalTextColor);
        alphabetKey.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize);
        alphabetKey.setTypeface(params.mTypeface);
    }

    public void startEmojiPalettes(final String switchToAlphaLabel,
                                   final KeyVisualAttributes keyVisualAttr,
                                   final KeyboardIconsSet iconSet) {
        final boolean largeKeys = DeviceProtectedUtils.getSharedPreferences(getContext())
                .getBoolean("petal_large_keys", false);
        if (mLargeKeys != largeKeys) {
            mLargeKeys = largeKeys;
            mEmojiLayoutParams = new EmojiLayoutParams(getResources(), largeKeys);
            final TypedArray attrs = getContext().obtainStyledAttributes(null,
                    R.styleable.EmojiPalettesView, mDefStyle, R.style.EmojiPalettesView);
            mEmojiCategory = new EmojiCategory(DeviceProtectedUtils.getSharedPreferences(getContext()),
                    getResources(), mEmojiLayoutSet, attrs, mEmojiLayoutParams.mLargeTargetSize);
            attrs.recycle();
            mEmojiPalettesAdapter = new EmojiPalettesAdapter(mEmojiCategory, this);
            mEmojiRecyclerView.setAdapter(mEmojiPalettesAdapter);
            setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), 0, true);
            applyTargetSizes();
        }
        final int deleteIconResId = iconSet.getIconResourceId(KeyboardIconsSet.NAME_DELETE_KEY);
        if (deleteIconResId != 0) {
            mDeleteKey.setImageResource(deleteIconResId);
        }
        final int spacebarResId = iconSet.getIconResourceId(KeyboardIconsSet.NAME_SPACE_KEY);
        if (spacebarResId != 0) {
            // TODO: Remove this workaround to place the spacebar icon.
            mSpacebarIcon.setBackgroundResource(spacebarResId);
        }
        final KeyDrawParams params = new KeyDrawParams();
        params.updateParams(mEmojiLayoutParams.getActionBarHeight(), keyVisualAttr);
        setupAlphabetKey(mAlphabetKeyLeft, switchToAlphaLabel, params);
        mPersonalMediaKey.setImageTintList(android.content.res.ColorStateList.valueOf(params.mFunctionalTextColor));
        if (mEmojiRecyclerView.getAdapter() == null) {
            mEmojiRecyclerView.setAdapter(mEmojiPalettesAdapter);
            setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), mEmojiCategory.getCurrentCategoryPageId(),
                    true /* force */);
        }
    }

    public void stopEmojiPalettes() {
        mEmojiPalettesAdapter.releaseCurrentKey(false /* withKeyRegistering */);
        mEmojiPalettesAdapter.flushPendingRecentKeys();
        mEmojiRecyclerView.setAdapter(null);
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    private void updateEmojiCategoryPageIdView() {
        if (mEmojiCategoryPageIndicatorView == null) {
            return;
        }
        mEmojiCategoryPageIndicatorView.setCategoryPageId(
                mEmojiCategory.getCurrentCategoryPageCount(),
                mEmojiCategory.getCurrentCategoryPageId(), 0.0f /* offset */);
    }

    private void setCurrentCategoryAndPageId(final int categoryId, final int categoryPageId,
                            final boolean force) {
        final int oldCategoryId = mEmojiCategory.getCurrentCategoryId();
        final int oldCategoryPageId = mEmojiCategory.getCurrentCategoryPageId();

        if (oldCategoryId == EmojiCategory.ID_RECENTS && categoryId != EmojiCategory.ID_RECENTS) {
            // Needs to save pending updates for recent keys when we get out of the recents
            // category because we don't want to move the recent emojis around while the user
            // is in the recents category.
            mEmojiPalettesAdapter.flushPendingRecentKeys();
        }

        if (force || oldCategoryId != categoryId || oldCategoryPageId != categoryPageId) {
            mEmojiCategory.setCurrentCategoryId(categoryId);
            mEmojiCategory.setCurrentCategoryPageId(categoryPageId);
            mEmojiPalettesAdapter.onCategoryChanged();
            mEmojiRecyclerView.scrollToPosition(categoryPageId);
        }

        final int newTabId = mEmojiCategory.getTabIdFromCategoryId(categoryId);
        if (force || mTabHost.getCurrentTab() != newTabId) {
            mTabHost.setCurrentTab(newTabId);
        }
    }

}
