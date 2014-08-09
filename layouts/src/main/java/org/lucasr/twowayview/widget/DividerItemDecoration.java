package org.lucasr.twowayview.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

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

        mItemSpacing = createSpacing(mDivider);
    }

    public DividerItemDecoration(Drawable verticalDivider) {
        mDivider = verticalDivider;
        mItemSpacing = createSpacing(mDivider);
    }

    private static ItemSpacingOffsets createSpacing(Drawable divider) {
        final int verticalSpacing;
        final int horizontalSpacing;
        if (divider != null) {
            verticalSpacing = divider.getIntrinsicHeight();
            horizontalSpacing = divider.getIntrinsicWidth();
        } else {
            verticalSpacing = 0;
            horizontalSpacing = 0;
        }

        final ItemSpacingOffsets spacing = new ItemSpacingOffsets(verticalSpacing, horizontalSpacing);
        spacing.setAddSpacingAtEnd(true);

        return spacing;
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent) {
        final BaseLayoutManager lm = (BaseLayoutManager) parent.getLayoutManager();

        final int rightWithPadding = parent.getWidth() - parent.getPaddingRight();
        final int bottomWithPadding = parent.getHeight() - parent.getPaddingBottom();

        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);

            final int childLeft = lm.getDecoratedLeft(child);
            final int childTop = lm.getDecoratedTop(child);
            final int childRight = lm.getDecoratedRight(child);
            final int childBottom = lm.getDecoratedBottom(child);

            final int rightOffset = childRight - child.getRight();
            if (rightOffset > 0 && childRight < rightWithPadding) {
                final int left = childRight - rightOffset;
                final int top = childTop;
                final int right = left + mDivider.getIntrinsicWidth();
                final int bottom = childBottom;

                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }

            final int bottomOffset = childBottom - child.getBottom();
            if (bottomOffset > 0 && childBottom < bottomWithPadding) {
                final int left = childLeft;
                final int top = childBottom - bottomOffset;
                final int right = childRight;
                final int bottom = top + mDivider.getIntrinsicHeight();

                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, int itemPosition, RecyclerView parent) {
        mItemSpacing.getItemOffsets(outRect, itemPosition, parent);
    }
}
