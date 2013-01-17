/*
 * Copyright (C) 2013 Lucas Rocha
 *
 * This code was initially based on Android's StaggeredGridView.
 *
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.twowayview;

import java.util.ArrayList;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.Scroller;

public class TwoWayView extends ViewGroup {
    private static final String LOGTAG = "TwoWayListView";
    private static final boolean DEBUG = true;

    public static enum ScrollDirection {
        VERTICAL,
        HORIZONTAL;
    };

    private static final int TOUCH_MODE_IDLE = 0;
    private static final int TOUCH_MODE_DRAGGING = 1;
    private static final int TOUCH_MODE_FLINGING = 2;

    private ListAdapter mAdapter;

    private boolean mIsVertical;

    private int mItemMargin;

    private boolean mPopulating;
    private boolean mInLayout;

    private final RecycleBin mRecycler;
    private final AdapterDataSetObserver mObserver;

    private boolean mDataChanged;
    private int mItemCount;
    private boolean mHasStableIds;

    private int mFirstPosition;

    private int mRestoreOffset;
    private Integer mItemsStart;
    private Integer mItemsEnd;

    private final int mTouchSlop;
    private final int mMaximumVelocity;
    private final int mFlingVelocity;
    private float mLastTouchPos;
    private float mTouchRemainderPos;
    private int mActivePointerId;

    private int mTouchMode;
    private final VelocityTracker mVelocityTracker = VelocityTracker.obtain();
    private final Scroller mScroller;

    private final EdgeEffectCompat mStartEdge;
    private final EdgeEffectCompat mEndEdge;

    public TwoWayView(Context context) {
        this(context, null);
    }

    public TwoWayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoWayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mItemsStart = null;
        mItemsEnd = null;

        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMaximumVelocity = vc.getScaledMaximumFlingVelocity();
        mFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mScroller = new Scroller(context);

        mIsVertical = true;

        mRecycler = new RecycleBin();
        mObserver = new AdapterDataSetObserver();

        mStartEdge = new EdgeEffectCompat(context);
        mEndEdge = new EdgeEffectCompat(context);

        setWillNotDraw(false);
        setClipToPadding(false);
    }

    public void setScrollDirection(ScrollDirection scrollDirection) {
        final boolean isVertical = (scrollDirection.compareTo(ScrollDirection.VERTICAL) == 0);
        if (mIsVertical == isVertical) {
            return;
        }

        mIsVertical = isVertical;
        populate();
    }

    public ScrollDirection getScrollDirection() {
        return (mIsVertical ? ScrollDirection.VERTICAL : ScrollDirection.HORIZONTAL);
    }

    public void setItemMargin(int itemMargin) {
        if (mItemMargin == itemMargin) {
            return;
        }

        mItemMargin = itemMargin;
        populate();
    }

    public int getItemMargin() {
        return mItemMargin;
    }

    public ListAdapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(ListAdapter adapter) {
        if (DEBUG) {
            Log.d(LOGTAG, "Setting adapter");
        }

        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);

            if (DEBUG) {
                Log.d(LOGTAG, "Unregistered old adapter");
            }
        }

        // TODO: If the new adapter says that there are stable IDs,
        // remove certain layout records and onscreen views if they
        // have changed instead of removing all of the state here.
        clearAllState();

        mAdapter = adapter;
        mDataChanged = true;

        if (mAdapter != null) {
            if (DEBUG) {
                Log.d(LOGTAG, "Registering new adapter");
            }

            mItemCount = adapter.getCount();
            mAdapter.registerDataSetObserver(mObserver);
            mRecycler.setViewTypeCount(adapter.getViewTypeCount());
            mHasStableIds = adapter.hasStableIds();
        } else {
            if (DEBUG) {
                Log.d(LOGTAG, "New adapter is null");
            }

            mItemCount = 0;
            mHasStableIds = false;
        }

        populate();
    }

    /**
     * Return the first adapter position with a view currently attached as
     * a child view of this grid.
     *
     * @return the adapter position represented by the view at getChildAt(0).
     */
    public int getFirstPosition() {
        return mFirstPosition;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) {
            Log.d(LOGTAG, "Touch event intercepted");
        }

        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event: ACTION_DOWN");
                }

                mVelocityTracker.clear();
                mScroller.abortAnimation();

                mLastTouchPos = (mIsVertical ? ev.getY() : ev.getX());

                if (DEBUG) {
                    Log.d(LOGTAG, "new last touched position: " + mLastTouchPos);
                }

                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mTouchRemainderPos = 0;

                if (mTouchMode == TOUCH_MODE_FLINGING) {
                    mTouchMode = TOUCH_MODE_DRAGGING;
                    return true;
                }

                break;

            case MotionEvent.ACTION_MOVE: {
                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event: ACTION_MOVE");
                }

                final int index = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (index < 0) {
                    Log.e(LOGTAG, "onInterceptTouchEvent could not find pointer with id " +
                            mActivePointerId + " - did TwoWayView receive an inconsistent " +
                            "event stream?");
                    return false;
                }

                final float pos;
                if (mIsVertical) {
                    pos = MotionEventCompat.getY(ev, index);
                } else {
                    pos = MotionEventCompat.getX(ev, index);
                }

                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event position is: " + pos);
                }

                final float diff = pos - mLastTouchPos + mTouchRemainderPos;
                final int delta = (int) diff;
                mTouchRemainderPos = diff - delta;

                if (DEBUG) {
                    Log.d(LOGTAG, "Is dragging? diff=" + diff + " slop=" + mTouchSlop);
                }

                if (Math.abs(diff) > mTouchSlop) {
                    mTouchMode = TOUCH_MODE_DRAGGING;

                    if (DEBUG) {
                        Log.d(LOGTAG, "Touch slop crossed, now dragging");
                    }

                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (DEBUG) {
            Log.d(LOGTAG, "Touch event");
        }

        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event: ACTION_DOWN");
                }

                mVelocityTracker.clear();
                mScroller.abortAnimation();

                mLastTouchPos = (mIsVertical ? ev.getY() : ev.getX());

                if (DEBUG) {
                    Log.d(LOGTAG, "new last touched position: " + mLastTouchPos);
                }

                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mTouchRemainderPos = 0;
                break;

            case MotionEvent.ACTION_MOVE: {
                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event: ACTION_MOVE");
                }

                final int index = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (index < 0) {
                    Log.e(LOGTAG, "onInterceptTouchEvent could not find pointer with id " +
                            mActivePointerId + " - did StaggeredGridView receive an inconsistent " +
                            "event stream?");
                    return false;
                }

                final float pos;
                if (mIsVertical) {
                    pos = MotionEventCompat.getY(ev, index);
                } else {
                    pos = MotionEventCompat.getX(ev, index);
                }

                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event position is: " + pos);
                }

                final float diff = pos - mLastTouchPos + mTouchRemainderPos;
                final int delta = (int) diff;
                mTouchRemainderPos = diff - delta;

                if (DEBUG) {
                    Log.d(LOGTAG, "Is dragging? diff=" + diff + " slop=" + mTouchSlop);
                }

                if (Math.abs(diff) > mTouchSlop) {
                    mTouchMode = TOUCH_MODE_DRAGGING;

                    if (DEBUG) {
                        Log.d(LOGTAG, "Touch slop crossed, now dragging");
                    }
                }

                if (mTouchMode == TOUCH_MODE_DRAGGING) {
                    mLastTouchPos = pos;

                    if (DEBUG) {
                        Log.d(LOGTAG, "In dragging mode, tracking motion");
                    }

                    if (!trackMotionScroll(delta, true)) {
                        // Break fling velocity if we impacted an edge
                        mVelocityTracker.clear();
                    }
                }

                break;
            }

            case MotionEvent.ACTION_CANCEL:
                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event: ACTION_CANCEL");
                }

                mTouchMode = TOUCH_MODE_IDLE;
                break;

            case MotionEvent.ACTION_UP: {
                if (DEBUG) {
                    Log.d(LOGTAG, "Touch event: ACTION_UP");
                }

                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                final float velocity;
                if (mIsVertical) {
                    velocity = VelocityTrackerCompat.getYVelocity(mVelocityTracker,
                            mActivePointerId);
                } else {
                    velocity = VelocityTrackerCompat.getXVelocity(mVelocityTracker,
                            mActivePointerId);
                }

                if (Math.abs(velocity) >= mFlingVelocity) { // TODO
                    mTouchMode = TOUCH_MODE_FLINGING;

                    mScroller.fling(0, 0,
                                    (int) (mIsVertical ? 0 : velocity),
                                    (int) (mIsVertical ? velocity : 0),
                                    (mIsVertical ? 0 : Integer.MIN_VALUE),
                                    (mIsVertical ? 0 : Integer.MAX_VALUE),
                                    (mIsVertical ? Integer.MIN_VALUE : 0),
                                    (mIsVertical ? Integer.MAX_VALUE : 0));

                    if (DEBUG) {
                        Log.d(LOGTAG, "Fling detected");
                    }

                    mLastTouchPos = 0;

                    ViewCompat.postInvalidateOnAnimation(this);
                } else {
                    mTouchMode = TOUCH_MODE_IDLE;
                }

                break;
            }
        }

        return true;
    }

    /**
    *
    * @param delta Pixels that content should move by
    * @return true if the movement completed, false if it was stopped prematurely.
    */
    private boolean trackMotionScroll(int delta, boolean allowOverScroll) {
        if (DEBUG) {
            Log.d(LOGTAG, "Tracking motion scroll");
        }

        final boolean contentFits = contentFits();
        final int allowOverhang = Math.abs(delta);

        if (DEBUG) {
            Log.d(LOGTAG, "Content fits: " + contentFits + " for delta " + delta);
        }

        final int overScrolledBy;
        final int movedBy;

        if (!contentFits) {
            final int overhang;
            final boolean back;

            mPopulating = true;

            if (delta > 0) {
                if (DEBUG) {
                    Log.d(LOGTAG, "Running fillBefore");
                }

                overhang = fillBefore(mFirstPosition - 1, allowOverhang);
                back = true;
            } else {
                if (DEBUG) {
                    Log.d(LOGTAG, "Running fillAfter");
                }

                overhang = fillAfter(mFirstPosition + getChildCount(), allowOverhang) + mItemMargin;
                back = false;
            }

            movedBy = Math.min(overhang, allowOverhang);
            if (mIsVertical) {
                offsetChildren(0, back ? movedBy : -movedBy);
            } else {
                offsetChildren(back ? movedBy : -movedBy, 0);
            }

            recycleOffscreenViews();
            mPopulating = false;

            overScrolledBy = allowOverhang - overhang;
        } else {
            overScrolledBy = allowOverhang;
            movedBy = 0;
        }

        if (allowOverScroll) {
            final int overScrollMode = ViewCompat.getOverScrollMode(this);

            if (overScrollMode == ViewCompat.OVER_SCROLL_ALWAYS ||
                    (overScrollMode == ViewCompat.OVER_SCROLL_IF_CONTENT_SCROLLS && !contentFits)) {

                if (overScrolledBy > 0) {
                    EdgeEffectCompat edge = delta > 0 ? mStartEdge : mEndEdge;
                    edge.onPull((float) Math.abs(delta) / getHeight());
                    ViewCompat.postInvalidateOnAnimation(this);
                }
            }
        }

        return delta == 0 || movedBy != 0;
    }

    @TargetApi(14)
    private final float getCurrVelocity() {
        if (Build.VERSION.SDK_INT >= 14) {
            return mScroller.getCurrVelocity();
        }

        return 0;
    }

    private final boolean contentFits() {
        return (mIsVertical ? contentFitsVertical() : contentFitsHorizontal());
    }

    private final boolean contentFitsVertical() {
        if (mItemCount == 0) {
            if (DEBUG) {
                Log.d(LOGTAG, "Content fits (vertical): item count is zero, bailing");
            }

            return true;
        }

        if (mFirstPosition != 0 || getChildCount() != mItemCount) {
            if (DEBUG) {
                Log.d(LOGTAG, "Content fits (vertical): scrolled, bailing");
            }

            return false;
        }

        if (DEBUG) {
            Log.d(LOGTAG, "Content fits (vertical): mItemsStart: " + mItemsStart +
                    " mItemsEnd: " + mItemsEnd + " paddingTop: " + getPaddingTop() +
                    " height + paddingBottom: " + (getHeight() + getPaddingBottom()));
        }

        return (mItemsStart >= getPaddingTop() &&
                mItemsEnd <= getHeight() - getPaddingBottom());
    }

    private final boolean contentFitsHorizontal() {
        if (mItemCount == 0) {
            if (DEBUG) {
                Log.d(LOGTAG, "Content fits (horizontal): item count is zero, bailing");
            }

            return true;
        }

        if (mFirstPosition != 0 || getChildCount() != mItemCount) {
            if (DEBUG) {
                Log.d(LOGTAG, "Content fits (horizontal): scrolled, bailing");
            }

            return false;
        }

        if (DEBUG) {
            Log.d(LOGTAG, "Content fits (horizontal): mItemsStart: " + mItemsStart +
                    " mItemsEnd: " + mItemsEnd + " paddingLeft: " + getPaddingLeft() +
                    " height + paddingRight: " + (getWidth() + getPaddingRight()));
        }

        return (mItemsStart >= getPaddingLeft() &&
                mItemsEnd <= getWidth() - getPaddingRight());
    }

    private void recycleAllViews() {
        for (int i = 0; i < getChildCount(); i++) {
            mRecycler.addScrap(getChildAt(i));
        }

        if (mInLayout) {
            removeAllViewsInLayout();
        } else {
            removeAllViews();
        }
    }

    /**
     * Important: this method will leave offscreen views attached if they
     * are required to maintain the invariant that child view with index i
     * is always the view corresponding to position mFirstPosition + i.
     */
    private void recycleOffscreenViews() {
        final int size = (mIsVertical ? getHeight() : getWidth());
        final int clearBefore = -mItemMargin;
        final int clearAfter = size + mItemMargin;

        if (DEBUG) {
            Log.d(LOGTAG, "recycleOffscreenViews: clearBefore: " + clearBefore +
                    " clearAfter: " + clearAfter);
        }

        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final int childStart = (mIsVertical ? child.getTop() : child.getLeft());

            if (DEBUG) {
                Log.d(LOGTAG, "recycleOffscreenViews: remove from end?: " + i + " - " + childStart);
            }

            if (childStart <= clearAfter)  {
                // There may be other offscreen views, but we need to maintain
                // the invariant documented above.
                break;
            }

            if (mInLayout) {
                removeViewsInLayout(i, 1);
            } else {
                removeViewAt(i);
            }

            if (DEBUG) {
                Log.d(LOGTAG, "Moving view at position" + i + " to scrap");
            }

            mRecycler.addScrap(child);
        }

        while (getChildCount() > 0) {
            final View child = getChildAt(0);
            final int childEnd = (mIsVertical ? child.getBottom() : child.getRight());

            if (DEBUG) {
                Log.d(LOGTAG, "recycleOffscreenViews: remove from start?: " + childEnd);
            }

            if (childEnd >= clearBefore) {
                // There may be other offscreen views, but we need to maintain
                // the invariant documented above.
                break;
            }

            if (mInLayout) {
                removeViewsInLayout(0, 1);
            } else {
                removeViewAt(0);
            }

            if (DEBUG) {
                Log.d(LOGTAG, "Moving view at position" + 0 + " to scrap");
            }

            mRecycler.addScrap(child);
            mFirstPosition++;

            if (DEBUG) {
                Log.d(LOGTAG, "recycleOffscreenVIews: mFirstPosition: " + mFirstPosition);
            }
        }

        final int childCount = getChildCount();
        if (childCount > 0) {
            mItemsStart = Integer.MAX_VALUE;
            mItemsEnd = Integer.MIN_VALUE;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final int childStart = (mIsVertical ? child.getTop() : child.getLeft()) - mItemMargin;
                final int childEnd = (mIsVertical ? child.getBottom() : child.getRight());

                if (childStart < mItemsStart) {
                    mItemsStart = childStart;
                }

                if (childEnd > mItemsEnd) {
                    mItemsEnd = childEnd;
                }
            }

            if (mItemsStart == Integer.MAX_VALUE) {
                mItemsStart = 0;
                mItemsEnd = 0;
            }

            if (DEBUG) {
                Log.d(LOGTAG, "recycleOffscreenViews: mItemsEnd: " + mItemsEnd);
            }
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int pos = 0;
            if (mIsVertical) {
                pos = mScroller.getCurrY();
            } else {
                pos = mScroller.getCurrX();
            }

            final int diff = (int) (pos - mLastTouchPos);
            mLastTouchPos = pos;

            final boolean stopped = !trackMotionScroll(diff, false);

            if (!stopped && !mScroller.isFinished()) {
                ViewCompat.postInvalidateOnAnimation(this);
            } else {
                if (stopped) {
                    final int overScrollMode = ViewCompat.getOverScrollMode(this);
                    if (overScrollMode != ViewCompat.OVER_SCROLL_NEVER) {
                        final EdgeEffectCompat edge =
                                (diff > 0 ? mStartEdge : mEndEdge);

                        edge.onAbsorb(Math.abs((int) getCurrVelocity()));
                        ViewCompat.postInvalidateOnAnimation(this);
                    }

                    mScroller.abortAnimation();
                }

                mTouchMode = TOUCH_MODE_IDLE;
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mStartEdge != null) {
            boolean needsInvalidate = false;
            if (!mStartEdge.isFinished()) {
                mStartEdge.draw(canvas);
                needsInvalidate = true;
            }

            // FIXME: handle horizontal mode here
            if (!mEndEdge.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();

                canvas.translate(-width, getHeight());
                canvas.rotate(180, width, 0);
                mEndEdge.draw(canvas);
                canvas.restoreToCount(restoreCount);

                needsInvalidate = true;
            }

            if (needsInvalidate) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }
    }

    @Override
    public void requestLayout() {
        if (!mPopulating) {
            super.requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            Log.e(LOGTAG, "onMeasure: must have an exact width or match_parent! " +
                    "Using fallback spec of EXACTLY " + widthSize);
            widthMode = MeasureSpec.EXACTLY;
        }

        if (heightMode != MeasureSpec.EXACTLY) {
            Log.e(LOGTAG, "onMeasure: must have an exact height or match_parent! " +
                    "Using fallback spec of EXACTLY " + heightSize);
            heightMode = MeasureSpec.EXACTLY;
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout = true;
        populate();
        mInLayout = false;

        final int width = r - l;
        final int height = b - t;
        mStartEdge.setSize(width, height);
        mEndEdge.setSize(width, height);
    }

    private void populate() {
        if (DEBUG) {
            Log.d(LOGTAG, "Populating");
        }

        if (getWidth() == 0 || getHeight() == 0) {
            if (DEBUG) {
                Log.d(LOGTAG, "View has empty size, bailing");
            }

            return;
        }

        if (mItemsStart == null || mItemsEnd == null) {
            final int padding = (mIsVertical ? getPaddingTop() : getPaddingLeft());
            final int offset = padding + Math.min(mRestoreOffset, 0);

            mItemsStart = offset;
            mItemsEnd = offset;

            if (DEBUG) {
                Log.d(LOGTAG, "Removing all views to populate");
            }

            if (mInLayout) {
                removeAllViewsInLayout();
            } else {
                removeAllViews();
            }

            mRestoreOffset = 0;
        }

        mPopulating = true;
        layoutChildren(mDataChanged);
        fillAfter(mFirstPosition + getChildCount(), 0);
        fillBefore(mFirstPosition - 1, 0);
        mPopulating = false;
        mDataChanged = false;
    }

    final void offsetChildren(int offsetX, int offsetY) {
        final int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            child.layout(child.getLeft() + offsetX, child.getTop() + offsetY,
                    child.getRight() + offsetX, child.getBottom() + offsetY);
        }

        final int offset = (mIsVertical ? offsetY : offsetX);
        mItemsStart += offset;
        mItemsEnd += offset;
    }

    final int getChildWidthMeasureSpec(LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            final int maxWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            return MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }
    }

    final int getChildHeightMeasureSpec(LayoutParams lp) {
        if (mIsVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (!mIsVertical) {
            final int maxHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            return MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }
    }

    final void measureChild(View child, LayoutParams lp) {
        if (DEBUG) {
            Log.d(LOGTAG, "Measuring child view");
        }

        final int widthSpec = getChildWidthMeasureSpec(lp);
        final int heightSpec = getChildHeightMeasureSpec(lp);
        child.measure(widthSpec, heightSpec);
    }

    /**
     * Measure and layout all currently visible children.
     *
     * @param queryAdapter true to requery the adapter for view data
     */
    final void layoutChildren(boolean queryAdapter) {
        if (DEBUG) {
            Log.d(LOGTAG, "Laying out children");
        }

        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int itemMargin = mItemMargin;
        final int childCount = getChildCount();

        if (DEBUG) {
            Log.d(LOGTAG, "Number of child views: " + childCount);
        }

        mItemsEnd = Integer.MIN_VALUE;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int position = mFirstPosition + i;
            final boolean needsLayout = queryAdapter || child.isLayoutRequested();

            if (DEBUG) {
                Log.d(LOGTAG, "Child views needs layout: " + needsLayout);
            }

            if (queryAdapter) {
                if (DEBUG) {
                    Log.d(LOGTAG, "Obtaining view from adapter: " + position);
                }

                View newView = obtainView(position, child);
                if (newView != child) {
                    if (DEBUG) {
                        Log.d(LOGTAG, "Adapter returned new view, replacing");
                    }

                    removeViewAt(i);
                    addView(newView, i);
                    child = newView;
                }

                lp = (LayoutParams) child.getLayoutParams();
            }

            if (needsLayout) {
                measureChild(child, lp);
            }

            final int childStart = (mIsVertical ? child.getTop() : child.getLeft());
            final int childPos = (mItemsEnd > Integer.MIN_VALUE ?
                    mItemsEnd + itemMargin : childStart);

            if (DEBUG) {
                Log.d(LOGTAG, "Child start: " + childStart + " Child pos: " + childPos);
            }

            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            final int childTop = (mIsVertical ? childPos : paddingTop);
            final int childBottom = childTop + childHeight;
            final int childLeft = (mIsVertical ? paddingLeft : childPos);
            final int childRight = childLeft + childWidth;

            if (DEBUG) {
                Log.d(LOGTAG, "Child view layout: l=" + childLeft +
                        " t=" + childTop + " r=" + childRight + " b=" + childBottom);
            }

            child.layout(childLeft, childTop, childRight, childBottom);

            mItemsEnd = (mIsVertical ? childBottom : childRight);
            if (DEBUG) {
                Log.d(LOGTAG, "Layout children, current pos: " + mItemsEnd);
            }
        }

        if (mItemsEnd == Integer.MIN_VALUE) {
            mItemsEnd = mItemsStart;
        }

        if (DEBUG) {
            Log.d(LOGTAG, "layoutChildren: mItemsEnd: " + mItemsEnd);
        }
    }

    /**
     * Should be called with mPopulating set to true
     *
     * @param fromPosition Position to start filling from
     * @param overhang the number of extra pixels to fill beyond the current top edge
     * @return the max overhang beyond the beginning of the view of any added items at the top
     */
    final int fillBefore(int fromPosition, int overhang) {
        if (DEBUG) {
            Log.d(LOGTAG, "Filling before " + fromPosition + " with overhang " + overhang);
        }

        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int itemMargin = mItemMargin;
        final int start = (mIsVertical ? paddingTop : paddingLeft);
        final int fillTo = start - overhang;
        int position = fromPosition;

        while (position >= 0 && mItemsStart > fillTo) {
            if (DEBUG) {
                Log.d(LOGTAG, "Filling position: " + position);
            }

            final View child = obtainView(position, null);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getParent() != this) {
                if (DEBUG) {
                    Log.d(LOGTAG, "Adding new view to layout");
                }

                if (mInLayout) {
                    addViewInLayout(child, 0, lp);
                } else {
                    addView(child, 0);
                }
            }

            if (mHasStableIds) {
                final long id = mAdapter.getItemId(position);
                lp.id = id;
            }

            measureChild(child, lp);

            final int from = mItemsStart;

            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            final int childBottom;
            final int childTop;
            final int childLeft;
            final int childRight;

            if (mIsVertical) {
                childBottom = from;
                childTop = childBottom - childHeight;
                childLeft = paddingLeft;
                childRight = childLeft + childWidth;
            } else {
                childTop = paddingTop;
                childBottom = childTop + childHeight;
                childRight = from;
                childLeft = childRight - childWidth;
            }

            child.layout(childLeft, childTop, childRight, childBottom);

            if (DEBUG) {
                Log.d(LOGTAG, "fillBefore: child view layout: l=" + childLeft +
                        " t=" + childTop + " r=" + childRight + " b=" + childBottom);
            }

            mItemsStart = (mIsVertical ? childTop : childLeft) - itemMargin;
            mFirstPosition = position--;

            if (DEBUG) {
                Log.d(LOGTAG, "fillBefore: mFirstPosition: " + mFirstPosition);
            }
        }

        return start - mItemsStart;
    }

    /**
     * Should be called with mPopulating set to true
     *
     * @param fromPosition Position to start filling from
     * @param overhang the number of extra pixels to fill beyond the current bottom edge
     * @return the max overhang beyond the end of the view of any added items at the bottom
     */
    final int fillAfter(int fromPosition, int overhang) {
        if (DEBUG) {
            Log.d(LOGTAG, "Filling after " + fromPosition + " with overhang " + overhang);
        }

        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int itemMargin = mItemMargin;
        final int end =
                (mIsVertical ? getHeight() - getPaddingBottom() : getWidth() - getPaddingRight());
        final int fillTo = end + overhang;
        int position = fromPosition;

        if (DEBUG) {
            Log.d(LOGTAG, "Position: " + fromPosition + " itemCount:  " + mItemCount +
                    " fillTo: " + fillTo + " mItemsEnd: " + mItemsEnd);
        }

        while (position < mItemCount && mItemsEnd < fillTo) {
            if (DEBUG) {
                Log.d(LOGTAG, "Filling position: " + position);
            }

            final View child = obtainView(position, null);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getParent() != this) {
                if (DEBUG) {
                    Log.d(LOGTAG, "Adding new view to layout");
                }

                if (mInLayout) {
                    addViewInLayout(child, -1, lp);
                } else {
                    addView(child);
                }
            } else {
                if (DEBUG) {
                    Log.d(LOGTAG, "View already in layout, not adding");
                }
            }

            if (mHasStableIds) {
                final long id = mAdapter.getItemId(position);
                lp.id = id;
            }

            measureChild(child, lp);

            final int from = mItemsEnd;

            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            final int childBottom;
            final int childTop;
            final int childLeft;
            final int childRight;

            if (mIsVertical) {
                childTop = from + itemMargin;
                childBottom = childTop + childHeight;
                childLeft = paddingLeft;
                childRight = childLeft + childWidth;
            } else {
                childTop = paddingTop;
                childBottom = childTop + childHeight;
                childLeft = from + itemMargin;
                childRight = childLeft + childWidth;
            }

            child.layout(childLeft, childTop, childRight, childBottom);

            if (DEBUG) {
                Log.d(LOGTAG, "fillAfter: child view layout: l=" + childLeft +
                        " t=" + childTop + " r=" + childRight + " b=" + childBottom +
                        " mItemsEnd: " + mItemsEnd + " childWidth: " + childWidth);
            }

            mItemsEnd = (mIsVertical ? childBottom : childRight);
            position++;
        }

        return mItemsEnd - end;
    }

    /**
     * Obtain a populated view from the adapter. If optScrap is non-null and is not
     * reused it will be placed in the recycle bin.
     *
     * @param position position to get view for
     * @param optScrap Optional scrap view; will be reused if possible
     * @return A new view, a recycled view from mRecycler, or optScrap
     */
    final View obtainView(int position, View optScrap) {
        View view = mRecycler.getTransientStateView(position);
        if (view != null) {
            return view;
        }

        // Reuse optScrap if it's of the right type (and not null)
        final int optType = (optScrap != null ?
                ((LayoutParams) optScrap.getLayoutParams()).viewType : -1);

        final int positionViewType = mAdapter.getItemViewType(position);

        final View scrap = (optType == positionViewType ?
                optScrap : mRecycler.getScrapView(positionViewType));

        view = mAdapter.getView(position, scrap, this);

        if (view != scrap && scrap != null) {
            // The adapter didn't use it; put it back.
            mRecycler.addScrap(scrap);
        }

        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (view.getParent() != this) {
            if (lp == null) {
                lp = generateDefaultLayoutParams();
            } else if (!checkLayoutParams(lp)) {
                lp = generateLayoutParams(lp);
            }
        }

        final LayoutParams twlp = (LayoutParams) lp;
        twlp.position = position;
        twlp.viewType = positionViewType;

        return view;
    }

    /**
     * Clear all state because the grid will be used for a completely different set of data.
     */
    private void clearAllState() {
        if (DEBUG) {
            Log.d(LOGTAG, "Clearning all state");
        }

        // Clear all layout records and views
        removeAllViews();

        // Reset to the top of the grid
        resetStateForListStart();

        // Clear recycler because there could be different view types now
        mRecycler.clear();

        if (DEBUG) {
            Log.d(LOGTAG, "State cleared");
        }
    }

    /**
     * Reset all internal state to be at the top of the grid.
     */
    private void resetStateForListStart() {
        final int padding = (mIsVertical ? getPaddingTop() : getPaddingLeft());
        mItemsStart = padding;
        mItemsEnd = padding;

        mFirstPosition = 0;
        mRestoreOffset = 0;
    }

    /**
     * Scroll the list so the first visible position in the grid is the first item in the adapter.
     */
    public void setSelectionToTop() {
        // Clear out the views (but don't clear out the layout records
        // or recycler because the data has not changed)
        removeAllViews();

        // Reset to top of grid
        resetStateForListStart();

        // Start populating again
        populate();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new LayoutParams(lp);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState ss = new SavedState(superState);
        final int position = mFirstPosition;

        ss.position = position;

        if (getChildCount() > 0) {
            if (mIsVertical) {
                ss.offset = getChildAt(0).getTop() - mItemMargin - getPaddingTop();
            } else {
                ss.offset = getChildAt(0).getLeft() - mItemMargin - getPaddingLeft();
            }
        }

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mDataChanged = true;
        mFirstPosition = ss.position;
        mRestoreOffset = ss.offset;

        requestLayout();
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        /**
         * Item position this view represents
         */
        int position;

        /**
         * Type of this view as reported by the adapter
         */
        int viewType;

        /**
         * The stable ID of the item this view displays
         */
        long id = -1;

        public LayoutParams(int width, int height) {
            super(width, height);

            if (this.height == MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with height FILL_PARENT - " +
                        "impossible! Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            if (this.width != MATCH_PARENT) {
                Log.w(LOGTAG, "Inflation setting LayoutParams width to " + this.width +
                        " - must be MATCH_PARENT");
                this.width = MATCH_PARENT;
            }

            if (this.height == MATCH_PARENT) {
                Log.w(LOGTAG, "Inflation setting LayoutParams height to MATCH_PARENT - " +
                        "impossible! Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);

            if (this.width != MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with width " + this.width +
                        " - must be MATCH_PARENT");
                this.width = MATCH_PARENT;
            }

            if (this.height == MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with height MATCH_PARENT - " +
                        "impossible! Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }
    }

    private class RecycleBin {
        private ArrayList<View>[] mScrapViews;
        private int mViewTypeCount;
        private int mMaxScrap;

        private SparseArray<View> mTransientStateViews;

        public void setViewTypeCount(int viewTypeCount) {
            if (viewTypeCount < 1) {
                throw new IllegalArgumentException("Must have at least one view type (" +
                        viewTypeCount + " types reported)");
            }

            if (viewTypeCount == mViewTypeCount) {
                return;
            }

            @SuppressWarnings("unchecked")
            ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];
            for (int i = 0; i < viewTypeCount; i++) {
                scrapViews[i] = new ArrayList<View>();
            }

            mViewTypeCount = viewTypeCount;
            mScrapViews = scrapViews;
        }

        public void clear() {
            final int typeCount = mViewTypeCount;
            for (int i = 0; i < typeCount; i++) {
                mScrapViews[i].clear();
            }

            if (mTransientStateViews != null) {
                mTransientStateViews.clear();
            }
        }

        public void clearTransientViews() {
            if (mTransientStateViews != null) {
                mTransientStateViews.clear();
            }
        }

        public void addScrap(View v) {
            final LayoutParams lp = (LayoutParams) v.getLayoutParams();
            if (ViewCompat.hasTransientState(v)) {
                if (mTransientStateViews == null) {
                    mTransientStateViews = new SparseArray<View>();
                }

                mTransientStateViews.put(lp.position, v);
                return;
            }

            final int childCount = getChildCount();
            if (childCount > mMaxScrap) {
                mMaxScrap = childCount;
            }

            ArrayList<View> scrap = mScrapViews[lp.viewType];
            if (scrap.size() < mMaxScrap) {
                scrap.add(v);
            }
        }

        public View getTransientStateView(int position) {
            if (mTransientStateViews == null) {
                return null;
            }

            final View result = mTransientStateViews.get(position);
            if (result != null) {
                mTransientStateViews.remove(position);
            }

            return result;
        }

        public View getScrapView(int type) {
            ArrayList<View> scrap = mScrapViews[type];
            if (scrap.isEmpty()) {
                return null;
            }

            final int index = scrap.size() - 1;
            final View result = scrap.get(index);
            scrap.remove(index);

            return result;
        }
    }

    private class AdapterDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            if (DEBUG) {
                Log.d(LOGTAG, "Adapter notified change");
            }

            mDataChanged = true;
            mItemCount = mAdapter.getCount();

            if (DEBUG) {
                Log.d(LOGTAG, "New item count on adapter is: " + mItemCount);
            }

            // TODO: Consider matching these back up if we have stable IDs.
            mRecycler.clearTransientViews();

            if (!mHasStableIds) {
                if (DEBUG) {
                    Log.d(LOGTAG, "No stable ids, recycling all views");
                }

                // Clear all layout records and recycle the views
                recycleAllViews();

                // Reset items end position to be equal to start
                mItemsEnd = mItemsStart;

                if (DEBUG) {
                    Log.d(LOGTAG, "adapter.onChange: mItemsEnd: " + mItemsEnd);
                }
            }

            if (DEBUG) {
                Log.d(LOGTAG, "Relayout requested because of adapter changes");
            }

            // TODO: consider re-populating in a deferred runnable instead
            // (so that successive changes may still be batched)
            requestLayout();
        }

        @Override
        public void onInvalidated() {
        }
    }

    static class SavedState extends BaseSavedState {
        int position;
        int offset;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            position = in.readInt();
            offset = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(position);
            out.writeInt(offset);
        }

        @Override
        public String toString() {
            return "TwoWayView.SavedState{"
            + Integer.toHexString(System.identityHashCode(this))
            + " position=" + position + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
