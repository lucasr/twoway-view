package org.lucasr.twowayview.widget;

/**
 * Listen for changes in a {@link TwoWayView}'s reordering state.
 */
public interface OnReorderingListener {
    /**
     * Called when a {@link TwoWayView} has started reordering.
     */
    public void onStartReordering();

    /**
     * Called when a {@link TwoWayView} has stopped reordering.
     */
    public void onStopReordering();
}
