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

import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.fragment.BufferFragment;
import com.tapchatapp.android.app.fragment.MemberListFragment;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.ChannelBuffer;

import javax.inject.Inject;

public class MemberListActivity extends TapchatServiceFragmentActivity {

    @Inject Bus mBus;
    @Inject TapchatAnalytics mAnalytics;

    private ChannelBuffer mChannel;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_single_fragment);

        setTitle(null);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mAnalytics.trackScreenView("member_list");
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        long connectionId = getIntent().getLongExtra(BufferFragment.ARG_CONNECTION_ID, -1);
        long bufferId     = getIntent().getLongExtra(BufferFragment.ARG_BUFFER_ID, -1);

        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, BuffersActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setData(Uri.parse(String.format("tapchat://%s/%s", connectionId, bufferId)));
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
    }

    @Override protected void onPause() {
        super.onPause();
        mBus.unregister(this);
    }

    @Override protected void loadFragments() {
        // Has to happen in onServiceConnected()...
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        TapchatService service = event.getService();
        if (service.getConnectionState() != TapchatService.STATE_LOADED) {
            return;
        }

        long connectionId = getIntent().getLongExtra(BufferFragment.ARG_CONNECTION_ID, -1);
        long bufferId = getIntent().getLongExtra(BufferFragment.ARG_BUFFER_ID, -1);

        mChannel = (ChannelBuffer) service.getConnection(connectionId).getBuffer(bufferId);

        setTitle(getString(R.string.members_title_format, mChannel.getDisplayName()));

        Bundle args = new Bundle();
        args.putLong(BufferFragment.ARG_CONNECTION_ID, mChannel.getConnection().getId());
        args.putLong(BufferFragment.ARG_BUFFER_ID, mChannel.getId());

        MemberListFragment fragment = (MemberListFragment) getFragmentManager().findFragmentByTag("members");
        if (fragment == null) {
            fragment = new MemberListFragment();
            fragment.setArguments(args);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.add(R.id.content, fragment, "members");
            transaction.commit();
        }
    }
}