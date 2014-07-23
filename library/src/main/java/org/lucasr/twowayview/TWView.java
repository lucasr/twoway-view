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
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

import org.lucasr.twowayview.TWLayoutManager.Orientation;

public class TWView extends RecyclerView {
    private static final String LOGTAG = "TWView";

    public TWView(Context context) {
        this(context, null);
    }

    public TWView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TWView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public Orientation getOrientation() {
        TWLayoutManager layout = (TWLayoutManager) getLayoutManager();
        return layout.getOrientation();
    }

    public void setOrientation(Orientation orientation) {
        TWLayoutManager layout = (TWLayoutManager) getLayoutManager();
        layout.setOrientation(orientation);
    }

    public int getFirstVisiblePosition() {
        TWLayoutManager layout = (TWLayoutManager) getLayoutManager();
        return layout.getFirstVisiblePosition();
    }
}
