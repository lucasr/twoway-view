/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2012 Lucas Rocha
 * Copyright (C) 2013 Evelio Tarazona Cáceres
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

import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends ListActivity {
    private static final String SAMPLE_CATEGORY = "org.lucasr.twowayview.sample.SAMPLE";
    private static final String KEY_LABEL = "org.lucasr.twowayview.key.LABEL";
    private static final String KEY_INTENT = "org.lucasr.twowayview.key.INTENT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setListAdapter(new SimpleAdapter(this, getItems(),
                android.R.layout.simple_list_item_1, new String[]{KEY_LABEL},
                new int[]{android.R.id.text1}));
    }

    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        Map<String, Object> item = (Map<String, Object>) listView.getItemAtPosition(position);
        Intent intent = (Intent) item.get(KEY_INTENT);
        startActivity(intent);
    }

    public List<? extends Map<String, ?>> getItems() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(SAMPLE_CATEGORY);

        final PackageManager pm = getPackageManager();
        List<ResolveInfo> resolvedActivities = pm.queryIntentActivities(mainIntent, 0);

        if (resolvedActivities == null || resolvedActivities.size() < 1) {
            return result;
        }

        for (ResolveInfo info : resolvedActivities) {
            CharSequence loadedLabel = info.loadLabel(pm);
            String label = loadedLabel != null ? loadedLabel.toString() : info.activityInfo.name;
            result.add(
                    buildItem(label,
                            buildIntent(info.activityInfo.applicationInfo.packageName, info.activityInfo.name)
                    )
            );
        }

        return result;
    }

    private Intent buildIntent(String packageName, String name) {
        Intent intent = new Intent();
        intent.setClassName(packageName, name);
        return intent;
    }

    private static final Map<String, Object> buildItem(String label, Intent intent) {
        Map<String, Object> item = new HashMap<String, Object>(2);
        item.put(KEY_LABEL, label);
        item.put(KEY_INTENT, intent);
        return item;
    }
}
