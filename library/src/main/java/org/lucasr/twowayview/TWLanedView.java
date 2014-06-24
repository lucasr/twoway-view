/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.twowayview;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;

public abstract class TWLanedView extends TWView {
    TWLanes mLanes;
    TWLanes mLanesToRestore;

    protected static class ItemEntry implements Parcelable {
        public final int lane;

        public ItemEntry(int position) {
            this.lane = position;
        }

        public ItemEntry(Parcel in) {
            this.lane = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(lane);
        }

        public static final Parcelable.Creator<ItemEntry> CREATOR
                = new Parcelable.Creator<ItemEntry>() {
            @Override
            public ItemEntry createFromParcel(Parcel in) {
                return new ItemEntry(in);
            }

            @Override
            public ItemEntry[] newArray(int size) {
                return new ItemEntry[size];
            }
        };
    }

    SparseArray<ItemEntry> mItemEntries;
    SparseArray<ItemEntry> mItemEntriesToRestore;

    boolean mIsVertical;

    final Rect mTempRect = new Rect();

    public TWLanedView(Context context) {
        this(context, null);
    }

    public TWLanedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWLanedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIsVertical = (getOrientation() == Orientation.VERTICAL);
    }

    private SparseArray<ItemEntry> cloneItemEntries() {
        if (mItemEntries == null) {
            return null;
        }

        final SparseArray<ItemEntry> itemLanes;
        if (Build.VERSION.SDK_INT >= 14) {
            itemLanes = mItemEntries.clone();
        } else {
            itemLanes = new SparseArray<ItemEntry>();

            for (int i = 0; i < mItemEntries.size(); i++) {
                itemLanes.put(mItemEntries.keyAt(i), mItemEntries.valueAt(i));
            }
        }

        return itemLanes;
    }

    protected void forceCreateLanes() {
        mLanes = null;
        ensureLayoutState();
    }

    private boolean canUseLanes(TWLanes lanes) {
        if (lanes == null) {
            return false;
        }

        if (lanes.getOrientation() != getOrientation()) {
            return false;
        }

        if (mLanes != null && lanes.getLaneSize() != mLanes.getLaneSize()) {
            return false;
        }

        if (mLanes != null && lanes.getCount() != mLanes.getCount()) {
            return false;
        }

        return true;
    }

    private void ensureLayoutState() {
        final int laneCount = getLaneCount();
        if (laneCount == 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        if (canUseLanes(mLanesToRestore)) {
            mLanes = mLanesToRestore;
            mLanesToRestore = null;

            mItemEntries = mItemEntriesToRestore;
            mItemEntriesToRestore = null;

            return;
        }

        if (mLanes != null && mLanes.getCount() == laneCount) {
            return;
        }

        mLanes = new TWLanes(this, getLaneCount());

        if (mItemEntries == null) {
            mItemEntries = new SparseArray<ItemEntry>(10);
        } else {
            mItemEntries.clear();
        }
    }

    @Override
    protected void layoutChildren() {
        if (mLanes != null) {
            mLanes.reset();
        }

        super.layoutChildren();
    }

    @Override
    protected void resetState() {
        super.resetState();
        forceCreateLanes();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        forceCreateLanes();
    }

    @Override
    public void setOrientation(Orientation orientation) {
        final boolean changed = (getOrientation() != orientation);
        super.setOrientation(orientation);

        if (changed) {
            mIsVertical = (orientation == Orientation.VERTICAL);
            forceCreateLanes();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final LanedSavedState ss = new LanedSavedState(superState);

        final int laneCount = mLanes.getCount();
        ss.lanes = new Rect[laneCount];
        for (int i = 0; i < laneCount; i++) {
            final Rect laneRect = new Rect();
            mLanes.getLane(i, laneRect);
            ss.lanes[i] = laneRect;
        }

        ss.orientation = getOrientation();
        ss.laneSize = mLanes.getLaneSize();
        ss.itemLanes = cloneItemEntries();

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        final LanedSavedState ss = (LanedSavedState) state;

        if (ss.lanes != null && ss.laneSize > 0) {
            mLanesToRestore = new TWLanes(this, ss.orientation, ss.lanes, ss.laneSize);
            mItemEntriesToRestore = ss.itemLanes;
        }

        super.onRestoreInstanceState(ss.getSuperState());
    }

    @Override
    protected int getOuterStartEdge() {
        return mLanes.getOuterStartEdge();
    }

    @Override
    protected int getInnerStartEdge() {
        return mLanes.getInnerStartEdge();
    }

    @Override
    protected int getInnerEndEdge() {
        return mLanes.getInnerEndEdge();
    }

    @Override
    protected int getOuterEndEdge() {
        return mLanes.getOuterEndEdge();
    }

    @Override
    protected int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLanes.getLaneSize(), MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position, LayoutParams lp) {
        if (mIsVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (!mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLanes.getLaneSize(), MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected void offsetLayout(int offset) {
        mLanes.offset(offset);
    }

    @Override
    protected void moveLayoutToPosition(int position, int offset) {
        mLanes.resetToOffset(offset);
    }

    @Override
    protected void detachChildFromLayout(View child, int position, Flow flow) {
        final int spacing = (mIsVertical ? getVerticalSpacing() : getHorizontalSpacing());
        final int dimension = (mIsVertical ? child.getHeight() : child.getWidth());

        final int lane = getLaneForPosition(position, flow);
        mLanes.removeFromLane(lane, flow, dimension + spacing);
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childFrame) {
        final int lane = getLaneForPosition(position, flow);
        final int dimension = mLanes.getChildFrame(child, lane, flow, childFrame);
        mLanes.addToLane(lane, flow, dimension);

        ensureItemEntry(child, position, lane, childFrame);
    }

    protected void ensureItemEntry(View child, int position, int lane, Rect childFrame) {
        // Do nothing by default
    }

    protected abstract int getLaneCount();
    protected abstract int getLaneForPosition(int position, Flow flow);

    protected static class LanedSavedState extends SavedState {
        private Orientation orientation;
        private Rect[] lanes;
        private int laneSize;
        private SparseArray<ItemEntry> itemLanes;

        protected LanedSavedState(Parcelable superState) {
            super(superState);
        }

        private LanedSavedState(Parcel in) {
            super(in);

            orientation = Orientation.values()[in.readInt()];
            laneSize = in.readInt();

            final int laneCount = in.readInt();
            if (laneCount > 0) {
                lanes = new Rect[laneCount];
                for (int i = 0; i < laneCount; i++) {
                    lanes[i].readFromParcel(in);
                }
            }

            final int itemLanesCount = in.readInt();
            if (itemLanesCount > 0) {
                itemLanes = new SparseArray<ItemEntry>(itemLanesCount);
                for (int i = 0; i < itemLanesCount; i++) {
                    final int key = in.readInt();
                    final ItemEntry value = in.readParcelable(getClass().getClassLoader());
                    itemLanes.put(key, value);
                }
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            out.writeInt(orientation.ordinal());
            out.writeInt(laneSize);

            final int laneCount = (lanes != null ? lanes.length : 0);
            out.writeInt(laneCount);

            for (int i = 0; i < laneCount; i++) {
                lanes[i].writeToParcel(out, Rect.PARCELABLE_WRITE_RETURN_VALUE);
            }

            final int itemLanesCount = (itemLanes != null ? itemLanes.size() : 0);
            out.writeInt(itemLanesCount);

            for (int i = 0; i < itemLanesCount; i++) {
                out.writeInt(itemLanes.keyAt(i));
                out.writeParcelable(itemLanes.valueAt(i), flags);
            }
        }

        public static final Parcelable.Creator<LanedSavedState> CREATOR
                = new Parcelable.Creator<LanedSavedState>() {
            @Override
            public LanedSavedState createFromParcel(Parcel in) {
                return new LanedSavedState(in);
            }

            @Override
            public LanedSavedState[] newArray(int size) {
                return new LanedSavedState[size];
            }
        };
    }
}
