/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * This code is based on Android's StaggeredLayoutManager's
 * LazySpanLookup class.
 *
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

package org.lucasr.twowayview.widget;

import android.util.Log;

import java.util.Arrays;

import org.lucasr.twowayview.widget.BaseLayoutManager.ItemEntry;

class ItemEntries {
    private static final int MIN_SIZE = 10;

    private ItemEntry[] mItemEntries;
    private int mAdapterSize;
    private boolean mRestoringItem;

    private int sizeForPosition(int position) {
        int len = mItemEntries.length;
        while (len <= position) {
            len *= 2;
        }

        // We don't apply any constraints while restoring
        // item entries.
        if (!mRestoringItem && len > mAdapterSize) {
            len = mAdapterSize;
        }

        return len;
    }

    private void ensureSize(int position) {
        if (mItemEntries == null) {
            mItemEntries = new ItemEntry[Math.max(position, MIN_SIZE) + 1];
            Arrays.fill(mItemEntries, null);
        } else if (position >= mItemEntries.length) {
            ItemEntry[] oldItemEntries = mItemEntries;
            mItemEntries = new ItemEntry[sizeForPosition(position)];
            System.arraycopy(oldItemEntries, 0, mItemEntries, 0, oldItemEntries.length);
            Arrays.fill(mItemEntries, oldItemEntries.length, mItemEntries.length, null);
        }
    }

    public ItemEntry getItemEntry(int position) {
        if (mItemEntries == null || position >= mItemEntries.length) {
            return null;
        }

        return mItemEntries[position];
    }

    public void putItemEntry(int position, ItemEntry entry) {
        ensureSize(position);
        mItemEntries[position] = entry;
    }

    public void restoreItemEntry(int position, ItemEntry entry) {
        mRestoringItem = true;
        putItemEntry(position, entry);
        mRestoringItem = false;
    }

    public int size() {
        return (mItemEntries != null ? mItemEntries.length : 0);
    }

    public void setAdapterSize(int adapterSize) {
        mAdapterSize = adapterSize;
    }

    public void invalidateItemLanesAfter(int position) {
        if (mItemEntries == null || position >= mItemEntries.length) {
            return;
        }

        for (int i = position; i < mItemEntries.length; i++) {
            final ItemEntry entry = mItemEntries[i];
            if (entry != null) {
                entry.invalidateLane();
            }
        }
    }

    public void clear() {
        if (mItemEntries != null) {
            Arrays.fill(mItemEntries, null);
        }
    }

    void offsetForRemoval(int positionStart, int itemCount) {
        if (mItemEntries == null || positionStart >= mItemEntries.length) {
            return;
        }

        ensureSize(positionStart + itemCount);

        System.arraycopy(mItemEntries, positionStart + itemCount, mItemEntries, positionStart,
                mItemEntries.length - positionStart - itemCount);
        Arrays.fill(mItemEntries, mItemEntries.length - itemCount, mItemEntries.length, null);
    }

    void offsetForAddition(int positionStart, int itemCount) {
        if (mItemEntries == null || positionStart >= mItemEntries.length) {
            return;
        }

        ensureSize(positionStart + itemCount);

        System.arraycopy(mItemEntries, positionStart, mItemEntries, positionStart + itemCount,
                mItemEntries.length - positionStart - itemCount);
        Arrays.fill(mItemEntries, positionStart, positionStart + itemCount, null);
    }
}
