/*
 * Copyright (C) 2012 Lucas Rocha
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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class SimpleListAdapter extends BaseAdapter {
	private final Context mContext;
    private final int mItemLayoutId;

    public SimpleListAdapter(Context context, int itemLayoutId) {
		mContext = context;
        mItemLayoutId = itemLayoutId;
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
			convertView = LayoutInflater.from(mContext).inflate(mItemLayoutId, parent, false);

            View titleView = convertView.findViewById(R.id.title);
            if (!(titleView instanceof TextView)) {
                throw new IllegalArgumentException("You must define a TextView with id = title in your item layout");
            }

			holder = new ViewHolder();
			holder.title = (TextView) titleView;

			convertView.setTag(holder);
		} else {
		    holder = (ViewHolder) convertView.getTag();
		}

        holder.title.setText("Item: " + position);

		return convertView;
	}

	class ViewHolder {
	    public TextView title;
	}
}
