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
import android.util.SparseArray;

import java.util.Arrays;

import org.lucasr.twowayview.widget.BaseLayoutManager.ItemEntry;

class ItemEntries {

    private SparseArray<ItemEntry> mItemEntries = new SparseArray<ItemEntry>();

    public ItemEntry getItemEntry(int position) {
        return mItemEntries.get(position);
    }

    public void putItemEntry(int position, ItemEntry entry) {
        mItemEntries.put(position,entry);
    }

    public void restoreItemEntry(int position, ItemEntry entry) {
        putItemEntry(position, entry);
    }


    /**
     * From android.util.SparseArray:
     *
     * The keys corresponding to indices in ascending order are guaranteed to be in ascending
     * order, e.g., keyAt(0) will return the smallest key and keyAt(size()-1) will return the
     * largest key.
     */

    public int size() {
        return (mItemEntries.size()==0 ? 0 : mItemEntries.keyAt(mItemEntries.size()-1));
    }

    public void invalidateItemLanesAfter(int position) {
        if (mItemEntries.size()==0 || position > mItemEntries.keyAt(mItemEntries.size()-1)) {
            return;
        }

        for (int i = position; i < mItemEntries.size(); i++) {
            final ItemEntry entry = mItemEntries.valueAt(i);
            entry.invalidateLane();
        }
    }

    public void clear() {
        mItemEntries.clear();
    }

    void offsetForRemoval(int positionStart, int itemCount) {

        for(int n = 0; n < (mItemEntries.size() - positionStart - itemCount); n++) {

            int actualPosition = positionStart+itemCount+n;
            int movedPosition = positionStart+n;

            ItemEntry temp = mItemEntries.get(actualPosition);
            mItemEntries.remove(actualPosition);
            mItemEntries.put(movedPosition, temp);

        }

    }

    void offsetForAddition(int positionStart, int itemCount) {

        for(int n = 0; n < itemCount; n++) {

            int actualPosition = positionStart+n;
            int movedPosition = positionStart+itemCount+n;

            ItemEntry temp = mItemEntries.get(actualPosition);
            mItemEntries.remove(actualPosition);
            mItemEntries.put(movedPosition, temp);

        }

    }
}
