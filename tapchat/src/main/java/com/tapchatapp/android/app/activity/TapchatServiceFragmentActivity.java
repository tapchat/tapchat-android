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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatApp;

public abstract class TapchatServiceFragmentActivity extends TapchatServiceActivity {

    private String mTheme;

    @Override public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mTheme = prefs.getString(TapchatApp.PREF_THEME, "light");
        if (mTheme.equals("dark")) {
            setTheme(R.style.TapchatDark);
        }

        super.onCreate(savedInstanceState);
        onSetupFragments();
    }

    @Override public void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!mTheme.equals(prefs.getString(TapchatApp.PREF_THEME, "light"))) {
            finish();
            startActivity(getIntent());
        }
    }

    protected void onSetupFragments() {
        setContentView(R.layout.activity_single_fragment);
        loadFragments();
    }

    protected abstract void loadFragments();
}
