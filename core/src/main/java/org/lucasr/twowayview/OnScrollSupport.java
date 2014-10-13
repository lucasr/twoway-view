package org.lucasr.twowayview;

import android.support.v7.widget.RecyclerView;


public class OnScrollSupport {

    public interface OnScrollListener {


        /**
         * Callback method to be invoked while the  view is being scrolled. If the
         * view is being scrolled, this method will be called before the next frame of the scroll is
         * rendered. In particular, it will be called before any calls to
         * {@link android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)}.
         *
         * @param view        The view whose scroll state is being reported
         * @param scrollState The current scroll state.
         */
        public void onScrollStateChanged(RecyclerView view, int scrollState);

        /**
         * Callback method to be invoked when the View has been scrolled. This will be
         * called after the scroll has completed
         *
         * @param view             The view whose scroll state is being reported
         * @param firstVisibleItem the index of the first visible cell (ignore if
         *                         visibleItemCount == 0)
         * @param visibleItemCount the number of visible cells
         * @param totalItemCount   the number of items in the list adaptor
         */
        public void onScroll(RecyclerView view, int firstVisibleItem, int visibleItemCount,
                             int totalItemCount);
    }

    private final TwoWayView mRecyclerView;
    private final ScrollListener mRecyclerScrollListener;

    private OnScrollListener mScrollListener;

    public OnScrollSupport(TwoWayView recyclerView) {
        mRecyclerView = recyclerView;

        mRecyclerScrollListener = new ScrollListener();
        recyclerView.setOnScrollListener(mRecyclerScrollListener);
    }

    /**
     * Register a callback to be invoked when an item in the
     * RecyclerView has been Scrolled.
     *
     * @param listener The callback that will be invoked.
     */
    public void setOnItemScrollListener(OnScrollListener listener) {
        mScrollListener = listener;
    }


    private class ScrollListener implements RecyclerView.OnScrollListener {
        @Override
        public void onScrollStateChanged(int scrollState) {
            if (mScrollListener != null) {
                mScrollListener.onScrollStateChanged(mRecyclerView, scrollState);
            }
        }

        @Override
        public void onScrolled(int i, int i2) {
            int firstVisibleItem = mRecyclerView.getFirstVisiblePosition();
            int visibleItemCount = mRecyclerView.getChildCount();
            int totalItemCount = mRecyclerView.getAdapter().getItemCount();
            if (mScrollListener != null) {
                mScrollListener.onScroll(mRecyclerView, firstVisibleItem, visibleItemCount, totalItemCount);
            }
        }
    }

    ;
}
