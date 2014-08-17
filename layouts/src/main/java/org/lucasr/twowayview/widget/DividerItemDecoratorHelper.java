package org.lucasr.twowayview.widget;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 *
 */
public class DividerItemDecoratorHelper {


    public static ItemSpacingOffsets createSpacing(Drawable divider) {
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

    public static void onDrawOver(Canvas c, RecyclerView parent,Drawable divider) {
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
                final int right = left + divider.getIntrinsicWidth();
                final int bottom = childBottom;

                divider.setBounds(left, top, right, bottom);
                divider.draw(c);
            }

            final int bottomOffset = childBottom - child.getBottom();
            if (bottomOffset > 0 && childBottom < bottomWithPadding) {
                final int left = childLeft;
                final int top = childBottom - bottomOffset;
                final int right = childRight;
                final int bottom = top + divider.getIntrinsicHeight();

                divider.setBounds(left, top, right, bottom);
                divider.draw(c);
            }
        }
    }

}
