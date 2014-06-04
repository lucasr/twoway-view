package org.lucasr.twowayview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class TwoWayGridView extends TwoWayView {
    private static final String LOGTAG = "TwoWayListLayout";

    private LayoutState mLayoutState;
    private int mLaneSize;
    private int mLaneCount;
    private boolean mIsVertical;

    public TwoWayGridView(Context context) {
        this(context, null);
    }

    public TwoWayGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoWayGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLaneSize = 0;
        mLaneCount = 3;

        mLayoutState = new LayoutState(mLaneCount);
        mIsVertical = (getOrientation() == Orientation.VERTICAL);
    }

    private int getLaneForPosition(int position) {
        return (position % mLaneCount);
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
        int maxSize = getEndEdge() - getStartEdge();
        mLaneSize = maxSize / mLaneCount;

        for (int i = 0; i < mLaneCount; i++) {
            int l = getPaddingLeft() + (mIsVertical ? i * mLaneSize : offset);
            int t = getPaddingTop() + (mIsVertical ? offset : i * mLaneSize);
            int r = (mIsVertical ? l + mLaneSize : l);
            int b = (mIsVertical ? t : t + mLaneSize);

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
    public void detachChild(View child, int position, boolean flow) {
        if (flow) {
            mLayoutState.offset(0, mIsVertical ? child.getHeight() : child.getWidth());
        }

        if (mIsVertical) {
            mLayoutState.reduceHeightBy(0, child.getHeight());
        } else {
            mLayoutState.reduceWidthBy(0, child.getWidth());
        }
    }

    @Override
    public void attachChild(View child, int position, boolean flow, boolean needsLayout) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int column = getLaneForPosition(position);
        final Rect columnState = mLayoutState.get(column);

        final int l, t, r, b;
        if (mIsVertical) {
            l = columnState.left;
            t = columnState.bottom;
            r = columnState.right;
            b = t + childHeight;
        } else {
            l = columnState.right;
            t = columnState.top;
            r = l + childWidth;
            b = columnState.bottom;
        }

        if (needsLayout) {
            child.layout(l, t, r, b);
        }

        if (!flow) {
            mLayoutState.offset(mIsVertical ? -childHeight : -childWidth);
        }

        if (mIsVertical) {
            mLayoutState.increaseHeightBy(0, childHeight);
        } else {
            mLayoutState.increaseWidthBy(0, childWidth);
        }
    }
}
