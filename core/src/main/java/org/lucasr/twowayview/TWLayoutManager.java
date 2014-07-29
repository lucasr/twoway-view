/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.twowayview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.RecyclerView.LayoutParams;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.ViewGroup.MarginLayoutParams;

import java.util.List;

public abstract class TWLayoutManager extends LayoutManager {
    private static final String LOGTAG = "TWLayoutManager";

    public static enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    public static enum Direction {
        START,
        END
    }

    private int mFirstPosition;
    private int mFirstVisiblePosition;

    private boolean mIsVertical = true;

    private SavedState mPendingSavedState = null;

    private int mPendingScrollPosition = RecyclerView.NO_POSITION;
    private int mPendingScrollOffset = 0;

    private final Rect mTempRect = new Rect();

    public TWLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.TWLayoutManager, defStyle, 0);

        final int indexCount = a.getIndexCount();
        for (int i = 0; i < indexCount; i++) {
            final int attr = a.getIndex(i);

            if (attr == R.styleable.TWLayoutManager_android_orientation) {
                final int orientation = a.getInt(attr, -1);
                if (orientation >= 0) {
                    setOrientation(Orientation.values()[orientation]);
                }
            }
        }

        a.recycle();
    }

    public TWLayoutManager(Context context, Orientation orientation) {
        mIsVertical = (orientation == Orientation.VERTICAL);
    }

    private int getTotalSpace() {
        if (mIsVertical) {
            return getHeight() - getPaddingBottom() - getPaddingTop();
        } else {
            return getWidth() - getPaddingRight() - getPaddingLeft();
        }
    }

    private int getChildStart(View child) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        if (mIsVertical) {
            return getDecoratedTop(child) - lp.topMargin;
        } else {
            return getDecoratedLeft(child) - lp.leftMargin;
        }
    }

    private int getChildEnd(View child) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        if (mIsVertical) {
            return getDecoratedBottom(child) + lp.bottomMargin;
        } else {
            return getDecoratedRight(child) + lp.rightMargin;
        }
    }

    private int getChildMeasurement(View child) {
        return (mIsVertical ? getDecoratedMeasuredHeight(child) : getDecoratedMeasuredWidth(child));
    }

    private void offsetChildren(int offset) {
        if (mIsVertical) {
            offsetChildrenVertical(offset);
        } else {
            offsetChildrenHorizontal(offset);
        }
    }

    private void recycleChildrenOutOfBounds(Direction direction, Recycler recycler) {
        if (direction == Direction.END) {
            recycleChildrenFromStart(direction, recycler);
        } else {
            recycleChildrenFromEnd(direction, recycler);
        }
    }

    private void recycleChildrenFromStart(Direction direction, Recycler recycler) {
        final int childCount = getChildCount();
        final int childrenStart = getStartWithPadding();

        int detachedCount = 0;
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final int childEnd = getChildEnd(child);

            if (childEnd >= childrenStart) {
                break;
            }

            detachedCount++;

            detachChild(child, direction);
        }

        mFirstPosition += detachedCount;

        while (--detachedCount >= 0) {
            removeAndRecycleViewAt(0, recycler);
        }
    }

    private void recycleChildrenFromEnd(Direction direction, Recycler recycler) {
        final int childrenEnd = getEndWithPadding();
        final int childCount = getChildCount();

        int firstDetachedPos = 0;
        int detachedCount = 0;
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final int childStart = getChildStart(child);

            if (childStart <= childrenEnd) {
                break;
            }

            firstDetachedPos = i;
            detachedCount++;

            detachChild(child, direction);
        }

        while (--detachedCount >= 0) {
            removeAndRecycleViewAt(firstDetachedPos, recycler);
        }
    }

    private int scrollBy(int delta, Recycler recycler, State state) {
        final int childCount = getChildCount();
        if (childCount == 0 || delta == 0) {
            return 0;
        }

        final int outerStart = getLayoutStart();
        final int outerEnd = getLayoutEnd();

        final int start = getStartWithPadding();
        final int end = getEndWithPadding();

        final int totalSpace = getTotalSpace();
        if (delta < 0) {
            delta = Math.max(-(totalSpace - 1), delta);
        } else {
            delta = Math.min(totalSpace - 1, delta);
        }

        final boolean cannotScrollBackward = (mFirstPosition == 0 &&
                outerStart >= start && delta <= 0);
        final boolean cannotScrollForward = (mFirstPosition + childCount == state.getItemCount() &&
                outerEnd <= end && delta >= 0);

        if (cannotScrollForward || cannotScrollBackward) {
            return 0;
        }

        offsetChildren(-delta);

        final Direction direction = (delta > 0 ? Direction.END : Direction.START);
        recycleChildrenOutOfBounds(direction, recycler);

        final int absDelta = Math.abs(delta);
        if (canAddMoreViews(Direction.START, start - absDelta) ||
            canAddMoreViews(Direction.END, end + absDelta)) {
            fillGap(direction, recycler, state);
        }

        mFirstVisiblePosition = mFirstPosition;

        return delta;
    }

    private void fillGap(Direction direction, Recycler recycler, State state) {
        final int childCount = getChildCount();
        final int extraSpace = getExtraLayoutSpace(state);

        if (direction == Direction.END) {
            fillAfter(mFirstPosition + childCount, recycler, state, extraSpace);
            correctTooHigh(childCount, recycler, state);
        } else {
            fillBefore(mFirstPosition - 1, recycler, extraSpace);
            correctTooLow(childCount, recycler, state);
        }
    }

    private void fillBefore(int pos, Recycler recycler) {
        fillBefore(pos, recycler, 0);
    }

    private void fillBefore(int position, Recycler recycler, int extraSpace) {
        final int edge = getStartWithPadding() - extraSpace;

        while (canAddMoreViews(Direction.START, edge) && position >= 0) {
            makeAndAddView(position, Direction.START, recycler);
            position--;
        }

        mFirstPosition = position + 1;
    }

    private void fillAfter(int pos, Recycler recycler, State state) {
        fillAfter(pos, recycler, state, 0);
    }

    private void fillAfter(int position, Recycler recycler, State state, int extraSpace) {
        final int edge = getEndWithPadding() + extraSpace;

        final int itemCount = state.getItemCount();
        while (canAddMoreViews(Direction.END, edge) && position < itemCount) {
            makeAndAddView(position, Direction.END, recycler);
            position++;
        }
    }

    private View fillSpecific(int position, Recycler recycler, State state) {
        makeAndAddView(position, Direction.END, recycler);

        // Possibly changed again in fillBefore if we add children
        // before this one.
        mFirstPosition = position;

        final int extraSpaceBefore;
        final int extraSpaceAfter;

        final int extraSpace = getExtraLayoutSpace(state);
        if (state.getTargetScrollPosition() < position) {
            extraSpaceAfter = 0;
            extraSpaceBefore = extraSpace;
        } else {
            extraSpaceAfter = extraSpace;
            extraSpaceBefore = 0;
        }

        fillBefore(position - 1, recycler, extraSpaceBefore);

        // This will correct for the top of the first view not
        // touching the top of the parent.
        adjustViewsStartOrEnd();

        fillAfter(position + 1, recycler, state, extraSpaceAfter);
        correctTooHigh(getChildCount(), recycler, state);

        return null;
    }

    private void fillScrapViewsIfNeeded(Recycler recycler, State state) {
        final int childCount = getChildCount();
        if (childCount == 0 || state.isPreLayout() || !supportsPredictiveItemAnimations()) {
            return;
        }

        final List<ViewHolder> scrapList = recycler.getScrapList();
        final int scrapCount = scrapList.size();

        int extraSpaceBefore = 0;
        int extraSpaceAfter = 0;

        for (int i = 0; i < scrapCount; i++) {
            final ViewHolder holder = scrapList.get(i);

            final int childMeasurement = getChildMeasurement(holder.itemView);
            if (holder.getPosition() < mFirstPosition) {
                extraSpaceBefore += childMeasurement;
            } else {
                extraSpaceAfter += childMeasurement;
            }
        }

        if (extraSpaceBefore > 0) {
            fillBefore(mFirstPosition - 1, recycler, extraSpaceBefore);
        }

        if (extraSpaceAfter > 0) {
            fillAfter(childCount, recycler, state, extraSpaceAfter);
        }
    }

    private void correctTooHigh(int childCount, Recycler recycler, State state) {
        // First see if the last item is visible. If it is not, it is OK for the
        // top of the list to be pushed up.
        final int lastPosition = mFirstPosition + childCount - 1;
        if (lastPosition != state.getItemCount() - 1 || childCount == 0) {
            return;
        }

        // Get the last end edge.
        final int lastEnd = getLayoutEnd();

        // This is bottom of our drawable area.
        final int start = getStartWithPadding();
        final int end = getEndWithPadding();

        // This is how far the end edge of the last view is from the end of the
        // drawable area.
        int endOffset = end - lastEnd;

        int firstStart = getLayoutStart();

        // Make sure we are 1) Too high, and 2) Either there are more rows above the
        // first row or the first row is scrolled off the top of the drawable area
        if (endOffset > 0 && (mFirstPosition > 0 || firstStart < start))  {
            if (mFirstPosition == 0) {
                // Don't pull the top too far down.
                endOffset = Math.min(endOffset, start - firstStart);
            }

            // Move everything down
            offsetChildren(endOffset);

            if (mFirstPosition > 0) {
                // Fill the gap that was opened above mFirstPosition with more
                // children, if possible.
                fillBefore(mFirstPosition - 1, recycler);

                // Close up the remaining gap.
                adjustViewsStartOrEnd();
            }
        }
    }

    private void correctTooLow(int childCount, Recycler recycler, State state) {
        // First see if the first item is visible. If it is not, it is OK for the
        // end of the list to be pushed forward.
        if (mFirstPosition != 0 || childCount == 0) {
            return;
        }

        final int firstStart = getLayoutStart();
        final int start = getStartWithPadding();
        final int end = getEndWithPadding();
        final int itemCount = state.getItemCount();

        // This is how far the start edge of the first view is from the start of the
        // drawable area.
        int startOffset = firstStart - start;

        int lastEnd = getLayoutEnd();
        final int lastPosition = mFirstPosition + childCount - 1;

        // Make sure we are 1) Too low, and 2) Either there are more columns/rows below the
        // last column/row or the last column/row is scrolled off the end of the
        // drawable area.
        if (startOffset > 0) {
            if (lastPosition < itemCount - 1 || lastEnd > end)  {
                if (lastPosition == itemCount - 1) {
                    // Don't pull the bottom too far up.
                    startOffset = Math.min(startOffset, lastEnd - end);
                }

                // Move everything up.
                offsetChildren(-startOffset);

                if (lastPosition < itemCount - 1) {
                    // Fill the gap that was opened below the last position with more
                    // children, if possible.
                    fillAfter(lastPosition + 1, recycler, state);

                    // Close up the remaining gap.
                    adjustViewsStartOrEnd();
                }
            } else if (lastPosition == itemCount - 1) {
                adjustViewsStartOrEnd();
            }
        }
    }

    private void adjustViewsStartOrEnd() {
        if (getChildCount() == 0) {
            return;
        }

        int delta = getLayoutStart() - getStartWithPadding();
        if (delta < 0) {
            // We only are looking to see if we are too low, not too high
            delta = 0;
        }

        if (delta != 0) {
            offsetChildren(-delta);
        }
    }

    private View makeAndAddView(int position, Direction direction, Recycler recycler) {
        final View child = recycler.getViewForPosition(position);
        addView(child, (direction == Direction.END ? -1 : 0));

        measureChild(child);
        layoutChild(child, direction);

        return child;
    }

    protected int getExtraLayoutSpace(State state) {
        if (state.hasTargetScrollPosition()) {
            return getTotalSpace();
        } else {
            return 0;
        }
    }

    protected int getPendingScrollPosition() {
        if (mPendingSavedState != null) {
            return mPendingSavedState.anchorItemPosition;
        }

        return mPendingScrollPosition;
    }

    protected int getPendingScrollOffset() {
        if (mPendingSavedState != null) {
            return 0;
        }

        return mPendingScrollOffset;
    }

    @Override
    public int getDecoratedMeasuredWidth(View child) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedMeasuredWidth(child) + lp.leftMargin + lp.rightMargin;
    }

    @Override
    public int getDecoratedMeasuredHeight(View child) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedMeasuredHeight(child) + lp.topMargin + lp.bottomMargin;
    }

    @Override
    public void onLayoutChildren(Recycler recycler, State state) {
        int pendingScrollPosition = getPendingScrollPosition();
        if (pendingScrollPosition != RecyclerView.NO_POSITION) {
            if (pendingScrollPosition < 0 || pendingScrollPosition >= state.getItemCount()) {
                pendingScrollPosition = RecyclerView.NO_POSITION;
            }
        }

        final int anchorItemPosition;
        if (pendingScrollPosition != RecyclerView.NO_POSITION) {
            anchorItemPosition = pendingScrollPosition;
        } else if (getChildCount() > 0) {
            anchorItemPosition = mFirstPosition;
        } else {
            anchorItemPosition = 0;
        }

        detachAndScrapAttachedViews(recycler);
        fillSpecific(anchorItemPosition, recycler, state);

        mFirstVisiblePosition = mFirstPosition;
        fillScrapViewsIfNeeded(recycler, state);

        mPendingScrollPosition = RecyclerView.NO_POSITION;
        mPendingScrollOffset = 0;
        mPendingSavedState = null;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        if (mIsVertical) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return true;
    }

    @Override
    public int scrollHorizontallyBy(int dx, Recycler recycler, State state) {
        if (mIsVertical) {
            return 0;
        }

        return scrollBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, Recycler recycler, State state) {
        if (!mIsVertical) {
            return 0;
        }

        return scrollBy(dy, recycler, state);
    }

    @Override
    public boolean canScrollHorizontally() {
        return !mIsVertical;
    }

    @Override
    public boolean canScrollVertically() {
        return mIsVertical;
    }

    @Override
    public void scrollToPosition(int position) {
        scrollToPositionWithOffset(position, 0);
    }

    public void scrollToPositionWithOffset(int position, int offset) {
        mPendingScrollPosition = position;
        mPendingScrollOffset = offset;
        requestLayout();
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, State state, int position) {
        final LinearSmoothScroller scroller = new LinearSmoothScroller(recyclerView.getContext()) {
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                if (getChildCount() == 0) {
                    return null;
                }

                final int direction = targetPosition < mFirstPosition ? -1 : 1;
                if (mIsVertical) {
                    return new PointF(0, direction);
                } else {
                    return new PointF(direction, 0);
                }
            }

            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }

            @Override
            protected int getHorizontalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };

        scroller.setTargetPosition(position);
        startSmoothScroll(scroller);
    }

    @Override
    public int computeHorizontalScrollOffset(State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        return getFirstVisiblePosition();
    }

    @Override
    public int computeVerticalScrollOffset(State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        return getFirstVisiblePosition();
    }

    @Override
    public int computeHorizontalScrollExtent(State state) {
        return getChildCount();
    }

    @Override
    public int computeVerticalScrollExtent(State state) {
        return getChildCount();
    }

    @Override
    public int computeHorizontalScrollRange(State state) {
        return state.getItemCount();
    }

    @Override
    public int computeVerticalScrollRange(State state) {
        return state.getItemCount();
    }

    @Override
    public void onMeasure(Recycler recycler, State state, int widthSpec, int heightSpec) {
        super.onMeasure(recycler, state, widthSpec, heightSpec);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState state = new SavedState(superState);

        int anchorItemPosition = getPendingScrollPosition();
        if (anchorItemPosition == RecyclerView.NO_POSITION) {
            anchorItemPosition = mFirstVisiblePosition;
        }
        state.anchorItemPosition = anchorItemPosition;

        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        mPendingSavedState = (SavedState) state;
        super.onRestoreInstanceState(mPendingSavedState.getSuperState());
        requestLayout();
    }

    public Orientation getOrientation() {
        return (mIsVertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
    }

    public void setOrientation(Orientation orientation) {
        final boolean isVertical = (orientation == Orientation.VERTICAL);
        if (this.mIsVertical == isVertical) {
            return;
        }

        this.mIsVertical = isVertical;
        requestLayout();
    }

    public int getStartWithPadding() {
        return (mIsVertical ? getPaddingTop() : getPaddingLeft());
    }

    public int getEndWithPadding() {
        if (mIsVertical) {
            return (getHeight() - getPaddingBottom());
        } else {
            return (getWidth() - getPaddingRight());
        }
    }

    public int getFirstVisiblePosition() {
        return mFirstPosition;
    }

    protected abstract boolean canAddMoreViews(Direction direction, int limit);

    protected abstract int getLayoutStart();
    protected abstract int getLayoutEnd();

    protected abstract void measureChild(View child);
    protected abstract void layoutChild(View child, Direction direction);
    protected abstract void detachChild(View child, Direction direction);

    protected static class SavedState extends BaseSavedState {
        private int anchorItemPosition;

        protected SavedState(Parcelable superState) {
            super(superState != null ? superState : Bundle.EMPTY);
        }

        protected SavedState(Parcel in) {
            super(in);
            anchorItemPosition = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(anchorItemPosition);
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
