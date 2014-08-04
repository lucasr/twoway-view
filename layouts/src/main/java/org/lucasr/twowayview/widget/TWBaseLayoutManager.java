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

package org.lucasr.twowayview.widget;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.LayoutParams;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import org.lucasr.twowayview.TWAbsLayoutManager;

public abstract class TWBaseLayoutManager extends TWAbsLayoutManager {
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

    protected final Rect mChildFrame = new Rect();
    protected final Rect mTempRect = new Rect();

    public TWBaseLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWBaseLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TWBaseLayoutManager(Context context, Orientation orientation) {
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

    void getDecoratedChildFrame(View child, Rect childFrame) {
        childFrame.left = getDecoratedLeft(child);
        childFrame.top = getDecoratedTop(child);
        childFrame.right = getDecoratedRight(child);
        childFrame.bottom = getDecoratedBottom(child);
    }

    protected int getChildLaneAndFrame(View child, int position, Direction direction,
                                       Rect childFrame) {
        final int lane = getLaneForPosition(position, direction);

        mLanes.getChildFrame(getDecoratedMeasuredWidth(child), getDecoratedMeasuredHeight(child),
                lane, direction, childFrame);

        return lane;
    }

    boolean isVertical() {
        return (getOrientation() == Orientation.VERTICAL);
    }

    void forceCreateLanes() {
        mLanes = null;
        ensureLayoutState();
    }

    TWLanes getLanes() {
        return mLanes;
    }

    void setItemEntryForPosition(int position, ItemEntry entry) {
        mItemEntries.put(position, entry);
    }

    ItemEntry getItemEntryForPosition(int position) {
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

    void handleAdapterChange() {
        // Adapter changes is very likely to affect chain of layout
        // decisions the layout manager has made regarding where to
        // place items e.g. the lane is dynamically decided in
        // some of the built-in layouts. Clear state so that the
        // next layout pass doesn't run with bogus layout assumptions.
        mItemEntries.clear();
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

        // Still not able to create lanes, nothing we can do here,
        // just bail for now.
        if (mLanes == null) {
            return;
        }

        if (canUseLanes(mLanesToRestore)) {
            mLanes = mLanesToRestore;
            mItemEntries = mItemEntriesToRestore;
        } else {
            final int pendingPosition = getPendingScrollPosition();
            if (pendingPosition != RecyclerView.NO_POSITION) {
                moveLayoutToPosition(pendingPosition, getPendingScrollOffset(), recycler, state);
            }
        }

        mLanesToRestore = null;
        mItemEntriesToRestore = null;

        mLanes.reset(Direction.START);

        super.onLayoutChildren(recycler, state);
    }

    @Override
    protected void onLayoutScrapList(Recycler recycler, State state) {
        mLanes.save();
        super.onLayoutScrapList(recycler, state);
        mLanes.restore();
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsAdded(recyclerView, positionStart, itemCount);
        handleAdapterChange();
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        handleAdapterChange();
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

        final int laneCount = (mLanes != null ? mLanes.getCount() : 0);
        state.lanes = new Rect[laneCount];
        for (int i = 0; i < laneCount; i++) {
            final Rect laneRect = new Rect();
            mLanes.getLane(i, laneRect);
            state.lanes[i] = laneRect;
        }

        state.orientation = getOrientation();
        state.laneSize = (mLanes != null ? mLanes.getLaneSize() : 0);
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
    protected boolean canAddMoreViews(Direction direction, int limit) {
        if (direction == Direction.START) {
            return (mLanes.getInnerStart() > limit);
        } else {
            return (mLanes.getInnerEnd() < limit);
        }
    }

    private int getWidthUsed(View child) {
        final boolean isVertical = isVertical();
        if (!isVertical) {
            return 0;
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int size = (isVertical ? mLanes.getLaneSize() : lp.width);
        return getWidth() - getPaddingLeft() - getPaddingRight() - size;
    }

    private int getHeightUsed(View child) {
        final boolean isVertical = isVertical();
        if (isVertical) {
            return 0;
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int size = (isVertical ? lp.height : mLanes.getLaneSize());
        return getHeight() - getPaddingTop() - getPaddingBottom() - size;
    }

    @Override
    protected void measureChild(View child) {
        measureChildWithMargins(child, getWidthUsed(child), getHeightUsed(child));
    }

    @Override
    protected void layoutChild(View child, Direction direction) {
        final int position = getPosition(child);

        final int lane = getChildLaneAndFrame(child, position, direction, mChildFrame);

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (!lp.isItemRemoved()) {
            mLanes.pushChildFrame(lane, direction, mChildFrame);
        }

        layoutDecorated(child, mChildFrame.left, mChildFrame.top, mChildFrame.right,
                mChildFrame.bottom);

        cacheItemEntry(child, position, lane, mChildFrame);
    }

    @Override
    protected void detachChild(View child, Direction direction) {
        final int lane = getLaneForPosition(getPosition(child), direction);

        getDecoratedChildFrame(child, mChildFrame);
        mLanes.popChildFrame(lane, direction, mChildFrame);
    }

    void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        mLanes.reset(offset);
    }

    ItemEntry cacheItemEntry(View child, int position, int lane, Rect childFrame) {
        // Do nothing by default
        return null;
    }

    @Override
    public boolean checkLayoutParams(LayoutParams lp) {
        if (isVertical()) {
            return (lp.width == LayoutParams.MATCH_PARENT);
        } else {
            return (lp.height == LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        if (isVertical()) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        final LayoutParams lanedLp = new LayoutParams((MarginLayoutParams) lp);
        if (isVertical()) {
            lanedLp.width = LayoutParams.MATCH_PARENT;
            lanedLp.height = lp.height;
        } else {
            lanedLp.width = lp.width;
            lanedLp.height = LayoutParams.MATCH_PARENT;
        }

        return lanedLp;
    }

    @Override
    public LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    abstract int getLaneCount();
    abstract int getLaneForPosition(int position, Direction direction);

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
