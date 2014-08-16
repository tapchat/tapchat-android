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
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.Spinner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.fragment.BufferFragment;
import com.tapchatapp.android.app.fragment.ConnectionFragment;
import com.tapchatapp.android.app.fragment.MainFragment;
import com.tapchatapp.android.app.ui.ConnectionsPagerAdapter;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.Connection;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class MainActivity extends TapchatServiceFragmentActivity {

    private static final Uri CONTRIBUTE_URI = Uri.parse("http://tapchatapp.com/contribute");

    public static final String EXTRA_SELECTED_CONNECTION = "com.tapchatapp.android.extra_seleted_connection";

    @Inject TapchatAnalytics mAnalytics;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!TapchatApp.get().isConfigured()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        getActionBar().setHomeButtonEnabled(false);

        mAnalytics.trackScreenView("main");
    }

    @Override public void onSetupFragments() {
        if (TapchatApp.get().isConfigured()) {
            super.onSetupFragments();
        }
    }

    @Override public void loadFragments() {
        setContentView(R.layout.activity_main);
        MainFragment fragment = (MainFragment) getFragmentManager().findFragmentByTag("main");
        if (fragment == null) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.content, new MainFragment(), "main");
            transaction.commit();
        }
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override public boolean onPrepareOptionsMenu(Menu menu) {
         boolean hasConnection = getConnection(getCurrentIndex()) != null;
         menu.findItem(R.id.join_channel).setEnabled((getService() != null) && hasConnection);
         menu.findItem(R.id.message_user).setEnabled((getService() != null) && hasConnection);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
         int currentIndex = getCurrentIndex();

         switch (item.getItemId()) {
             case R.id.join_channel: {
                 View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_join_channel, null);
                 Spinner serverSpinner = (Spinner) dialogView.findViewById(R.id.server);
                 serverSpinner.setAdapter(ServerSpinnerAdapter.create(this, getService()));
                 serverSpinner.setSelection(currentIndex);
                 new AlertDialog.Builder(this)
                     .setTitle(R.string.join_channel)
                     .setView(dialogView)
                     .setPositiveButton(R.string.join, new DialogInterface.OnClickListener() {
                         @Override
                         public void onClick(DialogInterface dialogInterface, int i) {
                             Spinner serverSpinner = (Spinner) ((AlertDialog) dialogInterface).findViewById(R.id.server);
                             Map serverItem = (Map) serverSpinner.getSelectedItem();

                             long connectionId = (Long) serverItem.get("id");
                             Connection connection = getService().getConnection(connectionId);

                             EditText channelEditText = (EditText) ((AlertDialog) dialogInterface).findViewById(R.id.channel);
                             connection.join(channelEditText.getText().toString(), null);
                         }

                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();
                 return true;
             }

             case R.id.message_user: {
                 View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_message_user, null);
                 Spinner serverSpinner = (Spinner) dialogView.findViewById(R.id.server);
                 serverSpinner.setAdapter(ServerSpinnerAdapter.create(this, getService()));
                 serverSpinner.setSelection(currentIndex);

                 new AlertDialog.Builder(this)
                     .setTitle(R.string.send_message)
                     .setView(dialogView)
                     .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                         @Override
                         public void onClick(DialogInterface dialogInterface, int i) {
                             Spinner serverSpinner = (Spinner) ((AlertDialog) dialogInterface).findViewById(R.id.server);
                             Map serverItem = (Map) serverSpinner.getSelectedItem();

                             long connectionId = (Long) serverItem.get("id");
                             Connection connection = getService().getConnection(connectionId);

                             EditText nickEditText = (EditText) ((AlertDialog) dialogInterface).findViewById(R.id.nick);
                             connection.say(nickEditText.getText().toString(), null, null);
                         }

                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();
                 return true;
             }

             case R.id.preferences:
                 startActivity(new Intent(this, PreferencesActivity.class));
                 return true;

             case R.id.contribute:
                 Intent intent = new Intent(Intent.ACTION_VIEW, CONTRIBUTE_URI);
                 intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 startActivity(intent);
                 return true;
         }
         return false;
     }

    public void showBuffer(int type, long connectionId, long bufferId, boolean isArchived) {
        boolean isTablet = (findViewById(R.id.content1) != null);

        if (isTablet) {
            Fragment fragment = BufferFragment.create(type, connectionId, bufferId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.content1, fragment, "buffer");
            transaction.commit();
        } else {
            Intent intent = new Intent(this, BuffersActivity.class);
            Uri data = Uri.parse(String.format("tapchat://%s/%s", connectionId, bufferId));
            if (isArchived) {
                data = data.buildUpon().appendQueryParameter("display", "archived").build();
            }
            intent.setData(data);
            startActivity(intent);
        }
    }

    private int getCurrentIndex() {
        ViewPager view = (ViewPager) findViewById(R.id.pager);
        if (view != null) {
            return view.getCurrentItem();
        } else {
            return -1;
        }
    }

    private Connection getConnection(int index) {
        Fragment fragment = getFragmentManager().findFragmentByTag("main");
        if (fragment == null) {
            return null;
        }

        ConnectionsPagerAdapter adapter = ((MainFragment) fragment).getTabsAdapter();

        if (index < 0 || adapter == null || index >= adapter.getCount()) {
            return null;
        }

        ConnectionsPagerAdapter.ConnectionInfo connectionInfo = adapter.getConnectionInfo(index);
        if (connectionInfo != null) {
            String currentId = String.valueOf(connectionInfo.getId());
            ConnectionFragment connectionFragment = (ConnectionFragment) this.getFragmentManager().findFragmentByTag(currentId);
            if (connectionFragment != null) {
                return connectionFragment.getConnection();
            }
        }
        return null;
    }

    private static class ServerSpinnerAdapter extends SimpleAdapter {
        public static ServerSpinnerAdapter create(Context context, TapchatService service) {
            List<Map<String,?>> servers = Lists.newArrayList();
            for (Connection connection : service.getConnections()) {
                Map<String, Object> serverInfo = Maps.newHashMap();
                serverInfo.put("name", connection.getDisplayName());
                serverInfo.put("id", connection.getId());
                servers.add(serverInfo);
            }
            return new ServerSpinnerAdapter(context, servers);
        }

        private ServerSpinnerAdapter(Context context, List<Map<String, ?>> data) {
            super(context, data, android.R.layout.simple_spinner_item,
                new String[] {
                    "name"
                },
                new int[] {
                    android.R.id.text1
                }
            );
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }
    }
}