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

package com.tapchatapp.android.app.ui;

import android.app.Fragment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.model.Connection;
import com.tapchatapp.android.client.TapchatService;

public class ConnectionStatusBar {
    private static final String TAG = "FragmentConnectionListener";

    private Fragment mFragment;
    private Connection mConnection;
    private long mConnectionId;

    private View.OnClickListener mReconnectListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mConnection.reconnect();
        }
    };

    public ConnectionStatusBar(Fragment fragment, long connectionId) {
        mFragment = fragment;
        mConnectionId = connectionId;
    }

    public void registerBus(Bus bus) {
        bus.register(this);
    }

    public void unregisterBus(Bus bus) {
        bus.unregister(this);
    }

    public void destroy() {
        mFragment = null;
    }

    @Subscribe
    public void onServiceStateChanged(ServiceStateChangedEvent event) {
        if (mConnection == null && event.getService().getConnectionState() == TapchatService.STATE_LOADED) {
            mConnection = event.getService().getConnection(mConnectionId);
            if (mConnection == null) {
                return; // FIXME: Throw error?
            }
            updateUI();
        }
    }

    @Subscribe
    public void onConnectionChanged(ConnectionChangedEvent event) {
        final Connection connection = event.getConnection();
        if (connection.getId() == mConnectionId) {
            updateUI();
        }
    }

    private void updateUI() {
        if (mFragment == null) {
            Log.e(TAG, "mfragment null");
            return;
        }
        if (mFragment.getView() == null) {
            // Fragment is being removed
            Log.e(TAG, "mfragment view null");
            return;
        }
        if (mConnection == null) {
            return;
        }

        View        header             = mFragment.getView().findViewById(R.id.connection_header);
        TextView    statusTextView     = (TextView)    header.findViewById(R.id.status_text);
        ProgressBar connectingProgress = (ProgressBar) header.findViewById(R.id.connecting_progress);
        Button      reconnectButton    = (Button)      header.findViewById(R.id.reconnect_button);

        if (!reconnectButton.isClickable()) {
            reconnectButton.setOnClickListener(mReconnectListener);
        }

        statusTextView.setText(mConnection.getDisplayState(TapchatApp.get()));

        boolean isConnected    = (mConnection.getState() == Connection.STATE_CONNECTED);
        boolean isConnecting   = (mConnection.getState() != Connection.STATE_DISCONNECTED);
        boolean isDisconnected = (mConnection.getState() == Connection.STATE_DISCONNECTED);

        connectingProgress.setVisibility(isConnecting ? View.VISIBLE : View.GONE);
        header.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        reconnectButton.setVisibility(isDisconnected ? View.VISIBLE : View.GONE);
    }
}
