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
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

public class TWSpannableGridView extends TWView {
    private static final String LOGTAG = "TWSpannableGridView";

    private static final int NUM_COLS = 3;
    private static final int NUM_ROWS = 3;

    private static final int NO_LANE = -1;

    private TWLayoutState mLayoutState;

//    private static class ItemLayout {
//        public final int lane;
//        private final int colsPan;
//        private final int rowSpan;
//
//        public ItemLayout(int lane, int colSpan, int rowSpan) {
//            this.lane = lane;
//            this.colsPan = colSpan;
//            this.rowSpan = rowSpan;
//        }
//    }

    private SparseIntArray mItemLanes;
    private int mNumColumns;
    private int mNumRows;
    private int mLaneSize;

    private boolean mIsVertical;

    private final Rect mTempRect = new Rect();

    public TWSpannableGridView(Context context) {
        this(context, null);
    }

    public TWSpannableGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWSpannableGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TWGridView, defStyle, 0);
        mNumColumns = Math.max(NUM_COLS, a.getInt(R.styleable.TWGridView_numColumns, -1));
        mNumRows = Math.max(NUM_ROWS, a.getInt(R.styleable.TWGridView_numRows, -1));
        a.recycle();

        Orientation orientation = getOrientation();
        mIsVertical = (orientation == Orientation.VERTICAL);
        // TODO: avoid double allocation
        mLayoutState = new TWLayoutState(orientation, getLaneCount());
        mItemLanes = new SparseIntArray(10);
    }

    private int getLaneCount() {
        return (mIsVertical ? mNumColumns : mNumRows);
    }

    private int getChildStartInLane(int lane, Flow flow, int childWidth, int childHeight) {
        mLayoutState.get(lane, mTempRect);

        if (mIsVertical) {
            return (flow == Flow.FORWARD ? mTempRect.bottom : mTempRect.top - childHeight);
        } else {
            return (flow == Flow.FORWARD ? mTempRect.right : mTempRect.left - childWidth);
        }
    }

    private void getChildFrameForLane(int targetLane, int anchorLane, Flow flow, int childWidth,
                                      int childHeight, Rect frame) {

        final int anchorEdge = getChildStartInLane(anchorLane, flow, childWidth, childHeight);
        mLayoutState.get(targetLane, mTempRect);

        if (mIsVertical) {
            frame.left = mTempRect.left;
            frame.top = anchorEdge;
        } else {
            frame.left = anchorEdge;
            frame.top = mTempRect.top;
        }

        frame.right = frame.left + childWidth;
        frame.bottom = frame.top + childHeight;
    }

    private int getLaneThatFitsFrame(int anchorLane, Flow flow, int laneSpan, int childWidth,
                                     int childHeight, Rect frame) {
        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            getChildFrameForLane(l, anchorLane, flow, childWidth, childHeight, frame);
            if (!mLayoutState.intersects(l, frame)) {
                return l;
            }
        }

        return NO_LANE;
    }

    private int getChildLaneAndFrame(View child, int position, Flow flow,
                                     int laneSpan, Rect frame) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        int lane = mItemLanes.get(position, NO_LANE);
        if (lane != NO_LANE) {
            getChildFrameForLane(lane, lane, flow, childWidth, childHeight, frame);
            return lane;
        }

        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            final int childStart = getChildStartInLane(l, flow, childWidth, childHeight);

            if ((flow == Flow.FORWARD && childStart < targetEdge) ||
                (flow == Flow.BACKWARD && childStart > targetEdge)) {

                final int targetLane =
                        getLaneThatFitsFrame(l, flow, laneSpan, childWidth, childHeight, frame);

                if (targetLane != NO_LANE) {
                    targetEdge = childStart;
                    lane = targetLane;
                }
            }
        }

        mItemLanes.put(position, Math.max(0, lane));
        return lane;
    }

    private void clearLayout() {
        mLayoutState = new TWLayoutState(getOrientation(), getLaneCount());
        // mItemLanes != null && mItemLanes.size() != mResolution
        if (mItemLanes != null) {
            mItemLanes.clear();
        }
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

    @Override
    public void setSelection(int position) {
        if (position != 0) {
            throw new IllegalArgumentException("You can only set selection to first position (0)" +
                                               "on a TWSpannableGridView");
        }

        super.setSelection(position);
    }

    @Override
    public void setSelectionFromOffset(int position, int offset) {
        if (position != 0) {
            throw new IllegalArgumentException("You can only set selection to first position (0)" +
                                               "on a TWSpannableGridView");
        }

        super.setSelectionFromOffset(position, offset);
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
    protected int getChildWidthMeasureSpec(View child, int position, TWView.LayoutParams lp) {
        final LayoutParams spannableLp = (LayoutParams) lp;
        // TODO: account for item margin
        final int width = mLaneSize * spannableLp.colSpan;
        return MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position, TWView.LayoutParams lp) {
        final LayoutParams spannableLp = (LayoutParams) lp;
        // TODO: account for item margin
        final int height = mLaneSize * spannableLp.rowSpan;
        return MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
    }

    @Override
    protected void detachChildFromLayout(View child, int position, Flow flow) {
        final int lane = mItemLanes.get(position, NO_LANE);
        if (lane == NO_LANE) {
            return;
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int laneSpan = (mIsVertical ? lp.colSpan : lp.rowSpan);

        final int childWidth = child.getWidth();
        final int childHeight = child.getHeight();

        for (int i = lane; i < lane + laneSpan; i++) {
            mLayoutState.get(i, mTempRect);

            final int l, t, r, b;
            if (mIsVertical) {
                l = mTempRect.left;
                t = mTempRect.top + (flow == Flow.FORWARD ? childHeight : 0);
                r = mTempRect.right;
                b = mTempRect.bottom - (flow == Flow.FORWARD ? 0 : childHeight);
            } else {
                l = mTempRect.left + (flow == Flow.FORWARD ? childWidth : 0);
                t = mTempRect.top;
                r = mTempRect.right - (flow == Flow.FORWARD ? 0 : childWidth);
                b = mTempRect.bottom;
            }
            mLayoutState.set(i, l, t, r, b);
        }
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childRect) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int laneSpan = (mIsVertical ? lp.colSpan : lp.rowSpan);

        final int lane = getChildLaneAndFrame(child, position, flow, laneSpan, childRect);

        for (int i = lane; i < lane + laneSpan; i++) {
            mLayoutState.get(i, mTempRect);

            final int l, t, r, b;
            if (mIsVertical) {
                l = mTempRect.left;
                t = (flow == Flow.FORWARD ? mTempRect.top : childRect.top);
                r = mTempRect.right;
                b = (flow == Flow.FORWARD ? childRect.bottom : mTempRect.bottom);
            } else {
                l = (flow == Flow.FORWARD ? mTempRect.left : childRect.left);
                t = mTempRect.top;
                r = (flow == Flow.FORWARD ? childRect.right : mTempRect.right);
                b = mTempRect.bottom;
            }
            mLayoutState.set(i, l, t, r, b);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        if (mIsVertical) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new LayoutParams(lp);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
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
            colSpan =Math.max(
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
