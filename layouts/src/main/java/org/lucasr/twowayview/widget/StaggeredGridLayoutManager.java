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

import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.Lanes.LaneInfo;

public class StaggeredGridLayoutManager extends GridLayoutManager {
    private static final String LOGTAG = "StaggeredGridLayoutManager";

    private static final int DEFAULT_NUM_COLS = 2;
    private static final int DEFAULT_NUM_ROWS = 2;

    protected static class StaggeredItemEntry extends BaseLayoutManager.ItemEntry {
        private final int span;
        private final int width;
        private final int height;

        public StaggeredItemEntry(int startLane, int anchorLane, int span, int width, int height) {
            super(startLane, anchorLane);
            this.span = span;
            this.width = width;
            this.height = height;
        }

        public StaggeredItemEntry(Parcel in) {
            super(in);
            this.span = in.readInt();
            this.width = in.readInt();
            this.height = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(span);
            out.writeInt(width);
            out.writeInt(height);
        }

        public static final Parcelable.Creator<StaggeredItemEntry> CREATOR
                = new Parcelable.Creator<StaggeredItemEntry>() {
            @Override
            public StaggeredItemEntry createFromParcel(Parcel in) {
                return new StaggeredItemEntry(in);
            }

            @Override
            public StaggeredItemEntry[] newArray(int size) {
                return new StaggeredItemEntry[size];
            }
        };
    }

    public StaggeredGridLayoutManager(Context context) {
        this(context, null);
    }

    public StaggeredGridLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StaggeredGridLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, DEFAULT_NUM_COLS, DEFAULT_NUM_ROWS);
    }

    public StaggeredGridLayoutManager(Context context, Orientation orientation,
                                      int numColumns, int numRows) {
        super(context, orientation, numColumns, numRows);
    }

    @Override
    int getLaneSpanForChild(View child) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return lp.span;
    }

    @Override
    int getLaneSpanForPosition(int position) {
        final StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(position);
        if (entry == null) {
            throw new IllegalStateException("Could not find span for position " + position);
        }

        return entry.span;
    }

    @Override
    void getLaneForPosition(LaneInfo outInfo, int position, Direction direction) {
        final StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(position);
        if (entry != null) {
            outInfo.set(entry.startLane, entry.anchorLane);
            return;
        }

        outInfo.setUndefined();
    }

    @Override
    void getLaneForChild(LaneInfo outInfo, View child, Direction direction) {
        super.getLaneForChild(outInfo, child, direction);
        if (outInfo.isUndefined()) {
            getLanes().findLane(outInfo, getLaneSpanForChild(child), direction);
        }
    }

    private int getWidthUsed(View child) {
        if (!isVertical()) {
            return 0;
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int size = getLanes().getLaneSize() * lp.span;
        return getWidth() - getPaddingLeft() - getPaddingRight() - size;
    }

    private int getHeightUsed(View child) {
        if (isVertical()) {
            return 0;
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int size = getLanes().getLaneSize() * lp.span;
        return getHeight() - getPaddingTop() - getPaddingBottom() - size;
    }

    @Override
    protected void measureChild(View child) {
        measureChildWithMargins(child, getWidthUsed(child), getHeightUsed(child));
    }

    @Override
    protected void layoutChild(View child, Direction direction) {
        super.layoutChild(child, direction);

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.isItemRemoved() || lp.span == 1) {
            return;
        }

        getLaneForPosition(mTempLaneInfo, getPosition(child), direction);
        final int lane = mTempLaneInfo.startLane;

        // The parent class has already pushed the frame to
        // the main lane. Now we push it to the remaining lanes
        // within the item's span.
        getDecoratedChildFrame(child, mChildFrame);
        getLanes().pushChildFrame(mChildFrame, lane + 1, lane + lp.span, direction);
    }

    @Override
    protected void detachChild(View child, Direction direction) {
        super.detachChild(child, direction);

        final int laneSpan = getLaneSpanForChild(child);
        if (laneSpan == 1) {
            return;
        }

        getLaneForPosition(mTempLaneInfo, getPosition(child), direction);
        final int lane = mTempLaneInfo.startLane;

        // The parent class has already popped the frame from
        // the main lane. Now we pop it from the remaining lanes
        // within the item's span.
        getDecoratedChildFrame(child, mChildFrame);
        getLanes().popChildFrame(mChildFrame, lane + 1, lane + laneSpan, direction);
    }

    @Override
    void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        final boolean isVertical = isVertical();
        final Lanes lanes = getLanes();
        final Rect childFrame = new Rect();

        lanes.reset(0);

        for (int i = 0; i <= position; i++) {
            StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(i);

            if (entry != null) {
                mTempLaneInfo.set(entry.startLane, entry.anchorLane);
                lanes.getChildFrame(childFrame, entry.width, entry.height, mTempLaneInfo,
                        Direction.END);
            } else {
                final View child = recycler.getViewForPosition(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                // Temporarily add view as some item decoration might assume
                // the while being measured is attached.
                addView(child);

                // XXX: This might potentially cause stalls in the main
                // thread if the layout ends up having to measure tons of
                // child views. We might need to add different policies based
                // on known assumptions regarding certain layouts e.g. child
                // views have stable aspect ratio, lane size is fixed, etc.
                measureChild(child);

                lanes.findLane(mTempLaneInfo, lp.span, Direction.END);
                lanes.getChildFrame(childFrame, getDecoratedMeasuredWidth(child),
                        getDecoratedMeasuredHeight(child), mTempLaneInfo, Direction.END);

                entry = (StaggeredItemEntry) cacheItemEntry(child, i, mTempLaneInfo, childFrame);

                // Done, now recycle view.
                removeAndRecycleView(child, recycler);
            }

            if (i != position) {
                lanes.pushChildFrame(childFrame, entry.startLane,
                        entry.startLane + entry.span, Direction.END);
            }
        }

        getLaneForPosition(mTempLaneInfo, position, Direction.END);
        lanes.getLane(mTempLaneInfo.startLane, mTempRect);

        lanes.reset(Direction.END);
        lanes.offset(offset - (isVertical ? mTempRect.bottom : mTempRect.right));
    }

    @Override
    ItemEntry cacheItemEntry(View child, int position, LaneInfo laneInfo, Rect childFrame) {
        StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(position);
        if (entry == null) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int width = childFrame.right - childFrame.left;
            final int height = childFrame.bottom - childFrame.top;

            entry = new StaggeredItemEntry(laneInfo.startLane, laneInfo.anchorLane,
                    lp.span, width, height);
            setItemEntryForPosition(position, entry);
        }

        return entry;
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        boolean result = super.checkLayoutParams(lp);
        if (lp instanceof LayoutParams) {
            final LayoutParams staggeredLp = (LayoutParams) lp;
            result &= (staggeredLp.span >= 1 && staggeredLp.span <= getLaneCount());
        }

        return result;
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
        final LayoutParams staggeredLp = new LayoutParams((ViewGroup.MarginLayoutParams) lp);
        if (isVertical()) {
            staggeredLp.width = LayoutParams.MATCH_PARENT;
            staggeredLp.height = lp.height;
        } else {
            staggeredLp.width = lp.width;
            staggeredLp.height = LayoutParams.MATCH_PARENT;
        }

        if (lp instanceof LayoutParams) {
            final LayoutParams other = (LayoutParams) lp;
            staggeredLp.span = Math.max(1, Math.min(other.span, getLaneCount()));
        }

        return staggeredLp;
    }

    @Override
    public LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    public static class LayoutParams extends TwoWayView.LayoutParams {
        private static final int DEFAULT_SPAN = 1;

        public int span;

        public LayoutParams(int width, int height) {
            super(width, height);
            span = DEFAULT_SPAN;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.StaggeredGridViewChild);
            span = Math.max(DEFAULT_SPAN, a.getInt(R.styleable.StaggeredGridViewChild_span, -1));
            a.recycle();
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);
            init(other);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams other) {
            super(other);
            init(other);
        }

        private void init(ViewGroup.LayoutParams other) {
            if (other instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) other;
                span = lp.span;
            } else {
                span = DEFAULT_SPAN;
            }
        }
    }
}