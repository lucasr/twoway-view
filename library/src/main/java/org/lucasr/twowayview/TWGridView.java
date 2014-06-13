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

public class TWGridView extends TWView {
    private static final String LOGTAG = "TwoWayGridView";

    private static final int NUM_COLS = 2;
    private static final int NUM_ROWS = 2;

    private TWLayoutState mLayoutState;

    private int mNumColumns;
    private int mNumRows;
    private int mLaneSize;

    private boolean mIsVertical;

    private final Rect mTempRect = new Rect();

    public TWGridView(Context context) {
        this(context, null);
    }

    public TWGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TWGridView, defStyle, 0);
        mNumColumns = Math.max(NUM_COLS, a.getInt(R.styleable.TWGridView_numColumns, -1));
        mNumRows = Math.max(NUM_ROWS, a.getInt(R.styleable.TWGridView_numRows, -1));
        a.recycle();

        Orientation orientation = getOrientation();
        mLayoutState = new TWLayoutState(orientation, getLaneCount());
        mIsVertical = (orientation == Orientation.VERTICAL);
    }

    private int getLaneCount() {
        return (mIsVertical ? mNumColumns : mNumRows);
    }

    private int getLaneForPosition(int position) {
        return (position % getLaneCount());
    }

    private void clearLayout() {
        mLayoutState = new TWLayoutState(getOrientation(), getLaneCount());
    }

    @Override
    public void setOrientation(Orientation orientation) {
        final boolean changed = (getOrientation() != orientation);
        super.setOrientation(orientation);

        if (changed) {
            mIsVertical = (orientation == Orientation.VERTICAL);
            clearLayout();
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
        if (mIsVertical) {
            clearLayout();
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
        if (!mIsVertical) {
            clearLayout();
        }
    }

    @Override
    protected void offsetLayout(int offset) {
        mLayoutState.offset(offset);
    }

    @Override
    protected void resetLayout(int offset) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int paddingRight = getPaddingRight();
        final int paddingBottom = getPaddingBottom();

        final int laneCount = getLaneCount();

        // TODO: account for itemmargin
        if (mIsVertical) {
            mLaneSize = (getWidth() - paddingLeft - paddingRight) / laneCount;
        } else {
            mLaneSize = (getHeight() - paddingTop - paddingBottom) / laneCount;
        }

        for (int i = 0; i < laneCount; i++) {
            final int l = paddingLeft + (mIsVertical ? i * mLaneSize : offset);
            final int t = paddingTop + (mIsVertical ? offset : i * mLaneSize);
            final int r = (mIsVertical ? l + mLaneSize : l);
            final int b = (mIsVertical ? t : t + mLaneSize);

            mLayoutState.set(i, l, t, r, b);
        }
    }

    @Override
    protected int getOuterStartEdge() {
        return mLayoutState.getOuterStartEdge();
    }

    @Override
    protected int getInnerStartEdge() {
        return mLayoutState.getInnerStartEdge();
    }

    @Override
    protected int getInnerEndEdge() {
        return mLayoutState.getInnerEndEdge();
    }

    @Override
    protected int getOuterEndEdge() {
        return mLayoutState.getOuterEndEdge();
    }

    @Override
    protected int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLaneSize, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position, LayoutParams lp) {
        if (mIsVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (!mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLaneSize, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected void detachChildFromLayout(View child, int position, Flow flow) {
        final int lane = getLaneForPosition(position);

        final int childWidth = child.getWidth();
        final int childHeight = child.getHeight();

        if (flow == Flow.FORWARD) {
            mLayoutState.offset(lane, mIsVertical ? childHeight : childWidth);
        }

        if (mIsVertical) {
            mLayoutState.reduceHeightBy(lane, childHeight);
        } else {
            mLayoutState.reduceWidthBy(lane, childWidth);
        }
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childRect) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int lane = getLaneForPosition(position);
        mLayoutState.get(lane, mTempRect);

        final int l, t, r, b;
        if (mIsVertical) {
            l = mTempRect.left;
            t = (flow == Flow.FORWARD ? mTempRect.bottom : mTempRect.top - childHeight);
            r = mTempRect.right;
            b = t + childHeight;
        } else {
            l = (flow == Flow.FORWARD ? mTempRect.right : mTempRect.left - childWidth);
            t = mTempRect.top;
            r = l + childWidth;
            b = mTempRect.bottom;
        }

        childRect.left = l;
        childRect.top = t;
        childRect.right = r;
        childRect.bottom = b;

        if (flow == Flow.BACKWARD) {
            mLayoutState.offset(lane, mIsVertical ? -childHeight : -childWidth);
        }

        if (mIsVertical) {
            mLayoutState.increaseHeightBy(lane, childHeight);
        } else {
            mLayoutState.increaseWidthBy(lane, childWidth);
        }
    }
}
