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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.MenuItem;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.service.DummyServiceConnection;

import javax.inject.Inject;

public class PreferencesActivity extends PreferenceActivity {

    private final DummyServiceConnection mServiceConnection = new DummyServiceConnection();

    private String mTheme;

    @Inject TapchatAnalytics mAnalytics;

    @Override public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mTheme = prefs.getString(TapchatApp.PREF_THEME, "light");
        if (mTheme.equals("dark")) {
            setTheme(R.style.TapchatDark);
        }

        super.onCreate(savedInstanceState);

        TapchatApp.get().inject(this);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);

        PreferenceCategory category = new PreferenceCategory(this);
        category.setTitle(R.string.connection);
        screen.addPreference(category);

        String hostname = prefs.getString(TapchatApp.PREF_SERVER_HOST, null);
        int port = prefs.getInt(TapchatApp.PREF_SERVER_PORT, -1);

        Preference serverPref = new Preference(this);
        serverPref.setEnabled(false);
        serverPref.setTitle(String.format("%s:%s", hostname, port));
        serverPref.setSummary(R.string.hostname);
        category.addPreference(serverPref);

        Preference networksPref = new Preference(this);
        networksPref.setKey("networks");
        networksPref.setTitle(R.string.manage_irc_networks);
        category.addPreference(networksPref);

        Preference logoutPref = new Preference(this);
        logoutPref.setKey("logout");
        logoutPref.setTitle(R.string.logout);
        category.addPreference(logoutPref);

        category = new PreferenceCategory(this);
        category.setTitle(R.string.options);
        screen.addPreference(category);

        CheckBoxPreference notifyPref = new CheckBoxPreference(this);
        notifyPref.setTitle(R.string.notifications);
        if (TapchatApp.get().isIRCCloud()) {
            notifyPref.setChecked(false);
            notifyPref.setEnabled(false);
            notifyPref.setSummary(R.string.irccloud_no_notifications);
        } else {
            notifyPref.setDefaultValue(true);
            notifyPref.setKey(TapchatApp.PREF_NOTIFICATIONS);
            notifyPref.setSummary(R.string.notifications_desc);
        }
        category.addPreference(notifyPref);

        ListPreference themePref = new ListPreference(this);
        themePref.setKey(TapchatApp.PREF_THEME);
        themePref.setEntries(R.array.theme_names);
        themePref.setEntryValues(R.array.theme_values);
        themePref.setTitle(R.string.theme);
        themePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (!((String) newValue).equals(mTheme)) {
                    finish();
                    startActivity(new Intent(PreferencesActivity.this, PreferencesActivity.class));
                }
                return true;
            }
        });
        final int index = themePref.findIndexOfValue(prefs.getString(TapchatApp.PREF_THEME, "light"));
        if (index >= 0) {
            final String summary = (String) themePref.getEntries()[index];
            themePref.setSummary(summary);
        }

        category.addPreference(themePref);

        Preference showArchivedPref = new CheckBoxPreference(this);
        showArchivedPref.setKey(TapchatApp.PREF_SHOW_ARCHIVED);
        showArchivedPref.setTitle(R.string.show_archived);
        category.addPreference(showArchivedPref);

        CheckBoxPreference debugPref = new CheckBoxPreference(this);
        debugPref.setKey(TapchatApp.PREF_DEBUG);
        debugPref.setTitle(R.string.debugging);
        debugPref.setSummary(R.string.debugging_summary);
        category.addItemFromInflater(debugPref);

        category = new PreferenceCategory(this);
        category.setTitle(R.string.information);
        screen.addPreference(category);

        Preference aboutPref = new Preference(this);
        aboutPref.setTitle(R.string.about);
        aboutPref.setIntent(new Intent(this, AboutActivity.class));
        category.addPreference(aboutPref);

//        Preference feedbackPref = new Preference(this);
//        feedbackPref.setKey("feedback");
//        feedbackPref.setTitle(R.string.send_feedback);
//        category.addPreference(feedbackPref);

        setPreferenceScreen(screen);

        mAnalytics.trackScreenView("preferences");
    }

    @Override protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TapchatService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override protected void onStop() {
        super.onStop();
        unbindService(mServiceConnection);
    }

    @Override public void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!mTheme.equals(prefs.getString(TapchatApp.PREF_THEME, "light"))) {
            finish();
            startActivity(getIntent());
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();

        if (key != null && key.equals("networks")) {
            startActivity(new Intent(this, NetworksActivity.class));
            return true;

        } else if (key != null && key.equals("logout")) {
            new AlertDialog.Builder(PreferencesActivity.this)
                .setMessage(R.string.logout_text)
                .setPositiveButton(R.string.logout, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mServiceConnection.getService().logout();
                        startActivity(new Intent(PreferencesActivity.this, WelcomeActivity.class));
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
        }
        return false;
    }
}
