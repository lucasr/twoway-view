package org.lucasr.twowayview;

import android.content.Context;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnItemTouchListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;

public class ClickItemTouchListener implements OnItemTouchListener {
    private static final String LOGTAG = "ClickItemTouchListener";

    /**
     * Interface definition for a callback to be invoked when an item in the
     * host RecyclerView has been clicked.
     */
    public interface OnItemClickListener {
        /**
         * Callback method to be invoked when an item in the host RecyclerView
         * has been clicked.
         *
         * @param parent The RecyclerView where the click happened.
         * @param view The view within the RecyclerView that was clicked
         * @param position The position of the view in the adapter.
         * @param id The row id of the item that was clicked.
         */
        void onItemClick(RecyclerView parent, View view, int position, long id);
    }

    /**
     * Interface definition for a callback to be invoked when an item in the
     * host RecyclerView has been clicked and held.
     */
    public interface OnItemLongClickListener {
        /**
         * Callback method to be invoked when an item in the host RecyclerView
         * has been clicked and held.
         *
         * @param parent The RecyclerView where the click happened
         * @param view The view within the RecyclerView that was clicked
         * @param position The position of the view in the list
         * @param id The row id of the item that was clicked
         *
         * @return true if the callback consumed the long click, false otherwise
         */
        boolean onItemLongClick(RecyclerView parent, View view, int position, long id);
    }


    private final RecyclerView mHostView;
    private final GestureDetector mGestureDetector;

    private OnItemClickListener mItemClickListener;
    private OnItemLongClickListener mItemLongClickListener;

    private ClickItemTouchListener(RecyclerView hostView) {
        mHostView = hostView;

        final Context context = mHostView.getContext();
        mGestureDetector = new ItemClickGestureDetector(context, new ItemClickGestureListener());
    }

    private boolean isAttachedToWindow() {
        if (Build.VERSION.SDK_INT >= 19) {
            return mHostView.isAttachedToWindow();
        } else {
            return (mHostView.getHandler() != null);
        }
    }

    private boolean hasAdapter() {
        return (mHostView.getAdapter() != null);
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
        if (!isAttachedToWindow() || !hasAdapter()) {
            return false;
        }

        mGestureDetector.onTouchEvent(event);
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView recyclerView, MotionEvent event) {
        // We can silently track tap and and long presses by silently
        // intercepting touch events in the host RecyclerView.
    }

    /**
     * Register a callback to be invoked when an item in the host
     * RecyclerView has been clicked.
     *
     * @param listener The callback that will be invoked.
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        mItemClickListener = listener;
    }

    /**
     * Register a callback to be invoked when an item in the host
     * RecyclerView has been clicked and held.
     *
     * @param listener The callback that will be invoked.
     */
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        if (!mHostView.isLongClickable()) {
            mHostView.setLongClickable(true);
        }

        mItemLongClickListener = listener;
    }

    public static ClickItemTouchListener addTo(RecyclerView recyclerView) {
        ClickItemTouchListener listener = new ClickItemTouchListener(recyclerView);
        recyclerView.addOnItemTouchListener(listener);
        return listener;
    }

    private class ItemClickGestureDetector extends GestureDetector {
        private final ItemClickGestureListener mGestureListener;

        public ItemClickGestureDetector(Context context, ItemClickGestureListener listener) {
            super(context, listener);
            mGestureListener = listener;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final boolean handled = super.onTouchEvent(event);

            final int action = event.getAction() & MotionEventCompat.ACTION_MASK;
            if (action == MotionEvent.ACTION_UP) {
                mGestureListener.dispatchSingleTapUpIfNeeded(event);
            }

            return handled;
        }
    }

    private class ItemClickGestureListener extends SimpleOnGestureListener {
        private View mTargetChild;

        public void dispatchSingleTapUpIfNeeded(MotionEvent event) {
            // When the long press hook is called but the long press listener
            // returns false, the target child will be left around to be
            // handled later. In this case, we should still treat the gesture
            // as potential item click.
            if (mTargetChild != null) {
                onSingleTapUp(event);
            }
        }

        @Override
        public boolean onDown(MotionEvent event) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();

            mTargetChild = mHostView.findChildViewUnder(x, y);
            return (mTargetChild != null);
        }

        @Override
        public void onShowPress(MotionEvent event) {
            if (mTargetChild != null) {
                mTargetChild.setPressed(true);
            }
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            boolean handled = false;

            if (mTargetChild != null) {
                mTargetChild.setPressed(false);

                if (mItemClickListener != null) {
                    final int position = mHostView.getChildPosition(mTargetChild);
                    final long id = mHostView.getAdapter().getItemId(position);
                    mItemClickListener.onItemClick(mHostView, mTargetChild, position, id);
                    handled = true;
                }

                mTargetChild = null;
            }

            return handled;
        }

        @Override
        public boolean onScroll(MotionEvent event, MotionEvent event2, float v, float v2) {
            if (mTargetChild != null) {
                mTargetChild.setPressed(false);
                mTargetChild = null;

                return true;
            }

            return false;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            if (mTargetChild != null) {
                if (mItemLongClickListener != null) {
                    final int position = mHostView.getChildPosition(mTargetChild);
                    final long id = mHostView.getAdapter().getItemId(position);
                    final boolean handled = mItemLongClickListener.onItemLongClick(mHostView,
                            mTargetChild, position, id);

                    if (handled) {
                        mTargetChild.setPressed(false);
                        mTargetChild = null;
                    }
                }
            }
        }
    }
}
