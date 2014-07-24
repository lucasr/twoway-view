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
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import static android.support.v7.widget.RecyclerView.SCROLL_STATE_IDLE;
import static android.support.v7.widget.RecyclerView.SCROLL_STATE_DRAGGING;
import static android.support.v7.widget.RecyclerView.SCROLL_STATE_SETTLING;

import org.lucasr.twowayview.TWItemClickListener;
import org.lucasr.twowayview.TWItemClickListener.OnItemClickListener;
import org.lucasr.twowayview.TWItemClickListener.OnItemLongClickListener;
import org.lucasr.twowayview.TWLayoutManager.Orientation;
import org.lucasr.twowayview.TWSpannableGridLayoutManager;
import org.lucasr.twowayview.TWView;

public class TWFragment extends Fragment {
    private static final String ARG_LAYOUT_ID = "layout_id";

    private TWView mRecyclerView;
    private TextView mPositionText;
    private TextView mCountText;
    private TextView mStateText;
    private Toast mToast;

    private int mLayoutId;

    public static TWFragment newInstance(int layoutId) {
        TWFragment fragment = new TWFragment();

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

        final Activity activity = getActivity();

        mToast = Toast.makeText(activity, "", Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.CENTER, 0, 0);

        mRecyclerView = (TWView) view.findViewById(R.id.list);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLongClickable(true);

        mPositionText = (TextView) view.getRootView().findViewById(R.id.position);
        mCountText = (TextView) view.getRootView().findViewById(R.id.count);

        mStateText = (TextView) view.getRootView().findViewById(R.id.state);
        updateState(SCROLL_STATE_IDLE);

        TWItemClickListener clickListener = TWItemClickListener.addTo(mRecyclerView);

        clickListener.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(RecyclerView parent, View child, int position, long id) {
                mToast.setText("Item clicked: " + position);
                mToast.show();
            }
        });

        clickListener.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(RecyclerView parent, View child, int position, long id) {
                mToast.setText("Item long pressed: " + position);
                mToast.show();
                return true;
            }
        });

        mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(int scrollState) {
                updateState(scrollState);
            }

            @Override
            public void onScrolled(int i, int i2) {
                mPositionText.setText("First: " + mRecyclerView.getFirstVisiblePosition());
                mCountText.setText("Count: " + mRecyclerView.getChildCount());
            }
        });

        mRecyclerView.setAdapter(new SimpleAdapter(activity, mRecyclerView, mLayoutId));

//        mRecyclerView.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                mRecyclerView.smoothScrollToPosition(20);
//            }
//        }, 5000);
    }

    private void updateState(int scrollState) {
        String stateName = "Undefined";
        switch(scrollState) {
            case SCROLL_STATE_IDLE:
                stateName = "Idle";
                break;

            case SCROLL_STATE_DRAGGING:
                stateName = "Dragging";
                break;

            case SCROLL_STATE_SETTLING:
                stateName = "Flinging";
                break;
        }

        mStateText.setText(stateName);
    }

    public int getLayoutId() {
        return getArguments().getInt(ARG_LAYOUT_ID);
    }

    public static class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.SimpleViewHolder> {
        private final Context mContext;
        private final TWView mRecyclerView;
        private final int mLayoutId;

        public static class SimpleViewHolder extends RecyclerView.ViewHolder {
            public final TextView title;

            public SimpleViewHolder(View view) {
                super(view);
                title = (TextView) view.findViewById(R.id.title);
            }
        }

        public SimpleAdapter(Context context, TWView recyclerView, int layoutId) {
            mContext = context;
            mRecyclerView = recyclerView;
            mLayoutId = layoutId;
        }

        @Override
        public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(mContext).inflate(R.layout.item, parent, false);
            return new SimpleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(SimpleViewHolder holder, int position) {
            holder.title.setText(String.valueOf(position));

            boolean isVertical = (mRecyclerView.getOrientation() == Orientation.VERTICAL);
            final View itemView = holder.itemView;

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

                final ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
                if (!isVertical && lp.width != id) {
                    lp.width = size;
                    itemView.setLayoutParams(lp);
                } else if (isVertical && lp.height != id) {
                    lp.height = size;
                    itemView.setLayoutParams(lp);
                }
            } else if (mLayoutId == R.layout.layout_spannable_grid) {
                final TWSpannableGridLayoutManager.LayoutParams lp =
                        (TWSpannableGridLayoutManager.LayoutParams) itemView.getLayoutParams();

                final int span1 = (position == 0 || position == 3 ? 2 : 1);
                final int span2 = (position == 0 ? 2 : (position == 3 ? 3 : 1));

                final int colSpan = (isVertical ? span2 : span1);
                final int rowSpan = (isVertical ? span1 : span2);

                if (lp.rowSpan != rowSpan || lp.colSpan != colSpan) {
                    lp.rowSpan = rowSpan;
                    lp.colSpan = colSpan;
                    itemView.setLayoutParams(lp);
                }
            }
        }

        @Override
        public int getItemCount() {
            return 100;
        }
    }
}
