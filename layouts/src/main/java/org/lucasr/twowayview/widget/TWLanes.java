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

import android.graphics.Rect;
import android.view.View;

import org.lucasr.twowayview.TWAbsLayoutManager.Direction;
import org.lucasr.twowayview.TWAbsLayoutManager.Orientation;

class TWLanes {
    public static final int NO_LANE = -1;

    private final TWBaseLayoutManager mLayout;
    private final boolean mIsVertical;
    private final Rect[] mLanes;
    private final Rect[] mSavedLanes;
    private final int mLaneSize;

    private Integer mInnerStart;
    private Integer mInnerEnd;

    public TWLanes(TWBaseLayoutManager layout, Orientation orientation, Rect[] lanes, int laneSize) {
        mLayout = layout;
        mIsVertical = (orientation == Orientation.VERTICAL);
        mLanes = lanes;
        mLaneSize = laneSize;

        mSavedLanes = new Rect[mLanes.length];
        for (int i = 0; i < mLanes.length; i++) {
            mSavedLanes[i] = new Rect();
        }
    }

    public TWLanes(TWBaseLayoutManager layout, int laneCount) {
        mLayout = layout;
        mIsVertical = (layout.getOrientation() == Orientation.VERTICAL);

        mLanes = new Rect[laneCount];
        mSavedLanes = new Rect[laneCount];
        for (int i = 0; i < laneCount; i++) {
            mLanes[i] = new Rect();
            mSavedLanes[i] = new Rect();
        }

        final int paddingLeft = layout.getPaddingLeft();
        final int paddingTop = layout.getPaddingTop();
        final int paddingRight = layout.getPaddingRight();
        final int paddingBottom = layout.getPaddingBottom();

        if (mIsVertical) {
            final int width = layout.getWidth() - paddingLeft - paddingRight;
            mLaneSize = width / laneCount;
        } else {
            final int height = layout.getHeight() - paddingTop - paddingBottom;
            mLaneSize = height / laneCount;
        }

        for (int i = 0; i < laneCount; i++) {
            final int laneStart = i * mLaneSize;

            final int l = paddingLeft + (mIsVertical ? laneStart : 0);
            final int t = paddingTop + (mIsVertical ? 0 : laneStart);
            final int r = (mIsVertical ? l + mLaneSize : l);
            final int b = (mIsVertical ? t : t + mLaneSize);

            mLanes[i].set(l, t, r, b);
        }
    }

    private void invalidateEdges() {
        mInnerStart = null;
        mInnerEnd = null;
    }

    public Orientation getOrientation() {
        return (mIsVertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
    }

    public void save() {
        for (int i = 0; i < mLanes.length; i++) {
            mSavedLanes[i].set(mLanes[i]);
        }
    }

    public void restore() {
        for (int i = 0; i < mLanes.length; i++) {
            mLanes[i].set(mSavedLanes[i]);
        }
    }

    public int getLaneSize() {
        return mLaneSize;
    }

    public int getCount() {
        return mLanes.length;
    }

    private void offsetLane(int lane, int offset) {
        mLanes[lane].offset(mIsVertical ? 0 : offset,
                mIsVertical ? offset : 0);
    }

    public void offset(int offset) {
        for (int i = 0; i < mLanes.length; i++) {
            offset(i, offset);
        }

        invalidateEdges();
    }

    public void offset(int lane, int offset) {
        offsetLane(lane, offset);
        invalidateEdges();
    }

    public void getLane(int lane, Rect laneRect) {
        laneRect.set(mLanes[lane]);
    }

    public void pushChildFrame(int lane, Direction direction, Rect childFrame) {
        final Rect laneRect = mLanes[lane];
        if (mIsVertical) {
            if (direction == Direction.END) {
                laneRect.bottom = childFrame.bottom;
            } else {
                laneRect.top = childFrame.top;
            }
        } else {
            if (direction == Direction.END) {
                laneRect.right = childFrame.right;
            } else {
                laneRect.left = childFrame.left;
            }
        }

        invalidateEdges();
    }

    public void pushChildFrame(int start, int end, Direction direction, Rect childFrame) {
        for (int i = start; i < end; i++) {
            pushChildFrame(i, direction, childFrame);
        }
    }

    public void popChildFrame(int lane, Direction direction, Rect childFrame) {
        final Rect laneRect = mLanes[lane];
        if (mIsVertical) {
            if (direction == Direction.END) {
                laneRect.top = childFrame.bottom;
            } else {
                laneRect.bottom = childFrame.top;
            }
        } else {
            if (direction == Direction.END) {
                laneRect.left = childFrame.right;
            } else {
                laneRect.right = childFrame.left;
            }
        }

        invalidateEdges();
    }

    public void popChildFrame(int start, int end, Direction direction, Rect childFrame) {
        for (int i = start; i < end; i++) {
            popChildFrame(i, direction, childFrame);
        }
    }

    public void getChildFrame(View child, int lane, Direction direction, Rect childFrame) {
        getChildFrame(mLayout.getDecoratedMeasuredWidth(child),
                mLayout.getDecoratedMeasuredHeight(child), lane, direction, childFrame);
    }

    public void getChildFrame(int childWidth, int childHeight, int lane, Direction direction,
                              Rect childFrame) {
        final Rect laneRect = mLanes[lane];

        if (mIsVertical) {
            childFrame.left = laneRect.left;
            childFrame.top =
                    (direction == Direction.END ? laneRect.bottom : laneRect.top - childHeight);
        } else {
            childFrame.top = laneRect.top;
            childFrame.left =
                    (direction == Direction.END ? laneRect.right : laneRect.left - childWidth);
        }

        childFrame.right = childFrame.left + childWidth;
        childFrame.bottom = childFrame.top + childHeight;
    }

    public void reset(Direction direction) {
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            if (mIsVertical) {
                if (direction == Direction.START) {
                    laneRect.bottom = laneRect.top;
                } else {
                    laneRect.top = laneRect.bottom;
                }
            } else {
                if (direction == Direction.START) {
                    laneRect.right = laneRect.left;
                } else {
                    laneRect.left = laneRect.right;
                }
            }
        }

        invalidateEdges();
    }

    public void reset(int offset) {
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];

            laneRect.offsetTo(mIsVertical ? laneRect.left : offset,
                              mIsVertical ? offset : laneRect.top);

            if (mIsVertical) {
                laneRect.bottom = laneRect.top;
            } else {
                laneRect.right = laneRect.left;
            }
        }

        invalidateEdges();
    }

    public int getInnerStart() {
        if (mInnerStart != null) {
            return mInnerStart;
        }

        mInnerStart = Integer.MIN_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            mInnerStart = Math.max(mInnerStart, mIsVertical ? laneRect.top : laneRect.left);
        }

        return mInnerStart;
    }

    public int getInnerEnd() {
        if (mInnerEnd != null) {
            return mInnerEnd;
        }

        mInnerEnd = Integer.MAX_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            mInnerEnd = Math.min(mInnerEnd, mIsVertical ? laneRect.bottom : laneRect.right);
        }

        return mInnerEnd;
    }

    public boolean intersects(int start, int count, Rect r) {
        for (int i = start; i < start + count; i++) {
            if (Rect.intersects(mLanes[i], r)) {
                return true;
            }
        }

        return false;
    }
}
