/*
 * Copyright (C) 2014 Eric Butler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tapchatapp.android.app.activity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class AboutActivity extends TapchatServiceActivity {

    private static final String WEBSITE = "http://tapchatapp.com";

    @Inject TapchatAnalytics mAnalytics;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setTitle(R.string.about);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_about);

        PackageInfo info = getPackageInfo();
        String version = String.format("%s (Build %s)", info.versionName, info.versionCode);

        List<Map<String,?>> data = Lists.newArrayList();

        Map<String, String> item = Maps.newHashMap();
        item.put("text1", WEBSITE);
        item.put("text2", "Open website");
        data.add(item);

        item = Maps.newHashMap();
        item.put("text1", version);
        item.put("text2", getString(R.string.version));
        data.add(item);

        ListView list = (ListView) findViewById(android.R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE)));
                }
            }
        });
        list.setAdapter(new SimpleAdapter(this, data,
            android.R.layout.simple_list_item_2,
            new String[] { "text1", "text2" },
            new int[] { android.R.id.text1, android.R.id.text2 }) {
            @Override
            public boolean isEnabled(int position) {
                return (position == 0);
            }
        });

        WebView webView = (WebView) findViewById(R.id.webview);
        webView.loadUrl("file:///android_res/raw/licenses.html");

        mAnalytics.trackScreenView("about");
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    private PackageInfo getPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}