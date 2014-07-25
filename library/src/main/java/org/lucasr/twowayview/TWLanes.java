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
import android.view.View;

import org.lucasr.twowayview.TWLayoutManager.Flow;
import org.lucasr.twowayview.TWLayoutManager.Orientation;

import java.util.EnumMap;

class TWLanes {
    public static final int NO_LANE = -1;

    private final TWLanedLayoutManager mLayout;
    private final boolean mIsVertical;
    private final Rect[] mLanes;
    private final int mLaneSize;

    private static enum Edge {
        OUTER_START,
        OUTER_END,
        INNER_START,
        INNER_END
    }

    private final EnumMap<Edge, Integer> mCachedEdges = new EnumMap(Edge.class);

    public TWLanes(TWLanedLayoutManager layout, Orientation orientation, Rect[] lanes, int laneSize) {
        mLayout = layout;
        mIsVertical = (orientation == Orientation.VERTICAL);
        mLanes = lanes;
        mLaneSize = laneSize;
    }

    public TWLanes(TWLanedLayoutManager layout, int laneCount) {
        mLayout = layout;
        mIsVertical = (layout.getOrientation() == Orientation.VERTICAL);

        mLanes = new Rect[laneCount];
        for (int i = 0; i < laneCount; i++) {
            mLanes[i] = new Rect();
        }

        final int paddingLeft = layout.getPaddingLeft();
        final int paddingTop = layout.getPaddingTop();
        final int paddingRight = layout.getPaddingRight();
        final int paddingBottom = layout.getPaddingBottom();

        final int verticalSpacing = layout.getVerticalSpacing();
        final int horizontalSpacing = layout.getHorizontalSpacing();

        if (mIsVertical) {
            final int width = layout.getWidth() - paddingLeft - paddingRight;
            final int spacing = horizontalSpacing * (laneCount - 1);
            mLaneSize = (width - spacing) / laneCount;
        } else {
            final int height = layout.getHeight() - paddingTop - paddingBottom;
            final int spacing = verticalSpacing * (laneCount - 1);
            mLaneSize = (height - spacing) / laneCount;
        }

        for (int i = 0; i < laneCount; i++) {
            final int spacing = i * (mIsVertical ? horizontalSpacing : verticalSpacing);
            final int laneStart = (i * mLaneSize) + spacing;

            final int l = paddingLeft + (mIsVertical ? laneStart : layout.getStartEdge());
            final int t = paddingTop + (mIsVertical ? layout.getStartEdge() : laneStart);
            final int r = (mIsVertical ? l + mLaneSize : l);
            final int b = (mIsVertical ? t : t + mLaneSize);

            mLanes[i].set(l, t, r, b);
        }
    }

    private void invalidateEdges() {
        mCachedEdges.clear();
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

    public void setLane(int lane, int l, int t, int r, int b) {
        final Rect laneRect = mLanes[lane];
        laneRect.left = l;
        laneRect.top = t;
        laneRect.right = r;
        laneRect.bottom = b;

        invalidateEdges();
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

        invalidateEdges();
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

        invalidateEdges();
    }

    public int getChildFrame(View child, int lane, Flow flow, Rect childFrame) {
        return getChildFrame(child.getMeasuredWidth(), child.getMeasuredHeight(),
                             lane, flow, childFrame);
    }

    public int getChildFrame(int childWidth, int childHeight, int lane, Flow flow, Rect childFrame) {
        final Rect laneRect = mLanes[lane];

        final int delta;

        if (mIsVertical) {
            childFrame.left = laneRect.left;
            childFrame.right = laneRect.left + childWidth;

            final int spacing = mLayout.getVerticalSpacing();
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

            final int spacing = mLayout.getHorizontalSpacing();
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

    private void reset(boolean toStart) {
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            if (mIsVertical) {
                if (toStart) {
                    laneRect.bottom = laneRect.top;
                } else {
                    laneRect.top = laneRect.bottom;
                }
            } else {
                if (toStart) {
                    laneRect.right = laneRect.left;
                } else {
                    laneRect.left = laneRect.right;
                }
            }
        }

        invalidateEdges();
    }

    public void resetToStart() {
        reset(true);
    }

    public void resetToEnd() {
        reset(false);
    }

    public void resetToOffset(int offset) {
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            if (mIsVertical) {
                laneRect.top = offset;
                laneRect.bottom = laneRect.top;
            } else {
                laneRect.left = offset;
                laneRect.right = laneRect.left;
            }
        }

        invalidateEdges();
    }

    public int getOuterStartEdge() {
        Integer outerStart = mCachedEdges.get(Edge.OUTER_START);
        if (outerStart != null) {
            return outerStart;
        }

        outerStart = Integer.MAX_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            outerStart = Math.min(outerStart, mIsVertical ? laneRect.top : laneRect.left);
        }

        mCachedEdges.put(Edge.OUTER_START, outerStart);
        return outerStart;
    }

    public int getInnerStartEdge() {
        Integer innerStart = mCachedEdges.get(Edge.INNER_START);
        if (innerStart != null) {
            return innerStart;
        }

        innerStart = Integer.MIN_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            innerStart = Math.max(innerStart, mIsVertical ? laneRect.top : laneRect.left);
        }

        mCachedEdges.put(Edge.INNER_START, innerStart);
        return innerStart;
    }

    public int getInnerEndEdge() {
        Integer innerEnd = mCachedEdges.get(Edge.INNER_END);
        if (innerEnd != null) {
            return innerEnd;
        }

        innerEnd = Integer.MAX_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            innerEnd = Math.min(innerEnd, mIsVertical ? laneRect.bottom : laneRect.right);
        }

        mCachedEdges.put(Edge.INNER_END, innerEnd);
        return innerEnd;
    }

    public int getOuterEndEdge() {
        Integer outerEnd = mCachedEdges.get(Edge.OUTER_END);
        if (outerEnd != null) {
            return outerEnd;
        }

        outerEnd = Integer.MIN_VALUE;
        for (int i = 0; i < mLanes.length; i++) {
            final Rect laneRect = mLanes[i];
            outerEnd = Math.max(outerEnd, mIsVertical ? laneRect.bottom : laneRect.right);
        }

        mCachedEdges.put(Edge.OUTER_END, outerEnd);
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
