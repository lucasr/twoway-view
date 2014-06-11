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
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;

public class TWStaggeredGridView extends TWView {
    private static final String LOGTAG = "TwoWayStaggeredGridView";

    private static final int NO_LANE = -1;

    private TWLayoutState mLayoutState;

    private SparseIntArray mItemLanes;
    private int mLaneSize;
    private int mLaneCount;

    private boolean mIsVertical;

    private final Rect mTempRect = new Rect();

    public TWStaggeredGridView(Context context) {
        this(context, null);
    }

    public TWStaggeredGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWStaggeredGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLaneSize = 0;
        mLaneCount = 3;

        Orientation orientation = getOrientation();
        mLayoutState = new TWLayoutState(orientation, mLaneCount);
        mItemLanes = new SparseIntArray(10);
        mIsVertical = (orientation == Orientation.VERTICAL);
    }

    private int getLaneForPosition(int position, Flow flow) {
        int lane = mItemLanes.get(position, NO_LANE);
        if (lane != NO_LANE) {
            return lane;
        }

        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);
        for (int i = 0; i < mLaneCount; i++) {
            mLayoutState.get(i, mTempRect);

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

        mItemLanes.put(position, lane);
        return lane;
    }

    @Override
    public void setOrientation(Orientation orientation) {
        super.setOrientation(orientation);
        mIsVertical = (orientation == Orientation.VERTICAL);
    }

    @Override
    public void offsetLayout(int offset) {
        mLayoutState.offset(offset);
    }

    @Override
    public void resetLayout(int offset) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int paddingRight = getPaddingRight();
        final int paddingBottom = getPaddingBottom();

        if (mIsVertical) {
            mLaneSize = (getWidth() - paddingLeft - paddingRight) / mLaneCount;
        } else {
            mLaneSize = (getHeight() - paddingTop - paddingBottom) / mLaneCount;
        }

        for (int i = 0; i < mLaneCount; i++) {
            final int l = paddingLeft + (mIsVertical ? i * mLaneSize : offset);
            final int t = paddingTop + (mIsVertical ? offset : i * mLaneSize);
            final int r = (mIsVertical ? l + mLaneSize : l);
            final int b = (mIsVertical ? t : t + mLaneSize);

            mLayoutState.set(i, l, t, r, b);
        }
    }

    @Override
    public int getOuterStartEdge() {
        return mLayoutState.getOuterStartEdge();
    }

    @Override
    public int getInnerStartEdge() {
        return mLayoutState.getInnerStartEdge();
    }

    @Override
    public int getInnerEndEdge() {
        return mLayoutState.getInnerEndEdge();
    }

    @Override
    public int getOuterEndEdge() {
        return mLayoutState.getOuterEndEdge();
    }

    @Override
    public int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLaneSize, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }
    }

    @Override
    public int getChildHeightMeasureSpec(View child, int position, LayoutParams lp) {
        if (mIsVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (!mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLaneSize, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }
    }

    @Override
    public void detachChildFromLayout(View child, int position, Flow flow) {
        final int lane = mItemLanes.get(position, NO_LANE);
        if (lane == NO_LANE) {
            return;
        }

        if (flow == Flow.FORWARD) {
            mLayoutState.offset(lane, mIsVertical ? child.getHeight() : child.getWidth());
        }

        if (mIsVertical) {
            mLayoutState.reduceHeightBy(lane, child.getHeight());
        } else {
            mLayoutState.reduceWidthBy(lane, child.getWidth());
        }
    }

    @Override
    public void attachChildToLayout(View child, int position, Flow flow, Rect childRect) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int lane = getLaneForPosition(position, flow);
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
