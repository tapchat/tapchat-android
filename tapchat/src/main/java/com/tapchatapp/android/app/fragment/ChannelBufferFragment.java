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

package com.tapchatapp.android.app.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.MemberListActivity;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferLineAddedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.client.model.ChannelBuffer;
import com.tapchatapp.android.client.model.Connection;
import com.tapchatapp.android.client.TapchatService;

public class ChannelBufferFragment extends BufferFragment {

    private ChannelBuffer mChannel;

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getView().findViewById(R.id.rejoin_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mChannel.join();
            }
        });
    }

    @Override public void onCreateOptionsMenu(android.view.Menu menu, android.view.MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.buffer_channel, menu);
    }

    @Override public void onPrepareOptionsMenu(android.view.Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean isJoined = (mChannel != null) && mChannel.isJoined();
        menu.findItem(R.id.info).setVisible(isJoined);
        menu.findItem(R.id.members).setVisible(isJoined);
        menu.findItem(R.id.part_channel).setVisible(isJoined);

        // User must part channel before archiving/deleting
        for (int id : new int[]{ R.id.archive, R.id.delete }) {
            MenuItem item = menu.findItem(id);
            item.setVisible(item.isVisible() && (!isJoined));
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.info:
                View infoView = getActivity().getLayoutInflater().inflate(R.layout.dialog_channel_info, null);
                TextView topicView = (TextView) infoView.findViewById(R.id.topic);
                topicView.setText(mChannel.getTopic());
                Linkify.addLinks(topicView, Linkify.WEB_URLS);
                // FIXME: modes, etc...
                new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.info_format, mChannel.getName()))
                    .setView(infoView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
                return true;

            case R.id.members:
                Intent intent = new Intent(getActivity(), MemberListActivity.class);
                intent.putExtra(ARG_CONNECTION_ID, getArguments().getLong(ARG_CONNECTION_ID));
                intent.putExtra(ARG_BUFFER_ID, getArguments().getLong(ARG_BUFFER_ID));
                startActivity(intent);
                return true;

            case R.id.part_channel:
                mChannel.part();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void updateUI() {
        super.updateUI();
        if (getView() == null) {
            // View not yet created.
            return;
        }

        boolean isConnected = (mConnection != null && mConnection.getState() == Connection.STATE_CONNECTED);
        boolean isJoined    = (mChannel != null && mChannel.isJoined());

        View notInChannelView = getView().findViewById(R.id.not_in_channel);
        notInChannelView.setVisibility((!isConnected) || isJoined ? View.GONE : View.VISIBLE);

        getActivity().invalidateOptionsMenu();
    }

    @Subscribe @Override public void onConnectionChanged(ConnectionChangedEvent event) {
        super.onConnectionChanged(event);
    }

    @Subscribe @Override public void onBufferChanged(BufferChangedEvent event) {
        super.onBufferChanged(event);
    }

    @Subscribe @Override public void onBufferLineAdded(BufferLineAddedEvent event) {
        super.onBufferLineAdded(event);
    }

    @Subscribe @Override public void onBufferRemoved(BufferRemovedEvent event) {
        super.onBufferRemoved(event);
    }

    @Subscribe @Override public void onServiceStateChanged(ServiceStateChangedEvent event) {
        super.onServiceStateChanged(event);

        TapchatService service = event.getService();

        if (service.getConnectionState() == TapchatService.STATE_LOADED) {
            mChannel = (ChannelBuffer) mBuffer;
        } else {
            mChannel = null;
        }

        updateUI();
    }
}
