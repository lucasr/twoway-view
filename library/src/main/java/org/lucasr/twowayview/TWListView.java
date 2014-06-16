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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class TWListView extends TWView {
    private static final String LOGTAG = "TwoWayListView";

    private TWLayoutState mLayoutState;
    private boolean mIsVertical;

    private final Rect mTempRect = new Rect();

    public TWListView(Context context) {
        this(context, null);
    }

    public TWListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIsVertical = (getOrientation() == Orientation.VERTICAL);
    }

    private void ensureLayoutState() {
        if (mLayoutState != null) {
            return;
        }

        mLayoutState = new TWLayoutState(this, 1);
    }

    private void recreateLayoutState() {
        mLayoutState = null;
        ensureLayoutState();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recreateLayoutState();
    }

    @Override
    public void setOrientation(Orientation orientation) {
        final boolean changed = (getOrientation() != orientation);
        super.setOrientation(orientation);

        if (changed) {
            mIsVertical = (orientation == Orientation.VERTICAL);
            recreateLayoutState();
        }
    }

    @Override
    protected void offsetLayout(int offset) {
        mLayoutState.offset(offset);
    }

    @Override
    protected void resetLayout(int offset) {
        if (mLayoutState != null) {
            mLayoutState.resetEndEdges();
        }
    }

    @Override
    protected int getOuterStartEdge() {
        mLayoutState.getLane(0, mTempRect);
        return (mIsVertical ? mTempRect.top : mTempRect.left);
    }

    @Override
    protected int getInnerStartEdge() {
        // Inner and outer edges are always the same in a list
        return getOuterStartEdge();
    }

    @Override
    protected int getInnerEndEdge() {
        // Inner and outer edges are always the same in a list
        return getOuterEndEdge();
    }

    @Override
    protected int getOuterEndEdge() {
        mLayoutState.getLane(0, mTempRect);
        return (mIsVertical ? mTempRect.bottom : mTempRect.right);
    }

    @Override
    protected int getChildWidthMeasureSpec(View child, int position, LayoutParams lp) {
        if (!mIsVertical && lp.width == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLayoutState.getLaneSize(), MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected int getChildHeightMeasureSpec(View child, int position, LayoutParams lp) {
        if (mIsVertical && lp.height == LayoutParams.WRAP_CONTENT) {
            return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (!mIsVertical) {
            return MeasureSpec.makeMeasureSpec(mLayoutState.getLaneSize(), MeasureSpec.EXACTLY);
        } else {
            return MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }
    }

    @Override
    protected void detachChildFromLayout(View child, int position, Flow flow) {
        final int spacing = (mIsVertical ? getVerticalSpacing() : getHorizontalSpacing());
        final int dimension = (mIsVertical ? child.getHeight() : child.getWidth());
        mLayoutState.removeFromLane(0, flow, dimension + spacing);
    }

    @Override
    protected void attachChildToLayout(View child, int position, Flow flow, Rect childFrame) {
        final int dimension = mLayoutState.getChildFrame(child, 0, flow, childFrame);
        mLayoutState.addToLane(0, flow, dimension);
    }
}
