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
import android.util.AttributeSet;
import android.view.View;

public class TWStaggeredGridView extends TWGridView {
    private static final String LOGTAG = "TWStaggeredGridView";

    private static final int NUM_COLS = 2;
    private static final int NUM_ROWS = 2;

    protected static class StaggeredItemEntry extends TWLanedView.ItemEntry {
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

    public TWStaggeredGridView(Context context) {
        this(context, null);
    }

    public TWStaggeredGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWStaggeredGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, NUM_COLS, NUM_ROWS);
    }

    @Override
    protected int getLaneForPosition(int position, Flow flow) {
        int lane = TWLanes.NO_LANE;

        final StaggeredItemEntry entry = (StaggeredItemEntry) mItemEntries.get(position, null);
        if (entry != null) {
            lane = entry.lane;
        }

        if (lane != TWLanes.NO_LANE) {
            return lane;
        }

        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        final int laneCount = mLanes.getCount();
        for (int i = 0; i < laneCount; i++) {
            mLanes.getLane(i, mTempRect);

            final int laneEdge;
            if (mIsVertical) {
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
    protected void ensureItemEntry(View child, int position, int lane, Rect childFrame) {
        super.ensureItemEntry(child, position, lane, childFrame);

        StaggeredItemEntry entry = (StaggeredItemEntry) mItemEntries.get(position, null);
        if (entry == null) {
            final int width = childFrame.right - childFrame.left;
            final int height = childFrame.bottom - childFrame.top;

            entry = new StaggeredItemEntry(lane, width, height);
            mItemEntries.put(position, entry);
        }
    }

    @Override
    protected void moveLayoutToPosition(int position, int offset) {
        super.moveLayoutToPosition(position, offset);
    }
}