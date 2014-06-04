package org.lucasr.twowayview;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import org.lucasr.twowayview.TwoWayView.LayoutParams;
import org.lucasr.twowayview.TwoWayView.Orientation;

import java.util.Arrays;

public abstract class TwoWayLayout {
    private final Context mContext;
    private final TwoWayView mView;
    private Orientation mOrientation;

    public TwoWayLayout(TwoWayView view) {
        mView = view;
        mContext = view.getContext();
        mOrientation = view.getOrientation();
    }

    public TwoWayView getView() {
        return mView;
    }

    public void setOrientation(Orientation orientation) {
        mOrientation = orientation;
    }

    public Orientation getOrientation() {
        return mOrientation;
    }

    public abstract void offset(int offset);

    public abstract int getFirstStart();
    public abstract int getLastStart();
    public abstract int getFirstEnd();
    public abstract int getLastEnd();

    public abstract int getChildWidthMeasureSpec(View child, int position, LayoutParams lp);
    public abstract int getChildHeightMeasureSpec(View child, int position, LayoutParams lp);

    public abstract void detachChild(View child, int position, boolean flow);
    public abstract void attachChild(View child, int position, boolean flow, boolean needsLayout);

    public abstract void reset(int offset);

    public class LayoutState {
        private boolean mIsVertical;
        private Rect[] mControlRects;

        public LayoutState(int count) {
            mControlRects = new Rect[count];
            for (int i = 0; i < count; i++) {
                mControlRects[i] = new Rect();
            }
        }

        public void setOrientation(Orientation orientation) {
            mIsVertical = (orientation == Orientation.VERTICAL);
        }

        public void offset(int offset) {
            for (int i = 0; i < mControlRects.length; i++) {
                offset(i, offset);
            }
        }

        public void offset(int index, int offset) {
            final Rect rect = mControlRects[index];
            rect.offset(mIsVertical ? 0 : offset,
                        mIsVertical ? offset : 0);
        }

        public Rect get(int index) {
            return mControlRects[index];
        }

        public void set(int index, int l, int t, int r, int b) {
            final Rect rect = mControlRects[index];
            rect.left = l;
            rect.top = t;
            rect.right = r;
            rect.bottom = b;
        }

        public void increaseWidthBy(int index, int addedWidth) {
            final Rect rect = mControlRects[index];
            rect.right += addedWidth;
        }

        public void increaseHeightBy(int index, int addedHeight) {
            final Rect rect = mControlRects[index];
            rect.bottom += addedHeight;
        }

        public void reduceWidthBy(int index, int removedWidth) {
            final Rect rect = mControlRects[index];
            rect.right -= Math.min(rect.right - rect.left, removedWidth);
        }

        public void reduceHeightBy(int index, int removedHeight) {
            final Rect rect = mControlRects[index];
            rect.bottom -= Math.min(rect.bottom - rect.top, removedHeight);
        }

        public int getFirstStart() {
            // TODO: make this a lot more performant/efficient
            int firstStart = Integer.MAX_VALUE;
            for (int i = 0; i < mControlRects.length; i++) {
                final Rect rect = mControlRects[i];
                firstStart = Math.min(firstStart, mIsVertical ? rect.top : rect.left);
            }

            return firstStart;
        }

        public int getLastStart() {
            int lastStart = 0;
            for (int i = 0; i < mControlRects.length; i++) {
                final Rect rect = mControlRects[i];
                lastStart = Math.max(lastStart, mIsVertical ? rect.top : rect.left);
            }

            return lastStart;
        }

        public int getFirstEnd() {
            int firstEnd = Integer.MAX_VALUE;
            for (int i = 0; i < mControlRects.length; i++) {
                final Rect rect = mControlRects[i];
                firstEnd = Math.min(firstEnd, mIsVertical ? rect.bottom : rect.right);
            }

            return firstEnd;
        }

        public int getLastEnd() {
            int lastEnd = 0;
            for (int i = 0; i < mControlRects.length; i++) {
                final Rect rect = mControlRects[i];
                lastEnd = Math.max(lastEnd, mIsVertical ? rect.bottom : rect.right);
            }

            return lastEnd;
        }
    }
}
