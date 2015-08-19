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
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final String ARG_SELECTED_LAYOUT_ID = "selectedLayoutId";

    private final int DEFAULT_LAYOUT = R.layout.layout_list;

    private int mSelectedLayoutId;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tabs_layout);

        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.pagers);

        mSelectedLayoutId = DEFAULT_LAYOUT;
        if (savedInstanceState != null) {
            mSelectedLayoutId = savedInstanceState.getInt(ARG_SELECTED_LAYOUT_ID);
        }

        CustomeFragmentAdapter adapter = new CustomeFragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(LayoutFragment.newInstance(R.layout.layout_list), "list");
        adapter.addFragment(LayoutFragment.newInstance(R.layout.layout_grid), "grid");
        adapter.addFragment(LayoutFragment.newInstance(R.layout.layout_staggered_grid), "staggered");
        adapter.addFragment(LayoutFragment.newInstance(R.layout.layout_spannable_grid), "spannable");

        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);

        /*addLayoutTab(
                actionBar, R.layout.layout_list, R.drawable.ic_list, "list");
        addLayoutTab(
                actionBar, R.layout.layout_grid, R.drawable.ic_grid, "grid");
        addLayoutTab(
                actionBar, R.layout.layout_staggered_grid, R.drawable.ic_staggered, "staggered");
        addLayoutTab(
                actionBar, R.layout.layout_spannable_grid, R.drawable.ic_spannable, "spannable");*/

        int count = mTabLayout.getTabCount();

        for (int i = 0; i < count; i++) {
            //mTabLayout.getTabAt(i).setCustomView(null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_SELECTED_LAYOUT_ID, mSelectedLayoutId);
    }

    public class CustomeFragmentAdapter extends FragmentPagerAdapter {

        private final List<Fragment> mFragments = new ArrayList<>();
        private final List<String> mFragmentTitles = new ArrayList<>();

        public CustomeFragmentAdapter(android.support.v4.app.FragmentManager fm) {
            super(fm);
        }

        public void addFragment(Fragment fragment, String title) {
            mFragments.add(fragment);
            mFragmentTitles.add(title);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitles.get(position);
        }
    }
}
