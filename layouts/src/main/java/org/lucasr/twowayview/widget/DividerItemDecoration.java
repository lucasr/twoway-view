package org.lucasr.twowayview.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.util.AttributeSet;

/**
 * {@link android.support.v7.widget.RecyclerView.ItemDecoration} that applies a
 * divider vertically and horizontally between the items of the target
 * {@link android.support.v7.widget.RecyclerView}.
 */
public class DividerItemDecoration extends ItemDecoration {
    private final ItemSpacingOffsets mItemSpacing;
    private final Drawable mDivider;

    public DividerItemDecoration(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DividerItemDecoration(Context context, AttributeSet attrs, int defStyle) {
        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.DividerItemDecoration, defStyle, 0);
        mDivider = a.getDrawable(R.styleable.DividerItemDecoration_android_divider);
        a.recycle();

        mItemSpacing = DividerItemDecoratorHelper.createSpacing(mDivider);
    }

    public DividerItemDecoration(Drawable verticalDivider) {
        mDivider = verticalDivider;
        mItemSpacing = DividerItemDecoratorHelper.createSpacing(mDivider);
    }


    @Override
    public void onDrawOver(Canvas c, RecyclerView parent) {
       DividerItemDecoratorHelper.onDrawOver(c, parent, mDivider);
    }

    @Override
    public void getItemOffsets(Rect outRect, int itemPosition, RecyclerView parent) {
        mItemSpacing.getItemOffsets(outRect, itemPosition, parent);
    }
}
