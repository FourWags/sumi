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

import android.content.res.Resources;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;
import dev.fourwags.sumi.R;
import dev.fourwags.sumi.inputmethod.latin.utils.ResourceUtils;

final class EmojiLayoutParams {
    private static final int DEFAULT_KEYBOARD_ROWS = 4;

    public final int mEmojiListHeight;
    private final int mEmojiListBottomMargin;
    public final int mEmojiKeyboardHeight;
    private final int mEmojiCategoryPageIdViewHeight;
    public final int mEmojiActionBarHeight;
    public final int mKeyVerticalGap;
    private final int mKeyHorizontalGap;
    private final int mBottomPadding;
    private final int mTopPadding;
    public final int mLargeTargetSize;
    public final int mCategoryHeight;
    private final int mMinimumActionTarget;
    private final int mMinimumActionBar;

    public EmojiLayoutParams(final Resources res, final boolean largeKeys) {
        mMinimumActionTarget=(int)Math.ceil(48*res.getDisplayMetrics().density);
        mMinimumActionBar=mMinimumActionTarget+(int)Math.ceil(2*res.getDisplayMetrics().density);
        mLargeTargetSize = largeKeys ? (int) Math.ceil(56 * res.getDisplayMetrics().density) : 0;
        mCategoryHeight = largeKeys ? mLargeTargetSize
                : res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
        final int defaultKeyboardHeight = largeKeys ? ResourceUtils.getDefaultKeyboardHeight(res)
                : ResourceUtils.getRegularKeyboardHeight(res);
        final int defaultKeyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        mKeyVerticalGap = (int) res.getFraction(R.fraction.config_key_vertical_gap_holo,
                defaultKeyboardHeight, defaultKeyboardHeight);
        mBottomPadding = (int) res.getFraction(R.fraction.config_keyboard_bottom_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight);
        mTopPadding = (int) res.getFraction(R.fraction.config_keyboard_top_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight);
        mKeyHorizontalGap = (int) (res.getFraction(R.fraction.config_key_horizontal_gap_holo,
                defaultKeyboardWidth, defaultKeyboardWidth));
        mEmojiCategoryPageIdViewHeight =
                (int) (res.getDimension(R.dimen.config_emoji_category_page_id_height));
        final int baseheight = defaultKeyboardHeight - mBottomPadding - mTopPadding
                + mKeyVerticalGap;
        mEmojiActionBarHeight = largeKeys ? mLargeTargetSize : Math.max(mMinimumActionBar,baseheight / DEFAULT_KEYBOARD_ROWS
                - (mKeyVerticalGap - mBottomPadding) / 2);
        mEmojiListBottomMargin = 0;
        // Whole rows at rest prevent a small, clipped final row from becoming a touch target.
        mEmojiKeyboardHeight = largeKeys ? Math.max(1,
                (defaultKeyboardHeight - mEmojiActionBarHeight - mEmojiCategoryPageIdViewHeight)
                        / mLargeTargetSize) * mLargeTargetSize
                : defaultKeyboardHeight - mEmojiActionBarHeight - mEmojiCategoryPageIdViewHeight - 1;
        mEmojiListHeight = mEmojiKeyboardHeight;

    }

    public void setEmojiListProperties(final RecyclerView vp) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) vp.getLayoutParams();
        lp.height = mEmojiKeyboardHeight;
        lp.bottomMargin = mEmojiListBottomMargin;
        vp.setLayoutParams(lp);
    }

    public void setCategoryPageIdViewProperties(final View v) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.height = mEmojiCategoryPageIdViewHeight;
        v.setLayoutParams(lp);
    }

    public int getActionBarHeight() {
        return mLargeTargetSize > 0 ? mEmojiActionBarHeight : Math.max(mMinimumActionBar,mEmojiActionBarHeight - mBottomPadding);
    }

    public void setActionBarProperties(final LinearLayout ll) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ll.getLayoutParams();
        lp.height = getActionBarHeight();
        lp.weight = 0;
        ll.setLayoutParams(lp);
    }

    public void setKeyProperties(final View v) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (mLargeTargetSize > 0) {
            lp.width = v.getId() == R.id.emoji_keyboard_space ? 0 : mLargeTargetSize;
            lp.weight = v.getId() == R.id.emoji_keyboard_space ? 1 : 0;
        } else {
            lp.width = v.getId() == R.id.emoji_keyboard_space ? 0 : mMinimumActionTarget;
            lp.weight = v.getId() == R.id.emoji_keyboard_space ? 1 : 0;
        }
        lp.leftMargin = mKeyHorizontalGap / 2;
        lp.rightMargin = mKeyHorizontalGap / 2;
        v.setLayoutParams(lp);
    }
}
