/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import dev.fourwags.sumi.R;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class EmojiPalettesAdapter extends RecyclerView.Adapter<EmojiPalettesAdapter.ViewHolder> {
    private final OnKeyEventListener mListener;
    private final EmojiCategory mEmojiCategory;
    private final Set<EmojiPageKeyboardView> mActiveViews =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private int mItemCount;

    EmojiPalettesAdapter(EmojiCategory category, OnKeyEventListener listener) {
        mEmojiCategory = category;
        mListener = listener;
        mItemCount = category.getCurrentCategoryPageCount();
    }

    public void flushPendingRecentKeys() {
        // Sumi never collects typed emoji or stores a recent-emoji history.
    }

    public void onPageScrolled() {
        releaseCurrentKey(false);
    }

    public void releaseCurrentKey(boolean withKeyRegistering) {
        for (EmojiPageKeyboardView view : mActiveViews) {
            if (withKeyRegistering) view.releaseCurrentKey(true);
            view.cancelPendingInput();
        }
    }

    public void onCategoryChanged() {
        releaseCurrentKey(false);
        int oldCount = mItemCount;
        mItemCount = mEmojiCategory.getCurrentCategoryPageCount();
        if (oldCount > 0) notifyItemRangeRemoved(0, oldCount);
        if (mItemCount > 0) notifyItemRangeInserted(0, mItemCount);
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        EmojiPageKeyboardView view = (EmojiPageKeyboardView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.emoji_keyboard_page, parent, false);
        return new ViewHolder(view);
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmojiPageKeyboardView view = holder.view;
        view.cancelPendingInput();
        view.setKeyboard(mEmojiCategory.getKeyboardFromAdapterPosition(position));
        view.setOnKeyEventListener(mListener);
        mActiveViews.add(view);
    }

    @Override public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        holder.view.cancelPendingInput();
        super.onViewDetachedFromWindow(holder);
    }

    @Override public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.view.cancelPendingInput();
        holder.view.setOnKeyEventListener(null);
        holder.view.deallocateMemory();
        mActiveViews.remove(holder.view);
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return mItemCount; }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final EmojiPageKeyboardView view;
        ViewHolder(EmojiPageKeyboardView view) { super(view); this.view = view; }
    }
}
