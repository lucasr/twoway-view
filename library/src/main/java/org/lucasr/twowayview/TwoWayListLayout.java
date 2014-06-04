package org.lucasr.twowayview;

import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;

import org.lucasr.twowayview.TwoWayView.LayoutParams;
import org.lucasr.twowayview.TwoWayView.Orientation;

public class TwoWayListLayout extends TwoWayLayout {
    private static final String LOGTAG = "TwoWayListLayout";

    private final TwoWayView mView;
    private final LayoutState mLayoutState;
    private boolean mIsVertical;

    public TwoWayListLayout(TwoWayView view) {
        super(view);
        mView = view;

        final Orientation orientation = view.getOrientation();
        mLayoutState = new LayoutState(1);
        setOrientation(orientation);
    }

    @Override
    public void setOrientation(TwoWayView.Orientation orientation) {
        super.setOrientation(orientation);

        mLayoutState.setOrientation(orientation);
        mIsVertical = (orientation == Orientation.VERTICAL);

        reset(0);
    }

    @Override
    public void offset(int offset) {
        mLayoutState.offset(offset);
    }

    @Override
    public int getFirstStart() {
        return mLayoutState.getFirstStart();
    }

    @Override
    public int getLastStart() {
        return mLayoutState.getLastStart();
    }

    @Override
    public int getFirstEnd() {
        return mLayoutState.getFirstEnd();
    }

    @Override
    public int getLastEnd() {
        return mLayoutState.getLastEnd();
    }

    @Override
    public int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            final int maxWidth =
                    mView.getWidth() - mView.getPaddingLeft() - mView.getPaddingRight();
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
            final int maxHeight =
                    mView.getHeight() - mView.getPaddingTop() - mView.getPaddingBottom();
            return MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY);
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

        final int l, t, r, b;

        final Rect state = mLayoutState.get(0);
        if (mIsVertical) {
            l = state.left;
            t = state.bottom;
            r = state.right;
            b = t + childHeight;
        } else {
            l = state.right;
            t = state.top;
            r = l + childWidth;
            b = state.bottom;
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

    @Override
    public void reset(int offset) {
        final int l = mView.getPaddingLeft() + (mIsVertical ? 0 : offset);
        final int t = mView.getPaddingTop() + (mIsVertical ? offset : 0);
        final int r = (mIsVertical ? mView.getWidth() - mView.getPaddingRight() : l);
        final int b = (mIsVertical ? t : mView.getHeight() - mView.getPaddingBottom());

        mLayoutState.set(0, l, t, r, b);
    }
}
