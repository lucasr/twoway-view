package org.lucasr.twowayview.widget;

import android.view.View;

/**
 * A {@link ReorderableAdapter} that allows for overriding the decoration applied to an item at a "drop position."
 * If an item is dragged over another, that view will slide over 1 position, leaving an "empty space."
 * The default implementation calls {@link View#setVisibility} with {@link View#INVISIBLE} for the {@code View}
 * at the drop position, and then calls {@code View.setVisibility} with {@link View#VISIBLE} when it is no longer
 * at the drop position. If an implementation uses a {@code View}'s visibility, or wants to apply custom animations
 * to this transition, its {@link android.support.v7.widget.RecyclerView.Adapter} should implement
 * this instead of {@link ReorderableAdapter}.
 * @see org.lucasr.twowayview.widget.ReorderableAdapter
 */
public interface ReorderableAdapterViewDecorator extends ReorderableAdapter {
    /**
     * Alter the {@code View}'s appearance when its position is the "drop position."
     * @param view the {@link View} that the dragged item is currently over
     */
    public void applyDropPositionDecoration(View view);

    /**
     * Undo whatever alterations were made in {@link ReorderableAdapterViewDecorator#applyDropPositionDecoration}.
     * This is called everytime its position is invalidated; not just after {@code applyDropPositionDecoration},
     * so it may be best to check if the alterations were already undone.
     * @param view a {@link View} that the dragged item is <b>not</b> currently over
     */
    public void undoDropPositionDecoration(View view);
}
