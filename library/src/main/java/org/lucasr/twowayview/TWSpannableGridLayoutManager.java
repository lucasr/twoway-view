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
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;

public class TWSpannableGridLayoutManager extends TWGridLayoutManager {
    private static final String LOGTAG = "TWSpannableGridView";

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

    private int getChildStartInLane(View child, int lane, Flow flow) {
        getLanes().getChildFrame(child, lane, flow, mTempRect);
        return (isVertical() ? mTempRect.top : mTempRect.left);
    }

    private int getLaneThatFitsFrame(View child, int anchor, Flow flow,
                                     int laneSpan, Rect frame) {
        final TWLanes lanes = getLanes();
        final boolean isVertical = isVertical();

        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            lanes.getChildFrame(child, l, flow, frame);

            frame.offsetTo(isVertical ? frame.left : anchor,
                           isVertical ? anchor : frame.top);

            if (!lanes.intersects(l, laneSpan, frame)) {
                return l;
            }
        }

        return TWLanes.NO_LANE;
    }

    private int getChildLaneAndFrame(View child, int position, Flow flow,
                                     int laneSpan, Rect childFrame) {
        final TWLanes lanes = getLanes();
        int lane = TWLanes.NO_LANE;

        final ItemEntry entry = getItemEntryForPosition(position);
        if (entry != null && entry.lane != TWLanes.NO_LANE) {
            lanes.getChildFrame(child, entry.lane, flow, childFrame);
            return entry.lane;
        }

        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            final int childStart = getChildStartInLane(child, l, flow);

            if ((flow == Flow.FORWARD && childStart < targetEdge) ||
                (flow == Flow.BACKWARD && childStart > targetEdge)) {

                final int targetLane =
                        getLaneThatFitsFrame(child, childStart, flow, laneSpan, childFrame);

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
    protected int getLaneForPosition(int position, Flow flow) {
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

    @Override
    protected int getChildWidthMeasureSpec(View child, int position) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return MeasureSpec.makeMeasureSpec(getChildWidth(lp.colSpan), MeasureSpec.EXACTLY);
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return MeasureSpec.makeMeasureSpec(getChildHeight(lp.rowSpan), MeasureSpec.EXACTLY);
    }

    @Override
    protected void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        if (moveLayoutToVisiblePosition(position, offset)) {
            return;
        }

        final TWLanes lanes = getLanes();
        final boolean isVertical = isVertical();

        lanes.resetToOffset(0);
        final Rect childFrame = new Rect();

        for (int i = 0; i <= position; i++) {
            SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(i);
            if (entry != null) {
                lanes.getChildFrame(getChildWidth(entry.colSpan), getChildHeight(entry.rowSpan),
                        entry.lane, Flow.FORWARD, childFrame);
            } else {
                final View child = recycler.getViewForPosition(i);

                final int lane = getChildLaneAndFrame(child, i, Flow.FORWARD,
                        getLaneSpan(isVertical, child), childFrame);

                entry = (SpannableItemEntry) ensureItemEntry(child, i, lane, childFrame);
            }

            if (i != position) {
                appendChildFrame(childFrame, Flow.FORWARD, entry.lane,
                        getLaneSpan(isVertical, entry));
            }
        }

        lanes.resetToEnd();
        lanes.getLane(getLaneForPosition(position, Flow.FORWARD), mTempRect);
        lanes.offset(offset - (isVertical ? mTempRect.bottom : mTempRect.right));
    }

    @Override
    protected void detachChildFromLayout(View child, int position, Flow flow) {
        final boolean isVertical = isVertical();
        final int laneSpan = getLaneSpan(isVertical, child);

        final int spacing = getLaneSpacing(isVertical);
        final int dimension = (isVertical ? child.getHeight() : child.getWidth());

        final TWLanes lanes = getLanes();
        final int lane = getLaneForPosition(position, flow);
        for (int i = lane; i < lane + laneSpan; i++) {
            lanes.removeFromLane(i, flow, dimension + spacing);
        }
    }

    private void appendChildFrame(Rect childFrame, Flow flow, int lane, int laneSpan) {
        final TWLanes lanes = getLanes();
        final boolean isVertical = isVertical();

        for (int i = lane; i < lane + laneSpan; i++) {
            lanes.getLane(i, mTempRect);

            final int l, t, r, b;
            if (isVertical) {
                l = mTempRect.left;
                t = (flow == Flow.FORWARD ? mTempRect.top : childFrame.top);
                r = mTempRect.right;
                b = (flow == Flow.FORWARD ? childFrame.bottom : mTempRect.bottom);
            } else {
                l = (flow == Flow.FORWARD ? mTempRect.left : childFrame.left);
                t = mTempRect.top;
                r = (flow == Flow.FORWARD ? childFrame.right : mTempRect.right);
                b = mTempRect.bottom;
            }
            lanes.setLane(i, l, t, r, b);
        }
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childFrame) {
        final int laneSpan = getLaneSpan(isVertical(), child);

        final int lane = getChildLaneAndFrame(child, position, flow, laneSpan, childFrame);
        appendChildFrame(childFrame, flow, lane, laneSpan);

        ensureItemEntry(child, position, lane, childFrame);
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        return (lp != null && lp instanceof LayoutParams);
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
        return new LayoutParams(lp);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
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
            }
        }
    }
}
