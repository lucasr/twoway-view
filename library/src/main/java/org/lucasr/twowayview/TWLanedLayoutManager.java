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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public abstract class TWLanedLayoutManager extends TWLayoutManager {
    private static final String LOGTAG = "TWLanedLayoutManager";

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

        public static final Creator<ItemEntry> CREATOR
                = new Creator<ItemEntry>() {
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

    private TWLanes mLanes;
    private TWLanes mLanesToRestore;

    private SparseArray<ItemEntry> mItemEntries;
    private SparseArray<ItemEntry> mItemEntriesToRestore;

    private int mHorizontalSpacing;
    private int mVerticalSpacing;

    protected final Rect mTempRect = new Rect();

    public TWLanedLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWLanedLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.TWLanedLayoutManager, defStyle, 0);

        final int indexCount = a.getIndexCount();
        for (int i = 0; i < indexCount; i++) {
            final int attr = a.getIndex(i);

            if (attr == R.styleable.TWLanedLayoutManager_android_horizontalSpacing) {
                final int spacing = a.getDimensionPixelSize(attr, -1);
                if (spacing >= 0) {
                    setHorizontalSpacing(spacing);
                }
            } else if (attr == R.styleable.TWLanedLayoutManager_android_verticalSpacing) {
                final int spacing = a.getDimensionPixelSize(attr, -1);
                if (spacing >= 0) {
                    setVerticalSpacing(spacing);
                }
            }
        }

        a.recycle();
    }

    public TWLanedLayoutManager(Context context, Orientation orientation) {
        super(context, orientation);
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

    protected boolean isVertical() {
        return (getOrientation() == Orientation.VERTICAL);
    }

    protected void forceCreateLanes() {
        mLanes = null;
        ensureLayoutState();
    }

    protected TWLanes getLanes() {
        return mLanes;
    }

    protected void setItemEntryForPosition(int position, ItemEntry entry) {
        mItemEntries.put(position, entry);
    }

    protected ItemEntry getItemEntryForPosition(int position) {
        return mItemEntries.get(position, null);
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

    protected void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        mLanes.resetToOffset(offset);
    }

    @Override
    public void onAdapterChanged(Adapter oldAdapter, Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        forceCreateLanes();
    }

    @Override
    public void offsetChildrenHorizontal(int offset) {
        if (!isVertical()) {
            mLanes.offset(offset);
        }

        super.offsetChildrenHorizontal(offset);
    }

    @Override
    public void offsetChildrenVertical(int offset) {
        super.offsetChildrenVertical(offset);

        if (isVertical()) {
            mLanes.offset(offset);
        }
    }

    @Override
    public void onLayoutChildren(Recycler recycler, State state) {
        ensureLayoutState();

        final int pendingPosition = getPendingScrollPosition();
        final int pendingOffset = getPendingScrollOffset();

        if (canUseLanes(mLanesToRestore)) {
            mLanes = mLanesToRestore;
            mItemEntries = mItemEntriesToRestore;
        }

        if (pendingPosition != RecyclerView.NO_POSITION &&
            pendingPosition != getFirstVisiblePosition()) {
            moveLayoutToPosition(pendingPosition, pendingOffset, recycler, state);
        }

        mLanesToRestore = null;
        mItemEntriesToRestore = null;

        mLanes.resetToStart();

        super.onLayoutChildren(recycler, state);
    }

    @Override
    public void setOrientation(Orientation orientation) {
        final boolean changed = (getOrientation() != orientation);
        super.setOrientation(orientation);

        if (changed) {
            forceCreateLanes();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final LanedSavedState state = new LanedSavedState(superState);

        final int laneCount = mLanes.getCount();
        state.lanes = new Rect[laneCount];
        for (int i = 0; i < laneCount; i++) {
            final Rect laneRect = new Rect();
            mLanes.getLane(i, laneRect);
            state.lanes[i] = laneRect;
        }

        state.orientation = getOrientation();
        state.laneSize = mLanes.getLaneSize();
        state.itemEntries = cloneItemEntries();

        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        final LanedSavedState ss = (LanedSavedState) state;

        if (ss.lanes != null && ss.laneSize > 0) {
            mLanesToRestore = new TWLanes(this, ss.orientation, ss.lanes, ss.laneSize);
            mItemEntriesToRestore = ss.itemEntries;
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
    protected int getChildWidthMeasureSpec(View child, int position) {
        final LayoutParams lp = child.getLayoutParams();
        final boolean isVertical = isVertical();

        if (!isVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        } else if (isVertical) {
            return View.MeasureSpec.makeMeasureSpec(mLanes.getLaneSize(), View.MeasureSpec.EXACTLY);
        } else {
            return View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position) {
        final LayoutParams lp = child.getLayoutParams();
        final boolean isVertical = isVertical();

        if (isVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        } else if (!isVertical) {
            return View.MeasureSpec.makeMeasureSpec(mLanes.getLaneSize(), View.MeasureSpec.EXACTLY);
        } else {
            return View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected void detachChildFromLayout(View child, int position, Flow flow) {
        final boolean isVertical = isVertical();
        final int spacing = (isVertical ? getVerticalSpacing() : getHorizontalSpacing());
        final int dimension = (isVertical ? child.getHeight() : child.getWidth());

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

    protected ItemEntry ensureItemEntry(View child, int position, int lane, Rect childFrame) {
        // Do nothing by default
        return null;
    }

    protected abstract int getLaneCount();
    protected abstract int getLaneForPosition(int position, Flow flow);

    public void setHorizontalSpacing(int horizontalSpacing) {
        if (mHorizontalSpacing == horizontalSpacing) {
            return;
        }

        mHorizontalSpacing = horizontalSpacing;
        requestLayout();
    }

    public int getHorizontalSpacing() {
        return mHorizontalSpacing;
    }

    public void setVerticalSpacing(int verticalSpacing) {
        if (mVerticalSpacing == verticalSpacing) {
            return;
        }

        mVerticalSpacing = verticalSpacing;
        requestLayout();
    }

    public int getVerticalSpacing() {
        return mVerticalSpacing;
    }

    protected static class LanedSavedState extends SavedState {
        private Orientation orientation;
        private Rect[] lanes;
        private int laneSize;
        private SparseArray<ItemEntry> itemEntries;

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
                itemEntries = new SparseArray<ItemEntry>(itemLanesCount);
                for (int i = 0; i < itemLanesCount; i++) {
                    final int key = in.readInt();
                    final ItemEntry value = in.readParcelable(getClass().getClassLoader());
                    itemEntries.put(key, value);
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

            final int itemLanesCount = (itemEntries != null ? itemEntries.size() : 0);
            out.writeInt(itemLanesCount);

            for (int i = 0; i < itemLanesCount; i++) {
                out.writeInt(itemEntries.keyAt(i));
                out.writeParcelable(itemEntries.valueAt(i), flags);
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
