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
import android.util.AttributeSet;
import android.view.View;

public class TWGridLayoutManager extends TWLanedLayoutManager {
    private static final String LOGTAG = "TWGridLayoutManager";

    private static final int DEFAULT_NUM_COLS = 2;
    private static final int DEFAULT_NUM_ROWS = 2;

    private int mNumColumns;
    private int mNumRows;

    public TWGridLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWGridLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, DEFAULT_NUM_COLS, DEFAULT_NUM_ROWS);
    }

    protected TWGridLayoutManager(Context context, AttributeSet attrs, int defStyle,
                                  int defaultNumColumns, int defaultNumRows) {
        super(context, attrs, defStyle);

        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.TWGridLayoutManager, defStyle, 0);

        final int indexCount = a.getIndexCount();
        for (int i = 0; i < indexCount; i++) {
            final int attr = a.getIndex(i);

            if (attr == R.styleable.TWGridLayoutManager_numColumns) {
                mNumColumns = Math.max(defaultNumColumns, a.getInt(attr, -1));
            } else if (attr == R.styleable.TWGridLayoutManager_numRows) {
                mNumRows = Math.max(defaultNumRows, a.getInt(attr, -1));
            }
        }

        a.recycle();
    }

    public TWGridLayoutManager(Context context, Orientation orientation,
                               int numColumns, int numRows) {
        super(context, orientation);
        mNumColumns = numColumns;
        mNumRows = numRows;
    }

    private int getRectDimension(Rect r) {
        if (isVertical()) {
            return r.bottom - r.top;
        } else {
            return r.right - r.left;
        }
    }

    @Override
    protected int getLaneCount() {
        return (isVertical() ? mNumColumns : mNumRows);
    }

    @Override
    protected int getLaneForPosition(int position, Flow flow) {
        return (position % getLaneCount());
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childFrame) {
        super.attachChildToLayout(child, position, flow, childFrame);

        final int previousLane = getLaneForPosition(position, flow) - 1;
        if (previousLane >= 0) {
            final int dimension = getRectDimension(childFrame);

            final TWLanes lanes = getLanes();
            lanes.getLane(previousLane, mTempRect);
            if (getRectDimension(mTempRect) == 0) {
                lanes.offset(previousLane, dimension);
            }
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
            forceCreateLanes();
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
            forceCreateLanes();
            requestLayout();
        }
    }
}
