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
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import org.lucasr.twowayview.TWView;

public class TWSpannableGridLayoutManager extends TWGridLayoutManager {
    private static final String LOGTAG = "TWSpannableGridLayoutManager";

    private static final int DEFAULT_NUM_COLS = 3;
    private static final int DEFAULT_NUM_ROWS = 3;

    protected static class SpannableItemEntry extends TWLanedLayoutManager.ItemEntry {
        private final int colSpan;
        private final int rowSpan;

        public SpannableItemEntry(int lane, int colSpan, int rowSpan) {
            super(lane);
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }

        public SpannableItemEntry(Parcel in) {
            super(in);
            this.colSpan = in.readInt();
            this.rowSpan = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(colSpan);
            out.writeInt(rowSpan);
        }

        public static final Parcelable.Creator<SpannableItemEntry> CREATOR
                = new Parcelable.Creator<SpannableItemEntry>() {
            @Override
            public SpannableItemEntry createFromParcel(Parcel in) {
                return new SpannableItemEntry(in);
            }

            @Override
            public SpannableItemEntry[] newArray(int size) {
                return new SpannableItemEntry[size];
            }
        };
    }

    private final Context mContext;
    private boolean mMeasuring;

    public TWSpannableGridLayoutManager(Context context) {
        this(context, null);
    }

    public TWSpannableGridLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWSpannableGridLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, DEFAULT_NUM_COLS, DEFAULT_NUM_ROWS);
        mContext = context;
    }

    public TWSpannableGridLayoutManager(Context context, Orientation orientation,
                                        int numColumns, int numRows) {
        super(context, orientation, numColumns, numRows);
        mContext = context;
    }

    private int getChildStartInLane(int childWidth, int childHeight, int lane, Direction direction) {
        getLanes().getChildFrame(childWidth, childHeight, lane, direction, mTempRect);
        return (isVertical() ? mTempRect.top : mTempRect.left);
    }

    private int getLaneThatFitsFrame(int childWidth, int childHeight, int anchor, Direction direction,
                                     int laneSpan, Rect frame) {
        final TWLanes lanes = getLanes();
        final boolean isVertical = isVertical();

        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            lanes.getChildFrame(childWidth, childHeight, l, direction, frame);

            frame.offsetTo(isVertical ? frame.left : anchor,
                           isVertical ? anchor : frame.top);

            if (!lanes.intersects(l, laneSpan, frame)) {
                return l;
            }
        }

        return TWLanes.NO_LANE;
    }

    private int getChildLaneAndFrame(int childWith, int childHeight, int position, Direction direction,
                                     int laneSpan, Rect childFrame) {
        final TWLanes lanes = getLanes();
        int lane = TWLanes.NO_LANE;

        final ItemEntry entry = getItemEntryForPosition(position);
        if (entry != null && entry.lane != TWLanes.NO_LANE) {
            lanes.getChildFrame(childWith, childHeight, entry.lane, direction, childFrame);
            return entry.lane;
        }

        int targetEdge = (direction == Direction.END ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            final int childStart = getChildStartInLane(childWith, childHeight, l, direction);

            if ((direction == Direction.END && childStart < targetEdge) ||
                (direction == Direction.START && childStart > targetEdge)) {
                final int targetLane = getLaneThatFitsFrame(childWith, childHeight, childStart,
                        direction, laneSpan, childFrame);

                if (targetLane != TWLanes.NO_LANE) {
                    targetEdge = childStart;
                    lane = targetLane;
                }
            }
        }

        return lane;
    }

    private int getChildWidth(int colSpan) {
        return getLanes().getLaneSize() * colSpan + getHorizontalSpacing() * (colSpan - 1);
    }

    private int getChildHeight(int rowSpan) {
        return getLanes().getLaneSize() * rowSpan + getVerticalSpacing() * (rowSpan- 1);
    }

    private int getLaneSpacing(boolean isVertical) {
        return (isVertical ? getVerticalSpacing() : getHorizontalSpacing());
    }

    private static int getLaneSpan(boolean isVertical, View child) {
        return getLaneSpan(isVertical, (LayoutParams) child.getLayoutParams());
    }

    private static int getLaneSpan(boolean isVertical, LayoutParams lp) {
        return (isVertical ? lp.colSpan : lp.rowSpan);
    }

    private static int getLaneSpan(boolean isVertical, SpannableItemEntry entry) {
        return (isVertical ? entry.colSpan : entry.rowSpan);
    }

    @Override
    public boolean canScrollHorizontally() {
        return super.canScrollHorizontally() && !mMeasuring;
    }

    @Override
    public boolean canScrollVertically() {
        return super.canScrollVertically() && !mMeasuring;
    }

    @Override
    protected int getLaneForPosition(int position, Direction direction) {
        final SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(position);
        if (entry != null) {
            return entry.lane;
        }

        return TWLanes.NO_LANE;
    }

    @Override
    protected ItemEntry ensureItemEntry(View child, int position, int lane, Rect childFrame) {
        SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(position);
        if (entry == null) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int colSpan = lp.colSpan;
            final int rowSpan = lp.rowSpan;

            entry = new SpannableItemEntry(lane, colSpan, rowSpan);
            setItemEntryForPosition(position, entry);
        }

        return entry;
    }

    private int getWidthUsed(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return getWidth() - getChildWidth(lp.colSpan);
    }

    private int getHeightUsed(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return getHeight() - getChildHeight(lp.rowSpan);
    }

    @Override
    protected void measureChild(View child) {
        // XXX: This will disable scrolling while measuring this child to ensure that
        // both width and height can use MATCH_PARENT properly.
        mMeasuring = true;
        measureChildWithMargins(child, getWidthUsed(child), getHeightUsed(child));
        mMeasuring = false;
    }

    private int getFirstChildCountInLanes(int laneCount, int maxPosition) {
        final boolean isVertical = isVertical();

        int childCount = 0, i = 0;
        while (i < maxPosition && i < laneCount) {
            SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(i);
            i += (isVertical ? entry.colSpan : entry.rowSpan);
            childCount++;
        }

        return childCount;
    }

    @Override
    protected void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        final boolean isVertical = isVertical();
        final TWLanes lanes = getLanes();
        final Rect childFrame = new Rect();

        lanes.resetToOffset(0);

        for (int i = 0; i <= position; i++) {
            SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(i);
            if (entry != null) {
                lanes.getChildFrame(getChildWidth(entry.colSpan), getChildHeight(entry.rowSpan),
                        entry.lane, Direction.END, childFrame);
            } else {
                final View child = recycler.getViewForPosition(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int lane = getChildLaneAndFrame(getChildWidth(lp.colSpan),
                        getChildHeight(lp.rowSpan), i, Direction.END, getLaneSpan(isVertical, lp),
                        childFrame);

                entry = (SpannableItemEntry) ensureItemEntry(child, i, lane, childFrame);
            }

            if (i != position) {
                appendChildFrame(childFrame, Direction.END, entry.lane,
                        getLaneSpan(isVertical, entry));
            }
        }

        final SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(position);
        final int laneCount = lanes.getCount();
        if (position >= getFirstChildCountInLanes(laneCount, position)) {
            final int spacing = getLaneSpacing(isVertical);
            for (int i = entry.lane; i < laneCount; i++) {
                lanes.addToLane(i, Direction.END, spacing);
            }
        }

        lanes.resetToEnd();
        lanes.getLane(entry.lane, mTempRect);
        lanes.offset(offset - (isVertical ? mTempRect.bottom : mTempRect.right));
    }

    @Override
    protected void detachChild(View child, Direction direction) {
        final boolean isVertical = isVertical();
        final int laneSpan = getLaneSpan(isVertical, child);

        final int spacing = getLaneSpacing(isVertical);
        final int dimension =
                (isVertical ? getDecoratedMeasuredHeight(child) : getDecoratedMeasuredWidth(child));

        final TWLanes lanes = getLanes();
        final int lane = getLaneForPosition(getPosition(child), direction);
        for (int i = lane; i < lane + laneSpan; i++) {
            lanes.removeFromLane(i, direction, dimension + spacing);
        }
    }

    private void appendChildFrame(Rect childFrame, Direction direction, int lane, int laneSpan) {
        final TWLanes lanes = getLanes();
        final boolean isVertical = isVertical();

        for (int i = lane; i < lane + laneSpan; i++) {
            lanes.getLane(i, mTempRect);

            final int l, t, r, b;
            if (isVertical) {
                l = mTempRect.left;
                t = (direction == Direction.END ? mTempRect.top : childFrame.top);
                r = mTempRect.right;
                b = (direction == Direction.END ? childFrame.bottom : mTempRect.bottom);
            } else {
                l = (direction == Direction.END ? mTempRect.left : childFrame.left);
                t = mTempRect.top;
                r = (direction == Direction.END ? childFrame.right : mTempRect.right);
                b = mTempRect.bottom;
            }
            lanes.setLane(i, l, t, r, b);
        }
    }

    @Override
    protected void layoutChild(View child, Direction direction) {
        final int position = getPosition(child);
        final int laneSpan = getLaneSpan(isVertical(), child);

        final int lane = getChildLaneAndFrame(getDecoratedMeasuredWidth(child),
                getDecoratedMeasuredHeight(child), position, direction, laneSpan, mChildFrame);
        appendChildFrame(mChildFrame, direction, lane, laneSpan);

        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        layoutDecorated(child, mChildFrame.left + lp.leftMargin, mChildFrame.top + lp.topMargin,
                mChildFrame.right - lp.rightMargin, mChildFrame.bottom - lp.bottomMargin);

        ensureItemEntry(child, position, lane, mChildFrame);
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        if (lp.width != LayoutParams.MATCH_PARENT ||
            lp.height != LayoutParams.MATCH_PARENT) {
            return false;
        }

        if (lp instanceof LayoutParams) {
            final LayoutParams spannableLp = (LayoutParams) lp;

            if (isVertical()) {
                return (spannableLp.rowSpan >= 1 && spannableLp.colSpan >= 1 &&
                        spannableLp.colSpan <= getLaneCount());
            } else {
                return (spannableLp.colSpan >= 1 && spannableLp.rowSpan >= 1 &&
                        spannableLp.rowSpan <= getLaneCount());
            }
        }

        return false;
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        if (isVertical()) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        } else {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        final LayoutParams spannableLp = generateDefaultLayoutParams();

        if (lp instanceof LayoutParams) {
            final LayoutParams other = (LayoutParams) lp;
            if (isVertical()) {
                spannableLp.colSpan = Math.max(1, Math.min(other.colSpan, getLaneCount()));
                spannableLp.rowSpan = Math.max(1, other.rowSpan);
            } else {
                spannableLp.colSpan = Math.max(1, other.colSpan);
                spannableLp.rowSpan = Math.max(1, Math.min(other.rowSpan, getLaneCount()));
            }
        }

        if (lp instanceof MarginLayoutParams) {
            final MarginLayoutParams marginLp = (MarginLayoutParams) lp;
            spannableLp.leftMargin = marginLp.leftMargin;
            spannableLp.topMargin = marginLp.topMargin;
            spannableLp.rightMargin = marginLp.rightMargin;
            spannableLp.bottomMargin = marginLp.bottomMargin;
        }

        return spannableLp;
    }

    @Override
    public LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    public static class LayoutParams extends TWView.LayoutParams {
        private static final int DEFAULT_SPAN = 1;

        public int rowSpan;
        public int colSpan;

        public LayoutParams(int width, int height) {
            super(width, height);
            rowSpan = DEFAULT_SPAN;
            colSpan = DEFAULT_SPAN;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.TWSpannableGridViewChild);
            colSpan = Math.max(
                    DEFAULT_SPAN, a.getInt(R.styleable.TWSpannableGridViewChild_colSpan, -1));
            rowSpan = Math.max(
                    DEFAULT_SPAN, a.getInt(R.styleable.TWSpannableGridViewChild_rowSpan, -1));
            a.recycle();
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);

            if (other instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) other;
                rowSpan = lp.rowSpan;
                colSpan = lp.colSpan;
            } else {
                rowSpan = DEFAULT_SPAN;
                colSpan = DEFAULT_SPAN;
            }
        }
    }
}
