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

package org.lucasr.twowayview.sample;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;

public class MainActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);

        ActionBar.Tab tab = actionBar.newTab()
                .setText("").setIcon(R.drawable.ic_list)
                .setTabListener(new TabListener<TWFragment>(
                        R.layout.layout_list, "list"));
        actionBar.addTab(tab);

        tab = actionBar.newTab()
                .setText("").setIcon(R.drawable.ic_grid)
                .setTabListener(new TabListener<TWFragment>(
                        R.layout.layout_grid, "grid"));
        actionBar.addTab(tab);

        tab = actionBar.newTab()
                .setText("").setIcon(R.drawable.ic_staggered)
                .setTabListener(new TabListener<TWFragment>(
                        R.layout.layout_staggered_grid, "staggered"));
        actionBar.addTab(tab);

        tab = actionBar.newTab()
                .setText("").setIcon(R.drawable.ic_spannable)
                .setTabListener(new TabListener<TWFragment>(
                        R.layout.layout_spannable_grid, "spannable"));
        actionBar.addTab(tab);
    }

    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private Fragment mFragment;
        private final int mLayoutId;
        private final String mTag;

        public TabListener(int layoutId, String tag) {
            mLayoutId = layoutId;
            mTag = tag;
        }

        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = (Fragment) TWFragment.newInstance(mLayoutId);
                ft.add(R.id.content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {

        }
    }
}
