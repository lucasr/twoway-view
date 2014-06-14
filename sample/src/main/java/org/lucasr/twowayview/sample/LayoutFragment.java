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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.lucasr.twowayview.TWSpannableGridView;
import org.lucasr.twowayview.TWView;
import org.lucasr.twowayview.TWView.Orientation;

public class LayoutFragment extends Fragment {
    private static final String ARG_LAYOUT_ID = "layout_id";

    private TWView mListView;
    private int mLayoutId;

    private Toast mToast;
    private String mClickMessage;
    private String mScrollMessage;
    private String mStateMessage;

    public static LayoutFragment newInstance(int layoutId) {
        LayoutFragment fragment = new LayoutFragment();

        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLayoutId = getArguments().getInt(ARG_LAYOUT_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(mLayoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mClickMessage = "";
        mScrollMessage = "";
        mStateMessage = "";

        final Activity activity = getActivity();

        mToast = Toast.makeText(activity, "", Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.CENTER, 0, 0);

        mListView = (TWView) view.findViewById(R.id.list);
        mListView.setLongClickable(true);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View child, int position,
                    long id) {
                mClickMessage = "Item clicked: " + position;
                refreshToast();
            }
        });

        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View child,
                    int position, long id) {
                mClickMessage = "Item long pressed: " + position;
                refreshToast();
                return true;
            }
        });

        mListView.setOnScrollListener(new TWView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(TWView view, int scrollState) {
                String stateName = "Undefined";
                switch(scrollState) {
                case SCROLL_STATE_IDLE:
                    stateName = "Idle";
                    break;

                case SCROLL_STATE_TOUCH_SCROLL:
                    stateName = "Dragging";
                    break;

                case SCROLL_STATE_FLING:
                    stateName = "Flinging";
                    break;
                }

                mStateMessage = "Scroll state changed: " + stateName;
                refreshToast();
            }

            @Override
            public void onScroll(TWView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
                mScrollMessage = "Scroll (first: " + firstVisibleItem + ", count = " + visibleItemCount + ")";
                refreshToast();
            }
        });

        mListView.setAdapter(new SimpleAdapter(activity, mLayoutId));
    }

    private void refreshToast() {
        StringBuffer buffer = new StringBuffer();

        if (!TextUtils.isEmpty(mClickMessage)) {
            buffer.append(mClickMessage);
        }

        if (!TextUtils.isEmpty(mScrollMessage)) {
            if (buffer.length() != 0) {
                buffer.append("\n");
            }

            buffer.append(mScrollMessage);
        }

        if (!TextUtils.isEmpty(mStateMessage)) {
            if (buffer.length() != 0) {
                buffer.append("\n");
            }

            buffer.append(mStateMessage);
        }

        mToast.setText(buffer.toString());
        mToast.show();
    }

    private static class ViewHolder {
        public TextView title;
    }

    private static class SimpleAdapter extends BaseAdapter {
        private final Context mContext;
        private final int mLayoutId;

        public SimpleAdapter(Context context, int layoutId) {
            mContext = context;
            mLayoutId = layoutId;
        }

        @Override
        public int getCount() {
            return 100;
        }

        @Override
        public Integer getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item, parent, false);

                holder = new ViewHolder();
                holder.title = (TextView) convertView.findViewById(R.id.title);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.title.setText(String.valueOf(position));

            boolean isVertical = (((TWView) parent).getOrientation() == TWView.Orientation.VERTICAL);

            if (mLayoutId == R.layout.layout_staggered_grid) {
                final int id;
                if (position % 3 == 0) {
                    id = R.dimen.staggered_child_medium;
                } else if (position % 5 == 0) {
                    id = R.dimen.staggered_child_large;
                } else if (position % 7 == 0) {
                    id = R.dimen.staggered_child_xlarge;
                } else {
                    id = R.dimen.staggered_child_small;
                }

                final int size = mContext.getResources().getDimensionPixelSize(id);

                final ViewGroup.LayoutParams lp = convertView.getLayoutParams();
                if (!isVertical && lp.width != id) {
                    lp.width = size;
                    convertView.setLayoutParams(lp);
                } else if (isVertical && lp.height != id) {
                    lp.height = size;
                    convertView.setLayoutParams(lp);
                }
            } else if (mLayoutId == R.layout.layout_spannable_grid) {
                final TWSpannableGridView.LayoutParams lp =
                        (TWSpannableGridView.LayoutParams) convertView.getLayoutParams();

                final int rowSpan = (position == 0 || position == 3 ? 2 : 1);
                final int colSpan = (position == 0 ? 2 : (position == 3 ? 3 : 1));

                if (lp.rowSpan != rowSpan || lp.colSpan != colSpan) {
                    lp.rowSpan = rowSpan;
                    lp.colSpan = colSpan;
                    convertView.setLayoutParams(lp);
                }
            }

            return convertView;
        }
    }

}
