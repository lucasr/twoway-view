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
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import org.lucasr.twowayview.TwoWayLayoutManager;
import org.lucasr.twowayview.widget.Lanes.LaneInfo;

public abstract class BaseLayoutManager extends TwoWayLayoutManager {
    private static final String LOGTAG = "BaseLayoutManager";

    protected static class ItemEntry implements Parcelable {
        public final int startLane;
        public final int anchorLane;

        private int[] spanMargins;

        public ItemEntry(int startLane, int anchorLane) {
            this.startLane = startLane;
            this.anchorLane = anchorLane;
        }

        public ItemEntry(Parcel in) {
            startLane = in.readInt();
            anchorLane = in.readInt();

            final int marginCount = in.readInt();
            if (marginCount > 0) {
                spanMargins = new int[marginCount];
                for (int i = 0; i < marginCount; i++) {
                    spanMargins[i] = in.readInt();
                }
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(startLane);
            out.writeInt(anchorLane);

            final int marginCount = (spanMargins != null ? spanMargins.length : 0);
            out.writeInt(marginCount);

            for (int i = 0; i < marginCount; i++) {
                out.writeInt(spanMargins[i]);
            }
        }

        private boolean hasSpanMargins() {
            return (spanMargins != null);
        }

        private int getSpanMargin(int index) {
            if (spanMargins == null) {
                return 0;
            }

            return spanMargins[index];
        }

        private void setSpanMargin(int index, int margin, int span) {
            if (spanMargins == null) {
                spanMargins = new int[span];
            }

            spanMargins[index] = margin;
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

    private Lanes mLanes;
    private Lanes mLanesToRestore;

    private SparseArray<ItemEntry> mItemEntries;
    private SparseArray<ItemEntry> mItemEntriesToRestore;

    protected final Rect mChildFrame = new Rect();
    protected final Rect mTempRect = new Rect();
    protected final LaneInfo mTempLaneInfo = new LaneInfo();

    public BaseLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BaseLayoutManager(Orientation orientation) {
        super(orientation);
    }

    protected void pushChildFrame(ItemEntry entry, Rect childFrame, int lane, int laneSpan,
                                  Direction direction) {
        final boolean shouldSetMargins = (direction == Direction.END &&
                                          entry != null && !entry.hasSpanMargins());

        for (int i = lane; i < lane + laneSpan; i++) {
            final int spanMargin;
            if (entry != null && direction != Direction.END) {
                spanMargin = entry.getSpanMargin(i - lane);
            } else {
                spanMargin = 0;
            }

            final int margin = mLanes.pushChildFrame(childFrame, i, spanMargin, direction);
            if (laneSpan > 1 && shouldSetMargins) {
                entry.setSpanMargin(i - lane, margin, laneSpan);
            }
        }
    }

    private void popChildFrame(ItemEntry entry, Rect childFrame, int lane, int laneSpan,
                               Direction direction) {
        for (int i = lane; i < lane + laneSpan; i++) {
            final int spanMargin;
            if (entry != null && direction != Direction.END) {
                spanMargin = entry.getSpanMargin(i - lane);
            } else {
                spanMargin = 0;
            }

            mLanes.popChildFrame(childFrame, i, spanMargin, direction);
        }
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

    boolean isVertical() {
        return (getOrientation() == Orientation.VERTICAL);
    }

    void forceCreateLanes() {
        mLanes = null;
        ensureLayoutState();
    }

    Lanes getLanes() {
        return mLanes;
    }

    void setItemEntryForPosition(int position, ItemEntry entry) {
        if (mItemEntries != null) {
            mItemEntries.put(position, entry);
        }
    }

    ItemEntry getItemEntryForPosition(int position) {
        return (mItemEntries != null ? mItemEntries.get(position, null) : null);
    }

    void clearItemEntries(){
        if (mItemEntries != null) {
            mItemEntries.clear();
        }
    }

    private boolean canUseLanes(Lanes lanes) {
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
        clearItemEntries();
    }

    private void ensureLayoutState() {
        final int laneCount = getLaneCount();
        if (laneCount == 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        if (mLanes != null && mLanes.getCount() == laneCount) {
            return;
        }

        mLanes = new Lanes(this, getLaneCount());

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
            if (pendingPosition != RecyclerView.NO_POSITION &&
                    pendingPosition >= 0 && pendingPosition < state.getItemCount()) {
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
    public void onItemsChanged(RecyclerView recyclerView) {
        super.onItemsChanged(recyclerView);
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
            mLanesToRestore = new Lanes(this, ss.orientation, ss.lanes, ss.laneSize);
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
        if (!isVertical()) {
            return 0;
        }

        final int size = getLanes().getLaneSize() * getLaneSpanForChild(child);
        return getWidth() - getPaddingLeft() - getPaddingRight() - size;
    }

    private int getHeightUsed(View child) {
        if (isVertical()) {
            return 0;
        }

        final int size = getLanes().getLaneSize() * getLaneSpanForChild(child);
        return getHeight() - getPaddingTop() - getPaddingBottom() - size;
    }

    void measureChildWithMargins(View child) {
        measureChildWithMargins(child, getWidthUsed(child), getHeightUsed(child));
    }

    @Override
    protected void measureChild(View child, Direction direction) {
        cacheChildLaneAndSpan(child, direction);
        measureChildWithMargins(child);
    }

    @Override
    protected void layoutChild(View child, Direction direction) {
        getLaneForChild(mTempLaneInfo, child, direction);

        mLanes.getChildFrame(mChildFrame, getDecoratedMeasuredWidth(child),
                getDecoratedMeasuredHeight(child), mTempLaneInfo, direction);
        final ItemEntry entry = cacheChildFrame(child, mChildFrame);

        layoutDecorated(child, mChildFrame.left, mChildFrame.top, mChildFrame.right,
                mChildFrame.bottom);

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (!lp.isItemRemoved()) {
            pushChildFrame(entry, mChildFrame, mTempLaneInfo.startLane,
                    getLaneSpanForChild(child), direction);
        }
    }

    @Override
    protected void detachChild(View child, Direction direction) {
        final int position = getPosition(child);
        getLaneForPosition(mTempLaneInfo, position, direction);
        getDecoratedChildFrame(child, mChildFrame);

        popChildFrame(getItemEntryForPosition(position), mChildFrame, mTempLaneInfo.startLane,
                getLaneSpanForChild(child), direction);
    }

    void getLaneForChild(LaneInfo outInfo, View child, Direction direction) {
        getLaneForPosition(outInfo, getPosition(child), direction);
    }

    int getLaneSpanForChild(View child) {
        return 1;
    }

    int getLaneSpanForPosition(int position) {
        return 1;
    }

    ItemEntry cacheChildLaneAndSpan(View child, Direction direction) {
        // Do nothing by default.
        return null;
    }

    ItemEntry cacheChildFrame(View child, Rect childFrame) {
        // Do nothing by default.
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
    abstract void getLaneForPosition(LaneInfo outInfo, int position, Direction direction);
    abstract void moveLayoutToPosition(int position, int offset, Recycler recycler, State state);

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
                    final Rect lane = new Rect();
                    lane.readFromParcel(in);
                    lanes[i] = lane;
                }
            }

            final int itemEntriesCount = in.readInt();
            if (itemEntriesCount > 0) {
                itemEntries = new SparseArray<ItemEntry>(itemEntriesCount);
                for (int i = 0; i < itemEntriesCount; i++) {
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

            final int itemEntriesCount = (itemEntries != null ? itemEntries.size() : 0);
            out.writeInt(itemEntriesCount);

            for (int i = 0; i < itemEntriesCount; i++) {
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
