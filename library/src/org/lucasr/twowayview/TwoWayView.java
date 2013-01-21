/*
 * Copyright (C) 2013 Lucas Rocha
 *
 * This code is based on bits and pieces of Android's AbsListView,
 * Listview, and StaggeredGridView.
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
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Rect;
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
import android.view.ContextMenu.ContextMenuInfo;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.Scroller;

public class TwoWayView extends AdapterView<ListAdapter> {
    private static final String LOGTAG = "TwoWayView";
    private static final boolean DEBUG = true;

    private static final int TOUCH_MODE_REST = -1;
    private static final int TOUCH_MODE_DOWN = 0;
    private static final int TOUCH_MODE_TAP = 1;
    private static final int TOUCH_MODE_DONE_WAITING = 2;
    private static final int TOUCH_MODE_DRAGGING = 3;
    private static final int TOUCH_MODE_FLINGING = 4;

    private ListAdapter mAdapter;

    public static enum ScrollDirection {
        VERTICAL,
        HORIZONTAL;
    };

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

    private Rect mTouchFrame;
    private int mMotionPosition;
    private CheckForTap mPendingCheckForTap;
    private CheckForLongPress mPendingCheckForLongPress;
    private PerformClick mPerformClick;
    private Runnable mTouchModeReset;

    private ContextMenuInfo mContextMenuInfo;

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

        mTouchMode = TOUCH_MODE_REST;

        mContextMenuInfo = null;

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

        TypedArray a = context.getTheme().obtainStyledAttributes(R.styleable.TwoWayView);
        initializeScrollbars(a);
        a.recycle();

        updateScrollbarsDirection();
    }

    public void setScrollDirection(ScrollDirection scrollDirection) {
        final boolean isVertical = (scrollDirection.compareTo(ScrollDirection.VERTICAL) == 0);
        if (mIsVertical == isVertical) {
            return;
        }

        mIsVertical = isVertical;

        updateScrollbarsDirection();
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

    @Override
    public ListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }

        // TODO: If the new adapter says that there are stable IDs,
        // remove certain layout records and onscreen views if they
        // have changed instead of removing all of the state here.
        clearAllState();

        mAdapter = adapter;
        mDataChanged = true;

        if (mAdapter != null) {
            mItemCount = adapter.getCount();
            mAdapter.registerDataSetObserver(mObserver);
            mRecycler.setViewTypeCount(adapter.getViewTypeCount());
            mHasStableIds = adapter.hasStableIds();
        } else {
            mItemCount = 0;
            mHasStableIds = false;
        }

        populate();
    }

    @Override
    public int getFirstVisiblePosition() {
        return mFirstPosition;
    }

    @Override
    public int getLastVisiblePosition() {
        return mFirstPosition + getChildCount() - 1;
    }

    @Override
    public int getPositionForView(View view) {
        View child = view;
        try {
            View v;
            while (!(v = (View) child.getParent()).equals(this)) {
                child = v;
            }
        } catch (ClassCastException e) {
            // We made it up to the window without find this list view
            return INVALID_POSITION;
        }

        // Search the children for the list item
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i).equals(child)) {
                return mFirstPosition + i;
            }
        }

        // Child not found!
        return INVALID_POSITION;
    }

    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }

        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);

            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);

                if (frame.contains(x, y)) {
                    return mFirstPosition + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    @Override
    protected int computeVerticalScrollExtent() {
        final int count = getChildCount();
        if (count == 0) {
            return 0;
        }

        int extent = count * 100;

        View child = getChildAt(0);
        final int childTop = child.getTop();

        int childHeight = child.getHeight();
        if (childHeight > 0) {
            extent += (childTop * 100) / childHeight;
        }

        child = getChildAt(count - 1);
        final int childBottom = child.getBottom();

        childHeight = child.getHeight();
        if (childHeight > 0) {
            extent -= ((childBottom - getHeight()) * 100) / childHeight;
        }

        return extent;
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        final int count = getChildCount();
        if (count == 0) {
            return 0;
        }

        int extent = count * 100;

        View child = getChildAt(0);
        final int childLeft = child.getLeft();

        int childWidth = child.getWidth();
        if (childWidth > 0) {
            extent += (childLeft * 100) / childWidth;
        }

        child = getChildAt(count - 1);
        final int childRight = child.getRight();

        childWidth = child.getWidth();
        if (childWidth > 0) {
            extent -= ((childRight - getWidth()) * 100) / childWidth;
        }

        return extent;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        final int firstPosition = mFirstPosition;
        final int childCount = getChildCount();

        if (firstPosition < 0 || childCount == 0) {
            return 0;
        }

        final View child = getChildAt(0);
        final int childTop = child.getTop();

        int childHeight = child.getHeight();
        if (childHeight > 0) {
            return Math.max(firstPosition * 100 - (childTop * 100) / childHeight, 0);
        }

        return 0;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        final int firstPosition = mFirstPosition;
        final int childCount = getChildCount();

        if (firstPosition < 0 || childCount == 0) {
            return 0;
        }

        final View child = getChildAt(0);
        final int childLeft = child.getLeft();

        int childWidth = child.getWidth();
        if (childWidth > 0) {
            return Math.max(firstPosition * 100 - (childLeft * 100) / childWidth, 0);
        }

        return 0;
    }

    @Override
    protected int computeVerticalScrollRange() {
        return Math.max(mItemCount * 100, 0);
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return Math.max(mItemCount * 100, 0);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        final int longPressPosition = getPositionForView(originalView);
        if (longPressPosition >= 0) {
            final long longPressId = mAdapter.getItemId(longPressPosition);
            boolean handled = false;

            OnItemLongClickListener listener = getOnItemLongClickListener();
            if (listener != null) {
                handled = listener.onItemLongClick(TwoWayView.this, originalView,
                        longPressPosition, longPressId);
            }

            if (!handled) {
                mContextMenuInfo = createContextMenuInfo(
                        getChildAt(longPressPosition - mFirstPosition),
                        longPressPosition, longPressId);

                handled = super.showContextMenuForChild(originalView);
            }

            return handled;
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mVelocityTracker.clear();
            mScroller.abortAnimation();

            final float x = ev.getX();
            final float y = ev.getY();

            mLastTouchPos = (mIsVertical ? y : x);
            final int motionPosition = pointToPosition((int) x, (int) y);

            mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
            mTouchRemainderPos = 0;

            if (mTouchMode == TOUCH_MODE_FLINGING) {
                return true;
            } else if (motionPosition >= 0) {
                mMotionPosition = motionPosition;
                mTouchMode = TOUCH_MODE_DOWN;
            }

            break;

        case MotionEvent.ACTION_MOVE: {
            if (mTouchMode != TOUCH_MODE_DOWN) {
                break;
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

            final float diff = pos - mLastTouchPos + mTouchRemainderPos;
            final int delta = (int) diff;
            mTouchRemainderPos = diff - delta;

            if (Math.abs(diff) > mTouchSlop) {
                mTouchMode = TOUCH_MODE_DRAGGING;

                setPressed(false);
                View motionView = getChildAt(mMotionPosition - mFirstPosition);
                if (motionView != null) {
                    motionView.setPressed(false);
                }

                return true;
            }
        }

        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            mTouchMode = TOUCH_MODE_REST;
            break;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return isClickable() || isLongClickable();
        }

        boolean needsInvalidate = false;

        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
        case MotionEvent.ACTION_DOWN: {
            if (mDataChanged) {
                break;
            }

            mVelocityTracker.clear();
            mScroller.abortAnimation();

            final float x = ev.getX();
            final float y = ev.getY();

            mLastTouchPos = (mIsVertical ? y : x);
            mMotionPosition = pointToPosition((int) x, (int) y);

            mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
            mTouchRemainderPos = 0;

            if (mTouchMode == TOUCH_MODE_FLINGING) {
                mTouchMode = TOUCH_MODE_DRAGGING;
                return true;
            } else if (mMotionPosition >= 0 && mAdapter.isEnabled(mMotionPosition)) {
                mTouchMode = TOUCH_MODE_DOWN;
                triggerCheckForTap();
            }

            break;
        }

        case MotionEvent.ACTION_MOVE: {
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

            final float diff = pos - mLastTouchPos + mTouchRemainderPos;
            final int delta = (int) diff;
            mTouchRemainderPos = diff - delta;

            boolean canStartDragging =
                    (mTouchMode == TOUCH_MODE_DOWN ||
                     mTouchMode == TOUCH_MODE_TAP ||
                     mTouchMode == TOUCH_MODE_DONE_WAITING);

            if (canStartDragging && Math.abs(diff) > mTouchSlop) {
                mTouchMode = TOUCH_MODE_DRAGGING;
            }

            if (mTouchMode == TOUCH_MODE_DRAGGING) {
                mLastTouchPos = pos;

                if (!trackMotionScroll(delta, true)) {
                    // Break fling velocity if we impacted an edge
                    mVelocityTracker.clear();
                }
            }

            break;
        }

        case MotionEvent.ACTION_CANCEL:
            cancelCheckForTap();
            mTouchMode = TOUCH_MODE_REST;

            setPressed(false);
            View motionView = this.getChildAt(mMotionPosition - mFirstPosition);
            if (motionView != null) {
                motionView.setPressed(false);
            }

            needsInvalidate =
                    mStartEdge.onRelease() | mEndEdge.onRelease();

            break;

        case MotionEvent.ACTION_UP: {
            switch (mTouchMode) {
            case TOUCH_MODE_DOWN:
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING: {
                final int motionPosition = mMotionPosition;
                final View child = getChildAt(motionPosition - mFirstPosition);

                final float x = ev.getX();
                final float y = ev.getY();

                boolean inList = false;
                if (mIsVertical) {
                    inList = x > getPaddingLeft() && x < getWidth() - getPaddingRight();
                } else {
                    inList = y > getPaddingTop() && y < getHeight() - getPaddingBottom();
                }

                if (child != null && !child.hasFocusable() && inList) {
                    if (mTouchMode != TOUCH_MODE_DOWN) {
                        child.setPressed(false);
                    }

                    if (mPerformClick == null) {
                        mPerformClick = new PerformClick();
                    }

                    final PerformClick performClick = mPerformClick;
                    performClick.mClickMotionPosition = motionPosition;
                    performClick.rememberWindowAttachCount();

                    if (mTouchMode == TOUCH_MODE_DOWN || mTouchMode == TOUCH_MODE_TAP) {
                        if (mTouchMode == TOUCH_MODE_DOWN) {
                            cancelCheckForTap();
                        } else {
                            cancelCheckForLongPress();
                        }

                        if (!mDataChanged && mAdapter.isEnabled(motionPosition)) {
                            mTouchMode = TOUCH_MODE_TAP;

                            setPressed(true);
                            child.setPressed(true);

                            if (mTouchModeReset != null) {
                                removeCallbacks(mTouchModeReset);
                            }

                            mTouchModeReset = new Runnable() {
                                @Override
                                public void run() {
                                    mTouchMode = TOUCH_MODE_REST;

                                    setPressed(false);
                                    child.setPressed(false);

                                    if (!mDataChanged) {
                                        performClick.run();
                                    }

                                    mTouchModeReset = null;
                                }
                            };

                            postDelayed(mTouchModeReset,
                                    ViewConfiguration.getPressedStateDuration());
                        } else {
                            mTouchMode = TOUCH_MODE_REST;
                        }
                    }
                }

                mTouchMode = TOUCH_MODE_REST;
                break;
            }

            case TOUCH_MODE_DRAGGING:
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

                    mLastTouchPos = 0;
                    needsInvalidate = true;
                } else {
                    mTouchMode = TOUCH_MODE_REST;
                }

                break;
            }

            cancelCheckForTap();
            setPressed(false);

            needsInvalidate |=
                    mStartEdge.onRelease() | mEndEdge.onRelease();

            break;
        }
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }

        return true;
    }

    private void updateScrollbarsDirection() {
        setHorizontalScrollBarEnabled(!mIsVertical);
        setVerticalScrollBarEnabled(mIsVertical);
    }

    private void triggerCheckForTap() {
        if (mPendingCheckForTap == null) {
            mPendingCheckForTap = new CheckForTap();
        }

        postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
    }

    private void cancelCheckForTap() {
        if (mPendingCheckForTap == null) {
            return;
        }

        removeCallbacks(mPendingCheckForTap);
    }

    private void triggerCheckForLongPress() {
        if (mPendingCheckForLongPress == null) {
            mPendingCheckForLongPress = new CheckForLongPress();
        }

        mPendingCheckForLongPress.rememberWindowAttachCount();

        postDelayed(mPendingCheckForLongPress,
                ViewConfiguration.getLongPressTimeout());
    }

    private void cancelCheckForLongPress() {
        if (mPendingCheckForLongPress == null) {
            return;
        }

        removeCallbacks(mPendingCheckForLongPress);
    }

    private boolean trackMotionScroll(int delta, boolean allowOverScroll) {
        final boolean contentFits = contentFits();
        final int allowOverhang = Math.abs(delta);

        final int overScrolledBy;
        final int movedBy;

        if (!contentFits) {
            final int overhang;
            final boolean back;

            mPopulating = true;

            if (delta > 0) {
                overhang = fillBefore(mFirstPosition - 1, allowOverhang);
                back = true;
            } else {
                overhang = fillAfter(mFirstPosition + getChildCount(), allowOverhang) + mItemMargin;
                back = false;
            }

            if (!awakenScrollbarsInternal()) {
               invalidate();
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
                    final EdgeEffectCompat edge = delta > 0 ? mStartEdge : mEndEdge;
                    final int size = (mIsVertical ? getHeight() : getWidth());

                    boolean needsInvalidate =
                            edge.onPull((float) Math.abs(delta) / size);

                    if (needsInvalidate) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
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

    @TargetApi(5)
    protected boolean awakenScrollbarsInternal() {
        if (Build.VERSION.SDK_INT >= 5) {
            return super.awakenScrollBars();
        } else {
            return false;
        }
    }

    private final boolean contentFits() {
        if (mItemCount == 0) {
            return true;
        }

        if (mFirstPosition != 0 || getChildCount() != mItemCount) {
            return false;
        }

        if (mIsVertical) {
            return (mItemsStart >= getPaddingTop() &&
                    mItemsEnd <= getHeight() - getPaddingBottom());
        } else {
            return (mItemsStart >= getPaddingLeft() &&
                    mItemsEnd <= getWidth() - getPaddingRight());
        }
    }

    private void recycleAllViews() {
        for (int i = 0; i < getChildCount(); i++) {
            mRecycler.addScrap(getChildAt(i));
        }

        detachAllViewsFromParent();
    }

    private void recycleOffscreenViews() {
        final int size = (mIsVertical ? getHeight() : getWidth());
        final int clearBefore = -mItemMargin;
        final int clearAfter = size + mItemMargin;

        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final int childStart = (mIsVertical ? child.getTop() : child.getLeft());

            if (childStart <= clearAfter)  {
                break;
            }

            detachViewFromParent(i);

            mRecycler.addScrap(child);
        }

        while (getChildCount() > 0) {
            final View child = getChildAt(0);
            final int childEnd = (mIsVertical ? child.getBottom() : child.getRight());

            if (childEnd >= clearBefore) {
                break;
            }

            detachViewFromParent(0);

            mRecycler.addScrap(child);
            mFirstPosition++;
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

                        boolean needsInvalidate =
                                edge.onAbsorb(Math.abs((int) getCurrVelocity()));

                        if (needsInvalidate) {
                            ViewCompat.postInvalidateOnAnimation(this);
                        }
                    }

                    mScroller.abortAnimation();
                }

                mTouchMode = TOUCH_MODE_REST;
            }
        }
    }

    private boolean drawStartEdge(Canvas canvas) {
        if (mStartEdge.isFinished()) {
            return false;
        }

        if (mIsVertical) {
            return mStartEdge.draw(canvas);
        }

        final int restoreCount = canvas.save();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();

        canvas.translate(0, height);
        canvas.rotate(270);

        final boolean needsInvalidate = mStartEdge.draw(canvas);
        canvas.restoreToCount(restoreCount);
        return needsInvalidate;
    }

    private boolean drawEndEdge(Canvas canvas) {
        if (mEndEdge.isFinished()) {
            return false;
        }

        final int restoreCount = canvas.save();
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();

        if (mIsVertical) {
            canvas.translate(-width, height);
            canvas.rotate(180, width, 0);
        } else {
            canvas.translate(width, 0);
            canvas.rotate(90);
        }

        final boolean needsInvalidate = mEndEdge.draw(canvas);
        canvas.restoreToCount(restoreCount);
        return needsInvalidate;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        boolean needsInvalidate = false;

        if (mStartEdge != null) {
            needsInvalidate |= drawStartEdge(canvas);
        }

        if (mEndEdge != null) {
            needsInvalidate |= drawEndEdge(canvas);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void requestLayout() {
        if (!mPopulating) {
            super.requestLayout();
        }
    }

    @Override
    public View getSelectedView() {
        // TODO Do nothing for now
        return null;
    }

    @Override
    public void setSelection(int position) {
        // TODO Do nothing for now
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // Don't dispatch setPressed to our children. We call setPressed on ourselves to
        // get the selector in the right state, but we don't want to press each child.
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

        final int width = r - l - getPaddingLeft() - getPaddingRight();
        final int height = b - t - getPaddingTop() - getPaddingBottom();

        if (mIsVertical) {
            mStartEdge.setSize(width, height);
            mEndEdge.setSize(width, height);
        } else {
            mStartEdge.setSize(height, width);
            mEndEdge.setSize(height, width);
        }
    }

    private void populate() {
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        if (mItemsStart == null || mItemsEnd == null) {
            final int padding = (mIsVertical ? getPaddingTop() : getPaddingLeft());
            final int offset = padding + Math.min(mRestoreOffset, 0);

            mItemsStart = offset;
            mItemsEnd = offset;

            removeAllViewsInternal();
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
        final int widthSpec = getChildWidthMeasureSpec(lp);
        final int heightSpec = getChildHeightMeasureSpec(lp);
        child.measure(widthSpec, heightSpec);
    }

    final void layoutChildren(boolean queryAdapter) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int itemMargin = mItemMargin;
        final int childCount = getChildCount();

        mItemsEnd = Integer.MIN_VALUE;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int position = mFirstPosition + i;
            final boolean needsLayout = queryAdapter || child.isLayoutRequested();

            if (queryAdapter) {
                View newView = obtainView(position, child);
                if (newView != child) {
                    detachViewFromParent(i);
                    removeDetachedView(child, false);

                    addViewInternal(newView, i);
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
        }

        if (mItemsEnd == Integer.MIN_VALUE) {
            mItemsEnd = mItemsStart;
        }
    }

    final int fillBefore(int fromPosition, int overhang) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int itemMargin = mItemMargin;
        final int start = (mIsVertical ? paddingTop : paddingLeft);
        final int fillTo = start - overhang;
        int position = fromPosition;

        while (position >= 0 && mItemsStart > fillTo) {
            final View child = obtainView(position, null);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getParent() != this) {
                if (mInLayout) {
                    addViewInternal(child, 0, lp);
                } else {
                    addViewInternal(child, 0);
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
        }

        return start - mItemsStart;
    }

    final int fillAfter(int fromPosition, int overhang) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int itemMargin = mItemMargin;
        final int end =
                (mIsVertical ? getHeight() - getPaddingBottom() : getWidth() - getPaddingRight());
        final int fillTo = end + overhang;
        int position = fromPosition;

        while (position < mItemCount && mItemsEnd < fillTo) {
            final View child = obtainView(position, null);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getParent() != this) {
                if (mInLayout) {
                    addViewInternal(child, -1, lp);
                } else {
                    addViewInternal(child);
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

    private void addViewInternal(View child) {
        addViewInternal(child, -1);
    }

    private void addViewInternal(View child, int position) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp == null) {
            lp= generateDefaultLayoutParams();
        }

        addViewInternal(child, position, lp);
    }

    private void addViewInternal(View child, int position, LayoutParams lp) {
        if (!mInLayout) {
            requestLayout();
            invalidate();
        }

        addViewInLayout(child, position, lp);
    }

    private void removeAllViewsInternal() {
        removeAllViewsInLayout();

        if (!mInLayout) {
            requestLayout();
            invalidate();
        }
    }

    private void clearAllState() {
        // Clear all layout records and views
        removeAllViewsInternal();

        // Reset to the top of the grid
        resetStateForListStart();

        // Clear recycler because there could be different view types now
        mRecycler.clear();
    }

    private void resetStateForListStart() {
        final int padding = (mIsVertical ? getPaddingTop() : getPaddingLeft());
        mItemsStart = padding;
        mItemsEnd = padding;

        mFirstPosition = 0;
        mRestoreOffset = 0;
    }

    private ContextMenuInfo createContextMenuInfo(View view, int position, long id) {
        return new AdapterContextMenuInfo(view, position, id);
    }

    private boolean performLongPress(final View child,
            final int longPressPosition, final long longPressId) {
        // CHOICE_MODE_MULTIPLE_MODAL takes over long press.
        boolean handled = false;

        OnItemLongClickListener listener = getOnItemLongClickListener();
        if (listener != null) {
            handled = listener.onItemLongClick(TwoWayView.this, child,
                    longPressPosition, longPressId);
        }

        if (!handled) {
            mContextMenuInfo = createContextMenuInfo(child, longPressPosition, longPressId);
            handled = super.showContextMenuForChild(TwoWayView.this);
        }

        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }

        return handled;
    }

    public void setSelectionToTop() {
        // Clear out the views (but don't clear out the layout records
        // or recycler because the data has not changed)
        removeAllViewsInternal();

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
    protected ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
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

            if (this.width == MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with width FILL_PARENT " +
                        "does not make much sense as the view might change orientation. " +
                        "Falling back to WRAP_CONTENT");
                this.width = WRAP_CONTENT;
            }

            if (this.height == MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with height FILL_PARENT " +
                        "does not make much sense as the view might change orientation. " +
                        "Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            if (this.width == MATCH_PARENT) {
                Log.w(LOGTAG, "Inflation setting LayoutParams width to MATCH_PARENT - " +
                        "does not make much sense as the view might change orientation. " +
                        "Falling back to WRAP_CONTENT");
                this.width = MATCH_PARENT;
            }

            if (this.height == MATCH_PARENT) {
                Log.w(LOGTAG, "Inflation setting LayoutParams height to MATCH_PARENT - " +
                        "does not make much sense as the view might change orientation. " +
                        "Falling back to WRAP_CONTENT");
                this.height = WRAP_CONTENT;
            }
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);

            if (this.width == MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with height MATCH_PARENT - " +
                        "does not make much sense as the view might change orientation. " +
                        "Falling back to WRAP_CONTENT");
                this.width = WRAP_CONTENT;
            }

            if (this.height == MATCH_PARENT) {
                Log.w(LOGTAG, "Constructing LayoutParams with height MATCH_PARENT - " +
                        "does not make much sense as the view might change orientation. " +
                        "Falling back to WRAP_CONTENT");
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
                final ArrayList<View> scraps = mScrapViews[i];

                final int scrapCount = scraps.size();
                for (int j = 0; j < scrapCount; j++) {
                    removeDetachedView(scraps.get(j), false);
                }

                scraps.clear();
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
            ArrayList<View> scraps = mScrapViews[type];
            if (scraps.isEmpty()) {
                return null;
            }

            final int index = scraps.size() - 1;
            final View result = scraps.get(index);
            scraps.remove(index);

            return result;
        }
    }

    private class AdapterDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            mDataChanged = true;
            mItemCount = mAdapter.getCount();

            // TODO: Consider matching these back up if we have stable IDs.
            mRecycler.clearTransientViews();

            if (!mHasStableIds) {
                // Clear all layout records and recycle the views
                recycleAllViews();

                // Reset items end position to be equal to start
                mItemsEnd = mItemsStart;
            }

            awakenScrollbarsInternal();

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

    private class WindowRunnnable {
        private int mOriginalAttachCount;

        public void rememberWindowAttachCount() {
            mOriginalAttachCount = getWindowAttachCount();
        }

        public boolean sameWindow() {
            return hasWindowFocus() && getWindowAttachCount() == mOriginalAttachCount;
        }
    }

    private class PerformClick extends WindowRunnnable implements Runnable {
        int mClickMotionPosition;

        @Override
        public void run() {
            if (mDataChanged) {
                return;
            }

            final ListAdapter adapter = mAdapter;
            final int motionPosition = mClickMotionPosition;

            if (adapter != null && mItemCount > 0 &&
                motionPosition != INVALID_POSITION &&
                motionPosition < adapter.getCount() && sameWindow()) {

                final View child = getChildAt(motionPosition - mFirstPosition);
                if (child != null) {
                    performItemClick(child, motionPosition, adapter.getItemId(motionPosition));
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

            final View child = getChildAt(mMotionPosition - mFirstPosition);
            if (child != null && !child.hasFocusable()) {
                if (!mDataChanged) {
                    setPressed(true);
                    child.setPressed(true);

                    layoutChildren(false);
                    refreshDrawableState();

                    if (isLongClickable()) {
                        triggerCheckForLongPress();
                    } else {
                        mTouchMode = TOUCH_MODE_DONE_WAITING;
                    }
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }

    private class CheckForLongPress extends WindowRunnnable implements Runnable {
        @Override
        public void run() {
            final int motionPosition = mMotionPosition;
            final View child = getChildAt(motionPosition - mFirstPosition);

            if (child != null) {
                final long longPressId = mAdapter.getItemId(mMotionPosition);

                boolean handled = false;
                if (sameWindow() && !mDataChanged) {
                    handled = performLongPress(child, motionPosition, longPressId);
                }

                if (handled) {
                    mTouchMode = TOUCH_MODE_REST;
                    setPressed(false);
                    child.setPressed(false);
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }
}
