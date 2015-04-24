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

package org.lucasr.twowayview.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.DragEvent;
import org.lucasr.twowayview.TwoWayLayoutManager;
import org.lucasr.twowayview.TwoWayLayoutManager.Orientation;

import java.lang.reflect.Constructor;

public class TwoWayView extends RecyclerView {
    private static final String LOGTAG = "TwoWayView";

    private static final Class<?>[] sConstructorSignature = new Class[] {
            Context.class, AttributeSet.class};

    final Object[] sConstructorArgs = new Object[2];

    private Reorderer mReorderer;
    private OnReorderingListener mReorderingListener;

    public TwoWayView(Context context) {
        this(context, null);
    }

    public TwoWayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TwoWayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.twowayview_TwoWayView, defStyle, 0);

        final String name = a.getString(R.styleable.twowayview_TwoWayView_twowayview_layoutManager);
        if (!TextUtils.isEmpty(name)) {
            loadLayoutManagerFromName(context, attrs, name);
        }

        a.recycle();
    }

    private void loadLayoutManagerFromName(Context context, AttributeSet attrs, String name) {
        try {
            final int dotIndex = name.indexOf('.');
            if (dotIndex == -1) {
                name = "org.lucasr.twowayview.widget." + name;
            } else if (dotIndex == 0) {
                final String packageName = context.getPackageName();
                name = packageName + "." + name;
            }

            Class<? extends TwoWayLayoutManager> clazz =
                    context.getClassLoader().loadClass(name).asSubclass(TwoWayLayoutManager.class);

            Constructor<? extends TwoWayLayoutManager> constructor =
                    clazz.getConstructor(sConstructorSignature);

            sConstructorArgs[0] = context;
            sConstructorArgs[1] = attrs;

            setLayoutManager(constructor.newInstance(sConstructorArgs));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load TwoWayLayoutManager from " +
                                             "class: " + name, e);
        }
    }

    @Override
    public void setLayoutManager(LayoutManager layout) {
        if (!(layout instanceof TwoWayLayoutManager)) {
            throw new IllegalArgumentException("TwoWayView can only use TwoWayLayoutManager " +
                                                "subclasses as its layout manager");
        }

        super.setLayoutManager(layout);
    }

    public Orientation getOrientation() {
        TwoWayLayoutManager layout = (TwoWayLayoutManager) getLayoutManager();
        return layout.getOrientation();
    }

    public void setOrientation(Orientation orientation) {
        TwoWayLayoutManager layout = (TwoWayLayoutManager) getLayoutManager();
        layout.setOrientation(orientation);
    }

    public int getFirstVisiblePosition() {
        TwoWayLayoutManager layout = (TwoWayLayoutManager) getLayoutManager();
        return layout.getFirstVisiblePosition();
    }

    public int getLastVisiblePosition() {
        TwoWayLayoutManager layout = (TwoWayLayoutManager) getLayoutManager();
        return layout.getLastVisiblePosition();
    }

    /*
    The following methods all relate to reordering, and are only applicable to API 11+
     */

    /**
     * Returns whether this {@code TwoWayView} is in middle of reordering.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public boolean isReordering() {
        return mReorderer.isReordering();
    }

    @Override
    public Adapter getAdapter() {
        if(mReorderer == null) {
            return super.getAdapter();
        }
        else {
            return ((ReordererAdapterDecorator) super.getAdapter()).getDecoratedAdapter();
        }
    }

    /*pacakge*/ ReordererAdapterDecorator getReordererAdapter() {
        if(mReorderer != null) {
            return (ReordererAdapterDecorator) super.getAdapter();
        }
        else {
            return null;
        }
    }

    /**
     * <b>Should only be used for API 11+.</b><br/>
     * Set a new adapter to provide child views on demand.
     * When adapter is changed, all existing views are recycled back to the pool.
     * If the pool has only one adapter, it will be cleared.
     * {@code adapter} must be a {@link ReorderableAdapter}.
     * @param adapter the new adapter to set
     * @throws java.lang.IllegalArgumentException if {@code adapter} is not a {@code ReorderableAdapter}
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setReorderableAdapter(Adapter adapter) {
        // if its null, we can't go into reorderable mode
        if(adapter == null) {
            setAdapter(null);
            return;
        }

        super.setAdapter(ReordererAdapterDecorator.decorateAdapter(adapter));
        mReorderer = new Reorderer(this);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if(mReorderer != null) {
            mReorderer = null;
        }
        super.setAdapter(adapter);
    }

    /**
     * <b>Should only be used for API 11+.</b><br/>
     * Swaps the current adapter with the provided one. It is similar to setAdapter(Adapter) but assumes
     * existing adapter and the new adapter uses the same RecyclerView.ViewHolder and does not clear the RecycledViewPool.
     * <br/>Note that it still calls onAdapterChanged callbacks.
     * {@code adapter} must be a {@link ReorderableAdapter}.
     * @param adapter the new adapter to set
     * @throws java.lang.NullPointerException if {@code adapter} is {@code null}
     * @throws java.lang.IllegalArgumentException if {@code adapter} is not a {@code ReorderableAdapter}
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void swapReorderableAdapter(Adapter adapter, boolean removeAndRecycleExistingViews) {
        //we don't check for null here because we want to propogate the NPE from ReordererAdapterDecorator
        super.swapAdapter(ReordererAdapterDecorator.decorateAdapter(adapter), removeAndRecycleExistingViews);
        mReorderer = new Reorderer(this);
    }

    @Override
    public void swapAdapter(Adapter adapter, boolean removeAndRecycleExistingViews) {
        if(mReorderer != null) {
            mReorderer = null;
        }
        super.swapAdapter(adapter, removeAndRecycleExistingViews);
    }

    /**
     * Sets a {@link OnReorderingListener}.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setOnReorderingListener(OnReorderingListener onReorderingListener) {
        this.mReorderingListener = onReorderingListener;
    }

    /*package*/ OnReorderingListener getOnReorderingListener() {
        return mReorderingListener;
    }

    /**
     * <b>Should only be used for API 11+.</b><br/>
     * Start a reordering operation.
     * {@link TwoWayView#setReorderableAdapter} or {@link TwoWayView#swapReorderableAdapter} must be called at some
     * point before this.
     * @param position the position to drag
     * @return whether the reordering operation was started successfully
     * @throws java.lang.IllegalStateException if {@code setReorderableAdapter} or
     * {@code swapReorderableAdapter} has not been called
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public final boolean startReorder(int position) {
        if(mReorderer == null) {
            throw new IllegalStateException("Cannot call startReorder if setReorderableAdapter or " +
                    "swapReorderableAdapter has not been called");
        }

        return mReorderer.startReorder(position);
    }

    // As per this bug - https://code.google.com/p/android/issues/detail?id=25073
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public boolean dispatchDragEvent(DragEvent ev) {
        if(mReorderer == null) {
            return super.dispatchDragEvent(ev);
        }
        else {
            return mReorderer.dispatchDragEvent(ev);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    /*package*/ boolean superDispatchDragEvent(DragEvent ev) {
        return super.dispatchDragEvent(ev);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public boolean onDragEvent(DragEvent ev) {
        if(mReorderer == null) {
            return super.onDragEvent(ev);
        }
        else {
            return mReorderer.onDragEvent(ev);
        }
    }
}
