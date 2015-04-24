package org.lucasr.twowayview.widget;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Build;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.view.DragEvent;
import android.view.View;
import android.widget.AbsListView;
import org.lucasr.twowayview.TwoWayLayoutManager;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
/*package*/ final class Reorderer implements View.OnDragListener {
    /**
     * Allows for system wide drag-drop compatability.
     * http://developer.android.com/guide/topics/ui/drag-drop.html#HandleDrop
     */
    /*package*/ final static String MIME_TYPE = "x-org-lucasr-twowayview-widget/item";

    private final TwoWayView twv;

    private boolean mIsReordering = false;

    /**
     * Allows for system wide drag-drop compatability.
     * http://developer.android.com/guide/topics/ui/drag-drop.html#HandleDrop
     */
    private ClipData mMyClipData;

    /**
     * Creates a new {@code Reorderer} for a given {@link TwoWayView}.
     */
    /*package*/ Reorderer(TwoWayView twv) {
        this.twv = twv;
        this.twv.setOnDragListener(this);
        this.mMyClipData = new ClipData(new ClipDescription("", new String[] {MIME_TYPE}), new ClipData.Item(""));
    }

    /**
     * As per this bug - https://code.google.com/p/android/issues/detail?id=25073
     */
    /*package*/ boolean dispatchDragEvent(DragEvent ev) {
        boolean r = twv.superDispatchDragEvent(ev);
        if (r && (ev.getAction() == DragEvent.ACTION_DRAG_STARTED
                || ev.getAction() == DragEvent.ACTION_DRAG_ENDED)){
            // If we got a start or end and the return value is true, our
            // onDragEvent wasn't called by ViewGroup.dispatchDragEvent
            // So we do it here.
            onDragEvent(ev);
            return true;
        }
        else {
            return true;
        }
    }

    /*package*/ boolean onDragEvent(DragEvent ev) {
        return onDrag(null, ev);
    }

    private Handler scrollHandler = new Handler();
    private int scrollDistance;
    private boolean isScrolling = false;
    private Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if(isScrolling) {
                if(twv.getOrientation() == TwoWayLayoutManager.Orientation.VERTICAL) {
                    twv.smoothScrollBy(0, scrollDistance);
                }
                else {
                    twv.smoothScrollBy(scrollDistance, 0);
                }
                scrollHandler.postDelayed(this, 250);
            }
        }
    };

    private int lastKnownPosition = TwoWayView.NO_POSITION;
    @Override
    public boolean onDrag(View v, DragEvent ev) {
        ClipDescription desc = ev.getClipDescription();
        // if this isn't our dragged item, ignore it
        if(desc != null && !desc.hasMimeType(MIME_TYPE)) {
            return false;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        RecyclerView.ViewHolder viewHolderToSendToAdapter = null;

        if(ev.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
            // reset our last known position for a new drag
            lastKnownPosition = AbsListView.INVALID_POSITION;
        }
        else if(ev.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
            boolean isVertical = (twv.getOrientation() == TwoWayLayoutManager.Orientation.VERTICAL);
            int pointer = (isVertical ? y : x);
            int bottomBound = (isVertical ? twv.getHeight() : twv.getWidth());
            int bottomOffset = bottomBound - pointer;

            View firstVisibleView = twv.getChildAt(twv.getFirstVisiblePosition());
            scrollDistance = bottomBound / 8;
            int scrollThreshold = scrollDistance / 2;
            if(firstVisibleView != null) {
                int bound = (isVertical ? firstVisibleView.getHeight() : firstVisibleView.getWidth());
                scrollDistance = ((bound * 2) + (bound / 2));
                scrollThreshold = bound / 8;
            }

            if(pointer <= scrollThreshold && bottomOffset >= scrollThreshold) {
                scrollDistance = -scrollDistance;
                if(!isScrolling) {
                    scrollHandler.post(scrollRunnable);
                    isScrolling = true;
                }
            }
            else if(pointer >= scrollThreshold && bottomOffset <= scrollThreshold) {
                if(!isScrolling) {
                    scrollHandler.post(scrollRunnable);
                    isScrolling = true;
                }
            }
            else {
                scrollHandler.removeCallbacks(scrollRunnable);
                isScrolling = false;
            }

            // get the view that the dragged item is over
            View viewAtCurrentPosition = twv.findChildViewUnder(ev.getX(), ev.getY());
            if(viewAtCurrentPosition != null) {
                // and get its position
                int currentPosition = twv.getChildPosition(viewAtCurrentPosition);
                // this is an optimization so that we don't keep sending the same ViewHolder to the adapter
                // for every pixel that it moves (which would be redundant anyway)
                if(currentPosition != lastKnownPosition) {
                    // if it's a valid position, use it to get the ViewHolder to send to the adapter
                    if(currentPosition != TwoWayView.NO_POSITION) {
                        viewHolderToSendToAdapter = twv.findViewHolderForPosition(currentPosition);
                    }
                    // this position is now our known position
                    lastKnownPosition = currentPosition;
                }
            }
            else { // if we can't get the view we're over, we don't have a known position
                lastKnownPosition = TwoWayView.NO_POSITION;
            }
        }
        else if(ev.getAction() == DragEvent.ACTION_DROP) {
            // stop scrolling
            scrollHandler.removeCallbacks(scrollRunnable);
            isScrolling = false;

            // get the view that the dragged item is over
            View viewAtCurrentPosition = twv.findChildViewUnder(ev.getX(), ev.getY());
            if(viewAtCurrentPosition != null) {
                // and get its position
                int currentPosition = twv.getChildPosition(viewAtCurrentPosition);
                // if it's a valid position, use it to get the ViewHolder to send to the adapter
                if(currentPosition != TwoWayView.NO_POSITION) {
                    viewHolderToSendToAdapter = twv.findViewHolderForPosition(currentPosition);
                }
            }
            // reset our last known position since we dropped
            lastKnownPosition = TwoWayView.NO_POSITION;
        }
        else if(ev.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            setIsReordering(false);

            // stop scrolling
            scrollHandler.removeCallbacks(scrollRunnable);
            isScrolling = false;

            // reset our last known position since we're done
            lastKnownPosition = TwoWayView.NO_POSITION;
        }

        ReordererAdapterDecorator adapter = twv.getReordererAdapter();
        return adapter != null && adapter.onDrag(viewHolderToSendToAdapter, ev);
    }

    /*package*/ boolean isReordering() {
        return mIsReordering;
    }

    /*package*/ void setIsReordering(boolean isReordering) {
        this.mIsReordering = isReordering;
        OnReorderingListener reorderingListener = twv.getOnReorderingListener();
        if(reorderingListener != null) {
            if(isReordering) {
                reorderingListener.onStartReordering();
            }
            else {
                reorderingListener.onStopReordering();
            }
        }
    }

    /*package*/ final boolean startReorder(int position) {
        if(isReordering()) {
            throw new IllegalStateException("Cannot start reordering if a reordering operation is already in progress");
        }
        if(twv.getAdapter() == null) {
            throw new IllegalStateException("Cannot start a reorder operation if there is no adapter set");
        }
        if(position < 0 || position >= twv.getAdapter().getItemCount()) {
            throw new IndexOutOfBoundsException("Cannot start a reorder operation if the position is out of the bounds of the adapter");
        }

        // TODO: custom DragShadowBuilder

        View.DragShadowBuilder dragShadowBuilder;
        View viewAtReorderPosition = twv.findViewHolderForPosition(position).itemView;
        dragShadowBuilder = new View.DragShadowBuilder(viewAtReorderPosition);

        boolean success = twv.startDrag(mMyClipData, dragShadowBuilder, null, 0);

        if(success) {
            setIsReordering(true);
            twv.getReordererAdapter().startReordering(position);
        }

        return success;
    }
}
