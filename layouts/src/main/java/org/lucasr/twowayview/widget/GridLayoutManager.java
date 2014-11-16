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
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.view.View;

import org.lucasr.twowayview.widget.Lanes.LaneInfo;

public class GridLayoutManager extends BaseLayoutManager {
    private static final String LOGTAG = "GridLayoutManager";

    private static final int DEFAULT_NUM_COLS = 2;
    private static final int DEFAULT_NUM_ROWS = 2;

    private int mNumColumns;
    private int mNumRows;

    public GridLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, DEFAULT_NUM_COLS, DEFAULT_NUM_ROWS);
    }

    protected GridLayoutManager(Context context, AttributeSet attrs, int defStyle,
                                int defaultNumColumns, int defaultNumRows) {
        super(context, attrs, defStyle);

        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.twowayview_GridLayoutManager, defStyle, 0);

        mNumColumns =
                Math.max(1, a.getInt(R.styleable.twowayview_GridLayoutManager_twowayview_numColumns, defaultNumColumns));
        mNumRows =
                Math.max(1, a.getInt(R.styleable.twowayview_GridLayoutManager_twowayview_numRows, defaultNumRows));

        a.recycle();
    }

    public GridLayoutManager(Orientation orientation, int numColumns, int numRows) {
        super(orientation);
        mNumColumns = numColumns;
        mNumRows = numRows;

        if (mNumColumns < 1) {
            throw new IllegalArgumentException("GridLayoutManager must have at least 1 column");
        }

        if (mNumRows < 1) {
            throw new IllegalArgumentException("GridLayoutManager must have at least 1 row");
        }
    }

    @Override
    int getLaneCount() {
        return (isVertical() ? mNumColumns : mNumRows);
    }

    @Override
    void getLaneForPosition(LaneInfo outInfo, int position, Direction direction) {
        final int lane = (position % getLaneCount());
        outInfo.set(lane, lane);
    }

    @Override
    void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        final Lanes lanes = getLanes();
        lanes.reset(offset);

        getLaneForPosition(mTempLaneInfo, position, Direction.END);
        final int lane = mTempLaneInfo.startLane;
        if (lane == 0) {
            return;
        }

        final View child = recycler.getViewForPosition(position);
        measureChild(child, Direction.END);

        final int dimension =
                (isVertical() ? getDecoratedMeasuredHeight(child) : getDecoratedMeasuredWidth(child));

        for (int i = lane - 1; i >= 0; i--) {
            lanes.offset(i, dimension);
        }
    }

    public int getNumColumns() {
        return mNumColumns;
    }

    public void setNumColumns(int numColumns) {
        if (mNumColumns == numColumns) {
            return;
        }

        mNumColumns = numColumns;
        if (isVertical()) {
            requestLayout();
        }
    }

    public int getNumRows() {
        return mNumRows;
    }

    public void setNumRows(int numRows) {
        if (mNumRows == numRows) {
            return;
        }

        mNumRows = numRows;
        if (!isVertical()) {
            requestLayout();
        }
    }
}
