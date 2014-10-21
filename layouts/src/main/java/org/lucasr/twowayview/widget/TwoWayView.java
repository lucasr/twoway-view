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

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.lucasr.twowayview.TwoWayLayoutManager;
import org.lucasr.twowayview.TwoWayLayoutManager.Orientation;

import java.lang.reflect.Constructor;

public class TwoWayView extends RecyclerView {
    private static final String LOGTAG = "TwoWayView";

    private static final Class<?>[] sConstructorSignature = new Class[] {
            Context.class, AttributeSet.class};

    final Object[] sConstructorArgs = new Object[2];

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
}
