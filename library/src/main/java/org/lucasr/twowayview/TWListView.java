package org.lucasr.twowayview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class TWListView extends TWView {
    private static final String LOGTAG = "TwoWayListView";

    private TWLayoutState mLayoutState;
    private boolean mIsVertical;

    public TWListView(Context context) {
        this(context, null);
    }

    public TWListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Orientation orientation = getOrientation();
        mLayoutState = new TWLayoutState(orientation, 1);
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
    public void detachChildFromLayout(View child, int position, Flow flow) {
        final int childWidth = child.getWidth();
        final int childHeight = child.getHeight();

        if (flow == Flow.FORWARD) {
            mLayoutState.offset(0, mIsVertical ? childHeight : childWidth);
        }

        if (mIsVertical) {
            mLayoutState.reduceHeightBy(0, childHeight);
        } else {
            mLayoutState.reduceWidthBy(0, childWidth);
        }
    }

    @Override
    public void attachChildToLayout(View child, int position, Flow flow, Rect childRect) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int l, t, r, b;

        final Rect state = mLayoutState.get(0);
        if (mIsVertical) {
            l = state.left;
            t = (flow == Flow.FORWARD ? state.bottom : state.top - childHeight);
            r = state.right;
            b = t + childHeight;
        } else {
            l = (flow == Flow.FORWARD ? state.right : state.left - childWidth);
            t = state.top;
            r = l + childWidth;
            b = state.bottom;
        }

        childRect.left = l;
        childRect.top = t;
        childRect.right = r;
        childRect.bottom = b;

        if (flow == Flow.BACKWARD) {
            mLayoutState.offset(0, mIsVertical ? -childHeight : -childWidth);
        }

        if (mIsVertical) {
            mLayoutState.increaseHeightBy(0, childHeight);
        } else {
            mLayoutState.increaseWidthBy(0, childWidth);
        }
    }
}
