package org.lucasr.twowayview.widget;

/**
 * Any {@link android.support.v7.widget.RecyclerView.Adapter} that is passed to one of
 * {@link TwoWayView#setReorderableAdapter} or {@link TwoWayView#swapReorderableAdapter}
 * should implement {@code ReorderableAdapter} (or its subinterfaces).
 */
public interface ReorderableAdapter {
    /**
     * Called when an item that was being dragged is dropped. Most implementations will want to
     * remove the item from the dataset at {@code from} and reinsert it at {@code to}.
     * @param from the position the item was dragged from
     * @param to the position the item was dropped on
     */
    public void onItemDropped(int from, int to);
}
