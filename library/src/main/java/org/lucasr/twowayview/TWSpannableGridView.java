package org.lucasr.twowayview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class TWSpannableGridView extends TWView {
    private static final String LOGTAG = "TWSpannableGridView";

    public TWSpannableGridView(Context context) {
        this(context, null);
    }

    public TWSpannableGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWSpannableGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setOrientation(Orientation orientation) {
        super.setOrientation(orientation);
    }

    @Override
    public void offsetLayout(int offset) {
    }

    @Override
    public void resetLayout(int offset) {
    }

    @Override
    public int getOuterStartEdge() {
        return 0;
    }

    @Override
    public int getInnerStartEdge() {
        return 0;
    }

    @Override
    public int getInnerEndEdge() {
        return 0;
    }

    @Override
    public int getOuterEndEdge() {
        return 0;
    }

    @Override
    public int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        return 0;
    }

    @Override
    public int getChildHeightMeasureSpec(View child, int position, LayoutParams lp) {
        return 0;
    }

    @Override
    public void detachChildFromLayout(View child, int position, Flow flow) {
    }

    @Override
    public void attachChildToLayout(View child, int position, Flow flow, Rect childRect) {
    }
}
