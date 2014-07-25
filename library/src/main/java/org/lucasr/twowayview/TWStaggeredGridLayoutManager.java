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
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.view.View;

public class TWStaggeredGridLayoutManager extends TWGridLayoutManager {
    private static final String LOGTAG = "TWStaggeredGridLayoutManager";

    private static final int DEFAULT_NUM_COLS = 2;
    private static final int DEFAULT_NUM_ROWS = 2;

    protected static class StaggeredItemEntry extends TWLanedLayoutManager.ItemEntry {
        private final int width;
        private final int height;

        public StaggeredItemEntry(int lane, int width, int height) {
            super(lane);
            this.width = width;
            this.height = height;
        }

        public StaggeredItemEntry(Parcel in) {
            super(in);
            this.width = in.readInt();
            this.height = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
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

    public TWStaggeredGridLayoutManager(Context context) {
        this(context, null);
    }

    public TWStaggeredGridLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWStaggeredGridLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, DEFAULT_NUM_COLS, DEFAULT_NUM_ROWS);
    }

    public TWStaggeredGridLayoutManager(Context context, Orientation orientation,
                                        int numColumns, int numRows) {
        super(context, orientation, numColumns, numRows);
    }

    @Override
    protected int getLaneForPosition(int position, Flow flow) {
        int lane = TWLanes.NO_LANE;

        final StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(position);
        if (entry != null) {
            lane = entry.lane;
        }

        if (lane != TWLanes.NO_LANE) {
            return lane;
        }

        final TWLanes lanes = getLanes();
        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        final int laneCount = lanes.getCount();
        for (int i = 0; i < laneCount; i++) {
            lanes.getLane(i, mTempRect);

            final int laneEdge;
            if (isVertical()) {
                laneEdge = (flow == Flow.FORWARD ? mTempRect.bottom : mTempRect.top);
            } else {
                laneEdge = (flow == Flow.FORWARD ? mTempRect.right : mTempRect.left);
            }

            if ((flow == Flow.FORWARD && laneEdge < targetEdge) ||
                (flow == Flow.BACKWARD && laneEdge > targetEdge)) {
                targetEdge = laneEdge;
                lane = i;
            }
        }

        return lane;
    }

    @Override
    protected ItemEntry ensureItemEntry(View child, int position, int lane, Rect childFrame) {
        StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(position);
        if (entry == null) {
            final int width = childFrame.right - childFrame.left;
            final int height = childFrame.bottom - childFrame.top;

            entry = new StaggeredItemEntry(lane, width, height);
            setItemEntryForPosition(position, entry);
        }

        return entry;
    }

    @Override
    protected void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        final TWLanes lanes = getLanes();
        final int firstVisiblePosition = getFirstVisiblePosition();

        final View visibleChild = findViewByPosition(position);
        if (visibleChild != null) {
            lanes.offset(offset - (isVertical() ? visibleChild.getTop() : visibleChild.getLeft()));

            // TODO: handle extra space left after resetting lanes
            for (int i = 0; i < position - firstVisiblePosition; i++) {
                detachChildFromLayout(getChildAt(i), i, Flow.FORWARD);
            }

            lanes.resetToStart();
            return;
        }

        lanes.resetToOffset(0);

        for (int i = 0; i < position; i++) {
            StaggeredItemEntry entry = (StaggeredItemEntry) getItemEntryForPosition(i);
            final int dimension;
            if (entry != null) {
                dimension = lanes.getChildFrame(entry.width, entry.height, entry.lane,
                        Flow.FORWARD, mTempRect);
            } else {
                final View child = recycler.getViewForPosition(i);
                if (child.isLayoutRequested()) {
                    child.measure(getChildWidthMeasureSpec(child, i),
                                  getChildHeightMeasureSpec(child, i));
                }

                final int lane = getLaneForPosition(position, Flow.FORWARD);
                dimension = lanes.getChildFrame(child, lane, Flow.FORWARD, mTempRect);

                entry = (StaggeredItemEntry) ensureItemEntry(child, i, lane, mTempRect);
            }

            lanes.addToLane(entry.lane, Flow.FORWARD, dimension);
        }

        lanes.resetToEnd();
        lanes.getLane(getLaneForPosition(position, Flow.FORWARD), mTempRect);
        lanes.offset(offset - (isVertical() ? mTempRect.bottom : mTempRect.right));
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childFrame) {
        final TWLanes lanes = getLanes();

        final int lane = getLaneForPosition(position, flow);
        final int dimension = lanes.getChildFrame(child, lane, flow, childFrame);
        lanes.addToLane(lane, flow, dimension);

        ensureItemEntry(child, position, lane, childFrame);
    }
}