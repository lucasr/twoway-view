package org.lucasr.twowayview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

public class TWSpannableGridView extends TWView {
    private static final String LOGTAG = "TWSpannableGridView";

    private static final int NO_LANE = -1;

    private TWLayoutState mLayoutState;

    private SparseIntArray mItemLanes;
    private int mLaneSize;
    private int mCellSize;
    private int mLaneCount;

    private boolean mIsVertical;

    private Rect mTempRect;

    public TWSpannableGridView(Context context) {
        this(context, null);
    }

    public TWSpannableGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWSpannableGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLaneSize = 0;
        mCellSize = 0;
        mLaneCount = 3;

        mTempRect = new Rect();

        Orientation orientation = getOrientation();
        mLayoutState = new TWLayoutState(orientation, mLaneCount);
        mItemLanes = new SparseIntArray(10);
        mIsVertical = (orientation == Orientation.VERTICAL);
    }

    private int getChildLaneAndBounds(View child, int position, Flow flow,
                                      int laneSpan, Rect childRect) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        int lane = mItemLanes.get(position, NO_LANE);
        if (lane != NO_LANE) {
            final Rect targetLane = mLayoutState.get(lane);

            // TODO: consolidate this code
            if (mIsVertical) {
                childRect.left = targetLane.left;
                childRect.top = (flow == Flow.FORWARD ? targetLane.bottom : targetLane.top - childHeight);
            } else {
                childRect.left = (flow == Flow.FORWARD ? targetLane.left : targetLane.left - childWidth);
                childRect.top = targetLane.top;
            }

            childRect.right = childRect.left + childWidth;
            childRect.bottom = childRect.top + childHeight;

            return lane;
        }

        int targetEdge = (flow == Flow.FORWARD ? Integer.MAX_VALUE : Integer.MIN_VALUE);
        for (int i = 0; i < mLaneCount; i++) {
            final Rect laneState = mLayoutState.get(i);

            final int laneEdge;
            if (mIsVertical) {
                laneEdge = (flow == Flow.FORWARD ? laneState.bottom : laneState.top);
            } else {
                laneEdge = (flow == Flow.FORWARD ? laneState.right : laneState.left);
            }

            if ((flow == Flow.FORWARD && laneEdge < targetEdge) ||
                (flow == Flow.BACKWARD && laneEdge > targetEdge)) {

                for (int j = 0; j < mLaneCount - laneSpan + 1; j++) {
                    final Rect targetLane = mLayoutState.get(j);
                    final int l, t, r, b;

                    if (mIsVertical) {
                        l = targetLane.left;
                        t = (flow == Flow.FORWARD ? laneState.bottom : targetLane.top - childHeight);
                    } else {
                        l = (flow == Flow.FORWARD ? laneState.left : targetLane.left - childWidth);
                        t = targetLane.top;
                    }
                    r = l + childWidth;
                    b = t + childHeight;

                    if (!mLayoutState.intersects(j, l, t, r, b)) {
                        targetEdge = laneEdge;
                        lane = j;
                        childRect.set(l, t, r, b);
                        break;
                    }
                }
            }
        }

        mItemLanes.put(position, Math.max(0, lane));
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
        mCellSize = mLaneSize;

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
    public int getChildWidthMeasureSpec(View child, int position, TWView.LayoutParams lp) {
        final LayoutParams spannableLp = (LayoutParams) lp;
        // TODO: account for item margin
        final int width = (mIsVertical ? mLaneSize : mCellSize) * spannableLp.colSpan;
        return MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
    }

    @Override
    public int getChildHeightMeasureSpec(View child, int position, TWView.LayoutParams lp) {
        final LayoutParams spannableLp = (LayoutParams) lp;
        // TODO: account for item margin
        final int height = (mIsVertical ? mCellSize : mLaneSize) * spannableLp.rowSpan;
        return MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
    }

    @Override
    public void detachChildFromLayout(View child, int position, Flow flow) {
        final int lane = mItemLanes.get(position, NO_LANE);
        if (lane == NO_LANE) {
            return;
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int laneSpan = (mIsVertical ? lp.colSpan : lp.rowSpan);

        final int childWidth = child.getWidth();
        final int childHeight = child.getHeight();

        for (int i = lane; i < lane + laneSpan; i++) {
            final Rect laneState = mLayoutState.get(i);
            final int l, t, r, b;

            if (mIsVertical) {
                l = laneState.left;
                t = laneState.top + (flow == Flow.FORWARD ? childHeight : 0);
                r = laneState.right;
                b = laneState.bottom - (flow == Flow.FORWARD ? 0 : childHeight);
            } else {
                l = laneState.left + (flow == Flow.FORWARD ? childWidth : 0);
                t = laneState.top;
                r = laneState.right - (flow == Flow.FORWARD ? 0 : childWidth);
                b = laneState.bottom;
            }
            mLayoutState.set(i, l, t, r, b);
        }
    }

    @Override
    public void attachChildToLayout(View child, int position, Flow flow, Rect childRect) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int laneSpan = (mIsVertical ? lp.colSpan : lp.rowSpan);

        final int lane = getChildLaneAndBounds(child, position, flow, laneSpan, childRect);

        for (int i = lane; i < lane + laneSpan; i++) {
            final Rect laneState = mLayoutState.get(i);
            final int l, t, r, b;

            if (mIsVertical) {
                l = laneState.left;
                t = (flow == Flow.FORWARD ? laneState.top : childRect.top);
                r = laneState.right;
                b = (flow == Flow.FORWARD ? childRect.bottom : laneState.bottom);
            } else {
                l = (flow == Flow.FORWARD ? laneState.left : childRect.left);
                t = laneState.top;
                r = (flow == Flow.FORWARD ? childRect.right : laneState.right);
                b = laneState.bottom;
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
        public int rowSpan;
        public int colSpan;

        public LayoutParams(int width, int height) {
            super(width, height);
            rowSpan = 1;
            colSpan = 1;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            rowSpan = 1;
            colSpan = 1;
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);

            final LayoutParams lp = (LayoutParams) other;
            rowSpan = lp.rowSpan;
            colSpan = lp.colSpan;
        }
    }
}
