package org.lucasr.twowayview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class TwoWayListView extends TwoWayView {
    private static final String LOGTAG = "TwoWayListView";

    private LayoutState mLayoutState;
    private boolean mIsVertical;

    public TwoWayListView(Context context) {
        this(context, null);
    }

    public TwoWayListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoWayListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Orientation orientation = getOrientation();
        mLayoutState = new LayoutState(orientation, 1);
        mIsVertical = (orientation == Orientation.VERTICAL);
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
        final int l = getPaddingLeft() + (mIsVertical ? 0 : offset);
        final int t = getPaddingTop() + (mIsVertical ? offset : 0);
        final int r = (mIsVertical ? getWidth() - getPaddingRight() : l);
        final int b = (mIsVertical ? t : getHeight() - getPaddingBottom());

        mLayoutState.set(0, l, t, r, b);
    }

    @Override
    public int getOuterStartEdge() {
        final Rect state = mLayoutState.get(0);
        return (mIsVertical ? state.top : state.left);
    }

    @Override
    public int getInnerStartEdge() {
        // Inner and outer edges are always the same in a list
        return getOuterStartEdge();
    }

    @Override
    public int getInnerEndEdge() {
        // Inner and outer edges are always the same in a list
        return getOuterEndEdge();
    }

    @Override
    public int getOuterEndEdge() {
        final Rect state = mLayoutState.get(0);
        return (mIsVertical ? state.bottom : state.right);
    }

    @Override
    public int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            final int maxWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            return MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }
    }

    @Override
    public int getChildHeightMeasureSpec(View child, int position, LayoutParams lp) {
        if (mIsVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (!mIsVertical) {
            final int maxHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            return MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }
    }

    @Override
    public void detachChildFromLayout(View child, int position, boolean flow) {
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
    public void attachChildToLayout(View child, int position, boolean flow, boolean needsLayout) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int l, t, r, b;

        final Rect state = mLayoutState.get(0);
        if (mIsVertical) {
            l = state.left;
            t = (flow ? state.bottom : state.top - childHeight);
            r = state.right;
            b = t + childHeight;
        } else {
            l = (flow ? state.right : state.left - childWidth);
            t = state.top;
            r = l + childWidth;
            b = state.bottom;
        }

        if (needsLayout) {
            child.layout(l, t, r, b);
        } else {
            child.offsetLeftAndRight(l - child.getLeft());
            child.offsetTopAndBottom(t - child.getTop());
        }

        if (!flow) {
            mLayoutState.offset(0, mIsVertical ? -childHeight : -childWidth);
        }

        if (mIsVertical) {
            mLayoutState.increaseHeightBy(0, childHeight);
        } else {
            mLayoutState.increaseWidthBy(0, childWidth);
        }
    }
}
