package org.lucasr.twowayview.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

/**
 * {@link android.support.v7.widget.RecyclerView.ItemDecoration} that applies two different
 * dividers in vertical and horizontal orientations between the items of the target
 * {@link android.support.v7.widget.RecyclerView}.
 */
public class TwoDividerItemDecorator extends RecyclerView.ItemDecoration {

    private final ItemSpacingOffsets mVerticalItemSpacing;
    private final ItemSpacingOffsets mHorizontalItemSpacing;

    private final Drawable mVerticalDivider;
    private final Drawable mHorizontalDivider;

    public TwoDividerItemDecorator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoDividerItemDecorator(Context context, AttributeSet attrs, int defStyle) {

        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.DividerItemDecoration, defStyle, 0);
        mVerticalDivider = a.getDrawable(R.styleable.TwoDividerItemDecoration_verticalDivider);
        mHorizontalDivider = a.getDrawable(R.styleable.TwoDividerItemDecoration_horizontalDivider);
        a.recycle();

        mVerticalItemSpacing = DividerItemDecoratorHelper.createSpacing(mVerticalDivider);
        mHorizontalItemSpacing = DividerItemDecoratorHelper.createSpacing(mHorizontalDivider);
    }

    public TwoDividerItemDecorator(Drawable verticalDivider,Drawable horizontalDivider) {

        mVerticalDivider = verticalDivider;
        mVerticalItemSpacing = DividerItemDecoratorHelper.createSpacing(mVerticalDivider);

        mHorizontalDivider = horizontalDivider;
        mHorizontalItemSpacing = DividerItemDecoratorHelper.createSpacing(mHorizontalDivider);
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent) {
        final BaseLayoutManager lm = (BaseLayoutManager) parent.getLayoutManager();

        if (lm.isVertical())
            DividerItemDecoratorHelper.onDrawOver(c, parent, mVerticalDivider);
        else
            DividerItemDecoratorHelper.onDrawOver(c, parent, mHorizontalDivider);
    }

    @Override
    public void getItemOffsets(Rect outRect, int itemPosition, RecyclerView parent) {
        final BaseLayoutManager lm = (BaseLayoutManager) parent.getLayoutManager();

        if (lm.isVertical())
            mVerticalItemSpacing.getItemOffsets(outRect, itemPosition, parent);
        else
            mHorizontalItemSpacing.getItemOffsets(outRect, itemPosition, parent);
    }
}
