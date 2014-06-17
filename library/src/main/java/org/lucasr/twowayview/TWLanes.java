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

import android.graphics.Rect;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import org.lucasr.twowayview.TWView.Flow;

import org.lucasr.twowayview.TWView.Orientation;

class TWLanes {
    public static final int NO_LANE = -1;

    private final TWView mView;
    private final boolean mIsVertical;
    private final Rect[] mLanes;
    private final int mLaneSize;

    public TWLanes(TWView view, Orientation orientation, Rect[] lanes, int laneSize) {
        mView = view;
        mIsVertical = (orientation == Orientation.VERTICAL);
        mLanes = lanes;
        mLaneSize = laneSize;
    }

    public TWLanes(TWView view, int laneCount) {
        mView = view;
        mIsVertical = (view.getOrientation() == Orientation.VERTICAL);

        mLanes = new Rect[laneCount];
        for (int i = 0; i < laneCount; i++) {
            mLanes[i] = new Rect();
        }

        final int paddingLeft = view.getPaddingLeft();
        final int paddingTop = view.getPaddingTop();
        final int paddingRight = view.getPaddingRight();
        final int paddingBottom = view.getPaddingBottom();

        final int verticalSpacing = view.getVerticalSpacing();
        final int horizontalSpacing = view.getHorizontalSpacing();

        if (mIsVertical) {
            final int width = view.getWidth() - paddingLeft - paddingRight;
            final int spacing = horizontalSpacing * (laneCount - 1);
            mLaneSize = (width - spacing) / laneCount;
        } else {
            final int height = view.getHeight() - paddingTop - paddingBottom;
            final int spacing = verticalSpacing * (laneCount - 1);
            mLaneSize = (height - spacing) / laneCount;
        }

        for (int i = 0; i < laneCount; i++) {
            final int spacing = i * (mIsVertical ? horizontalSpacing : verticalSpacing);
            final int laneStart = (i * mLaneSize) + spacing;

            final int l = paddingLeft + (mIsVertical ? laneStart : view.getStartEdge());
            final int t = paddingTop + (mIsVertical ? view.getStartEdge() : laneStart);
            final int r = (mIsVertical ? l + mLaneSize : l);
            final int b = (mIsVertical ? t : t + mLaneSize);

            mLanes[i].set(l, t, r, b);
        }
    }

    public Orientation getOrientation() {
        return (mIsVertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
    }

    public int getLaneSize() {
        return mLaneSize;
    }

    public int getCount() {
        return mLanes.length;
    }

    public void offset(int offset) {
        for (int i = 0; i < mLanes.length; i++) {
            mLanes[i].offset(mIsVertical ? 0 : offset,
                    mIsVertical ? offset : 0);
        }
    }

    public void getLane(int lane, Rect laneRect) {
        laneRect.set(mLanes[lane]);
    }

    public void setLane(int lane, int l, int t, int r, int b) {
        final Rect laneRect = mLanes[lane];
        laneRect.left = l;
        laneRect.top = t;
        laneRect.right = r;
        laneRect.bottom = b;
    }

    public void addToLane(int lane, Flow flow, int dimension) {
        final Rect laneRect = mLanes[lane];

        if (flow == Flow.FORWARD) {
            if (mIsVertical) {
                laneRect.bottom += dimension;
            } else {
                laneRect.right += dimension;
            }
        } else {
            if (mIsVertical) {
                laneRect.top -= dimension;
            } else {
                laneRect.left -= dimension;
            }
        }
    }

    public void removeFromLane(int lane, Flow flow, int dimension) {
        final Rect laneRect = mLanes[lane];

        if (flow == Flow.FORWARD) {
            if (mIsVertical) {
                laneRect.top += dimension;
            } else {
                laneRect.left += dimension;
            }
        } else {
            if (mIsVertical) {
                laneRect.bottom -= dimension;
            } else {
                laneRect.right -= dimension;
            }
        }
    }

    public int getChildFrame(View child, int lane, Flow flow, Rect childFrame) {
        final Rect laneRect = mLanes[lane];

        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int delta;

        if (mIsVertical) {
            childFrame.left = laneRect.left;
            childFrame.right = laneRect.left + childWidth;

            final int spacing = mView.getVerticalSpacing();
            final boolean shouldHaveSpacing = (laneRect.top != laneRect.bottom);
            if (flow == Flow.FORWARD) {
                childFrame.top = laneRect.bottom + (shouldHaveSpacing ? spacing : 0);
                childFrame.bottom = childFrame.top + childHeight;
                delta = childFrame.bottom - laneRect.bottom;
            } else {
                childFrame.top = laneRect.top - childHeight - (shouldHaveSpacing ? spacing : 0);
                childFrame.bottom = childFrame.top + childHeight;
                delta = laneRect.top - childFrame.top;
            }
        } else {
            childFrame.top = laneRect.top;
            childFrame.bottom = laneRect.top + childHeight;

            final int spacing = mView.getHorizontalSpacing();
            final boolean shouldHaveSpacing = (laneRect.left != laneRect.right);
            if (flow == Flow.FORWARD) {
                childFrame.left = laneRect.right + (shouldHaveSpacing ? spacing : 0);
                childFrame.right = childFrame.left + childWidth;
                delta = childFrame.right - laneRect.right;
            } else {
                childFrame.left = laneRect.left - childWidth - (shouldHaveSpacing ? spacing : 0);
                childFrame.right = childFrame.left + childWidth;
                delta = laneRect.left - childFrame.left;
            }
        }

        return delta;
    }

    public void resetEndEdges() {
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            if (mIsVertical) {
                laneRect.bottom = laneRect.top;
            } else {
                laneRect.right = laneRect.left;
            }
        }
    }

    public int getOuterStartEdge() {
        int outerStart = Integer.MAX_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            outerStart = Math.min(outerStart, mIsVertical ? laneRect.top : laneRect.left);
        }

        return outerStart;
    }

    public int getInnerStartEdge() {
        int innerStart = Integer.MIN_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            innerStart = Math.max(innerStart, mIsVertical ? laneRect.top : laneRect.left);
        }

        return innerStart;
    }

    public int getInnerEndEdge() {
        int innerEnd = Integer.MAX_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            innerEnd = Math.min(innerEnd, mIsVertical ? laneRect.bottom : laneRect.right);
        }

        return innerEnd;
    }

    public int getOuterEndEdge() {
        int outerEnd = Integer.MIN_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            outerEnd = Math.max(outerEnd, mIsVertical ? laneRect.bottom : laneRect.right);
        }

        return outerEnd;
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
