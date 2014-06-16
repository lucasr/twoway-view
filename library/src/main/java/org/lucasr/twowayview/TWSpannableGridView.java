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
    private SparseIntArray mItemLanes;

    private int mNumColumns;
    private int mNumRows;

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

        mIsVertical = (getOrientation() == Orientation.VERTICAL);
    }

    private int getLaneCount() {
        return (mIsVertical ? mNumColumns : mNumRows);
    }

    private int getChildStartInLane(View child, int lane, Flow flow) {
        mLayoutState.getChildFrame(child, lane, flow, mTempRect);
        return (mIsVertical ? mTempRect.top : mTempRect.left);
    }

    private int getLaneThatFitsFrame(View child, int anchor, Flow flow,
                                     int laneSpan, Rect frame) {
        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            mLayoutState.getChildFrame(child, l, flow, frame);

            frame.offsetTo(mIsVertical ? frame.left : anchor,
                           mIsVertical ? anchor : frame.top);

            if (!mLayoutState.intersects(l, laneSpan, frame)) {
                return l;
            }
        }

        return NO_LANE;
    }

    private int getChildLaneAndFrame(View child, int position, Flow flow,
                                     int laneSpan, Rect frame) {
        int lane = mItemLanes.get(position, NO_LANE);
        if (lane != NO_LANE) {
            mLayoutState.getChildFrame(child, lane, flow, frame);
            return lane;
        }

        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);

        final int count = getLaneCount() - laneSpan + 1;
        for (int l = 0; l < count; l++) {
            final int childStart = getChildStartInLane(child, l, flow);

            if ((flow == Flow.FORWARD && childStart < targetEdge) ||
                (flow == Flow.BACKWARD && childStart > targetEdge)) {

                final int targetLane =
                        getLaneThatFitsFrame(child, childStart, flow, laneSpan, frame);

                if (targetLane != NO_LANE) {
                    targetEdge = childStart;
                    lane = targetLane;
                }
            }
        }

        if (lane != NO_LANE) {
            mItemLanes.put(position, lane);
        }

        return lane;
    }

    private void ensureLayoutState() {
        final int laneCount = getLaneCount();
        if (mLayoutState != null && mLayoutState.getLaneCount() == laneCount) {
            return;
        }

        mLayoutState = new TWLayoutState(this, laneCount);
        if (mItemLanes == null) {
            mItemLanes = new SparseIntArray(10);
        } else {
            mItemLanes.clear();
        }
    }

    private void recreateLayoutState() {
        if (mNumColumns > 0 && mNumRows > 0) {
            mLayoutState = null;
            ensureLayoutState();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recreateLayoutState();
    }

    @Override
    public void setOrientation(Orientation orientation) {
        final boolean changed = (getOrientation() != orientation);
        super.setOrientation(orientation);

        if (changed) {
            mIsVertical = (orientation == Orientation.VERTICAL);
            recreateLayoutState();
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
            recreateLayoutState();
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
            recreateLayoutState();
        }
    }

    @Override
    protected void offsetLayout(int offset) {
        mLayoutState.offset(offset);
    }

    @Override
    protected void resetLayout(int offset) {
        if (mLayoutState != null) {
            mLayoutState.resetEndEdges();
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
        final int span = spannableLp.colSpan;

        final int width = mLayoutState.getLaneSize() * span + getHorizontalSpacing() * (span - 1);
        return MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position, TWView.LayoutParams lp) {
        final LayoutParams spannableLp = (LayoutParams) lp;
        final int span = spannableLp.rowSpan;

        final int height = mLayoutState.getLaneSize() * span + getVerticalSpacing() * (span - 1);
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

        final int spacing = (mIsVertical ? getVerticalSpacing() : getHorizontalSpacing());
        final int dimension = (mIsVertical ? child.getHeight() : child.getWidth());

        for (int i = lane; i < lane + laneSpan; i++) {
            mLayoutState.removeFromLane(i, flow, dimension + spacing);
        }
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childFrame) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int laneSpan = (mIsVertical ? lp.colSpan : lp.rowSpan);

        final int lane = getChildLaneAndFrame(child, position, flow, laneSpan, childFrame);
        for (int i = lane; i < lane + laneSpan; i++) {
            mLayoutState.getLane(i, mTempRect);

            final int l, t, r, b;
            if (mIsVertical) {
                l = mTempRect.left;
                t = (flow == Flow.FORWARD ? mTempRect.top : childFrame.top);
                r = mTempRect.right;
                b = (flow == Flow.FORWARD ? childFrame.bottom : mTempRect.bottom);
            } else {
                l = (flow == Flow.FORWARD ? mTempRect.left : childFrame.left);
                t = mTempRect.top;
                r = (flow == Flow.FORWARD ? childFrame.right : mTempRect.right);
                b = mTempRect.bottom;
            }
            mLayoutState.setLane(i, l, t, r, b);
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
