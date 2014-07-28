package org.lucasr.twowayview;

import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.RecyclerView.OnItemTouchListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class TWItemClickListener implements OnItemTouchListener {
    private static final String LOGTAG = "TWItemClickListener";

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

    private static final int INVALID_POINTER = -1;
    private static final int INVALID_POSITION = -1;

    private static final int TOUCH_MODE_REST = -1;
    private static final int TOUCH_MODE_DOWN = 0;
    private static final int TOUCH_MODE_TAP = 1;
    private static final int TOUCH_MODE_DONE_WAITING = 2;

    private final RecyclerView mHostView;

    private OnItemClickListener mItemClickListener;
    private OnItemLongClickListener mItemLongClickListener;

    private int mTouchMode = TOUCH_MODE_REST;
    private final int mTouchSlop;
    private int mMotionPosition;
    private float mLastTouchPos;
    private float mTouchRemainderPos;
    private int mActivePointerId;

    private final Rect mTempRect = new Rect();

    private CheckForTap mPendingCheckForTap;
    private CheckForLongPress mPendingCheckForLongPress;
    private PerformClick mPerformClick;
    private Runnable mTouchModeReset;

    private TWItemClickListener(RecyclerView hostView) {
        mHostView = hostView;

        final ViewConfiguration vc = ViewConfiguration.get(mHostView.getContext());
        mTouchSlop = vc.getScaledTouchSlop();
    }

    private boolean isAttachedToWindow() {
        if (Build.VERSION.SDK_INT >= 19) {
            return mHostView.isAttachedToWindow();
        } else {
            return (mHostView.getHandler() != null);
        }
    }

    private boolean isVertical() {
        final LayoutManager layout = mHostView.getLayoutManager();
        if (layout == null) {
            return true;
        }

        return layout.canScrollVertically();
    }

    private boolean hasAdapter() {
        return (mHostView.getAdapter() != null);
    }

    private View getViewAtPosition(int position) {
        return mHostView.getChildAt(position - getFirstVisiblePosition());
    }

    private int getFirstVisiblePosition() {
        RecyclerView.LayoutParams lp =
                (RecyclerView.LayoutParams) mHostView.getChildAt(0).getLayoutParams();
        return lp.getViewPosition();
    }

    private int pointToPosition(int x, int y) {
        final int childCount = mHostView.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = mHostView.getChildAt(i);

            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(mTempRect);

                if (mTempRect.contains(x, y)) {
                    return getFirstVisiblePosition() + i;
                }
            }
        }

        return INVALID_POSITION;
    }

    private void triggerCheckForTap() {
        if (mPendingCheckForTap == null) {
            mPendingCheckForTap = new CheckForTap();
        }

        mHostView.postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
    }

    private void cancelCheckForTap() {
        if (mPendingCheckForTap == null) {
            return;
        }

        mHostView.removeCallbacks(mPendingCheckForTap);
    }

    private void triggerCheckForLongPress() {
        if (mPendingCheckForLongPress == null) {
            mPendingCheckForLongPress = new CheckForLongPress();
        }

        mHostView.postDelayed(mPendingCheckForLongPress,
                ViewConfiguration.getLongPressTimeout());
    }

    private void cancelCheckForLongPress() {
        if (mPendingCheckForLongPress == null) {
            return;
        }

        mHostView.removeCallbacks(mPendingCheckForLongPress);
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
        if (!isAttachedToWindow() || !hasAdapter()) {
            return false;
        }

        final boolean isVertical = isVertical();

        final int action = event.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                final float x = event.getX();
                final float y = event.getY();

                mLastTouchPos = (isVertical ? y : x);
                mActivePointerId = MotionEventCompat.getPointerId(event, 0);
                mTouchRemainderPos = 0;

                mMotionPosition = pointToPosition((int) x, (int) y);

                if (mMotionPosition >= 0) {
                    mTouchMode = TOUCH_MODE_DOWN;
                    triggerCheckForTap();
                }
                break;

            case MotionEvent.ACTION_MOVE: {
                if (mTouchMode != TOUCH_MODE_DOWN) {
                    return false;
                }

                final int index = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                if (index < 0) {
                    Log.e(LOGTAG, "onInterceptTouchEvent could not find pointer with id " +
                            mActivePointerId + " - did RecyclerView receive an inconsistent " +
                            "event stream?");
                    return false;
                }

                final float pos;
                if (isVertical) {
                    pos = MotionEventCompat.getY(event, index);
                } else {
                    pos = MotionEventCompat.getX(event, index);
                }

                final float diff = pos - mLastTouchPos + mTouchRemainderPos;
                final int delta = (int) diff;
                mTouchRemainderPos = diff - delta;

                if (Math.abs(delta) > mTouchSlop) {
                    cancelCheckForTap();
                    cancelCheckForLongPress();

                    mTouchMode = TOUCH_MODE_REST;
                }

                break;
            }

            case MotionEvent.ACTION_CANCEL:
                cancelCheckForTap();
                mTouchMode = TOUCH_MODE_REST;

                final View motionView = getViewAtPosition(mMotionPosition);
                if (motionView != null) {
                    motionView.setPressed(false);
                }

                break;


            case MotionEvent.ACTION_UP:
                if (mTouchMode != TOUCH_MODE_DOWN &&
                    mTouchMode != TOUCH_MODE_TAP &&
                    mTouchMode != TOUCH_MODE_DONE_WAITING) {
                    return false;
                }

                final View child = getViewAtPosition(mMotionPosition);
                if (child != null && !child.hasFocusable()) {
                    if (mTouchMode != TOUCH_MODE_DOWN) {
                        child.setPressed(false);
                    }

                    if (mPerformClick == null) {
                        mPerformClick = new PerformClick();
                    }

                    final PerformClick performClick = mPerformClick;
                    performClick.clickMotionPosition = mMotionPosition;

                    if (mTouchMode == TOUCH_MODE_DOWN || mTouchMode == TOUCH_MODE_TAP) {
                        if (mTouchMode == TOUCH_MODE_DOWN) {
                            cancelCheckForTap();
                        } else {
                            cancelCheckForLongPress();
                        }

                        mTouchMode = TOUCH_MODE_TAP;

                        child.setPressed(true);

                        if (mTouchModeReset != null) {
                            mHostView.removeCallbacks(mTouchModeReset);
                        }

                        mTouchModeReset = new Runnable() {
                            @Override
                            public void run() {
                                mTouchMode = TOUCH_MODE_REST;

                                child.setPressed(false);

                                performClick.run();
                                mTouchModeReset = null;
                            }
                        };

                        mHostView.postDelayed(mTouchModeReset,
                                ViewConfiguration.getPressedStateDuration());
                    } else {
                        performClick.run();
                    }
                }

                mTouchMode = TOUCH_MODE_REST;
                mActivePointerId = INVALID_POINTER;
                break;
        }

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

    public static TWItemClickListener addTo(RecyclerView recyclerView) {
        TWItemClickListener listener = new TWItemClickListener(recyclerView);
        recyclerView.addOnItemTouchListener(listener);
        return listener;
    }

    private class PerformClick implements Runnable {
        int clickMotionPosition;

        @Override
        public void run() {
            final RecyclerView.Adapter adapter = mHostView.getAdapter();
            final int itemCount = (adapter != null ? adapter.getItemCount() : 0);
            final int pos = clickMotionPosition;

            if (itemCount > 0 && pos != INVALID_POSITION && pos < adapter.getItemCount()) {
                final View child = getViewAtPosition(pos);
                if (child != null && mItemClickListener != null) {
                    mItemClickListener.onItemClick(mHostView, child, pos, adapter.getItemId(pos));
                }
            }
        }
    }

    private final class CheckForTap implements Runnable {
        @Override
        public void run() {
            if (mTouchMode != TOUCH_MODE_DOWN) {
                return;
            }

            mTouchMode = TOUCH_MODE_TAP;

            final View child = getViewAtPosition(mMotionPosition);
            if (child != null && !child.hasFocusable()) {
                child.setPressed(true);

                if (mHostView.isLongClickable()) {
                    triggerCheckForLongPress();
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }

    private class CheckForLongPress implements Runnable {
        @Override
        public void run() {
            final RecyclerView.Adapter adapter = mHostView.getAdapter();
            final int pos = mMotionPosition;

            final View child = getViewAtPosition(pos);
            if (child != null) {
                final long longPressId = adapter.getItemId(mMotionPosition);

                boolean handled = false;
                if (mItemLongClickListener != null) {
                    handled = mItemLongClickListener.onItemLongClick(mHostView, child, pos, longPressId);
                }

                if (handled) {
                    mTouchMode = TOUCH_MODE_REST;
                    child.setPressed(false);
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }
}
