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
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.ui.TapchatServiceStatusBar;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.service.DummyServiceConnection;

public abstract class TapchatServiceActivity extends TapchatActivity implements DummyServiceConnection.Listener {

    private boolean mIsChangingConfigurations;

    private DummyServiceConnection mServiceConnection;

    private final TapchatServiceStatusBar mStatusBar = new TapchatServiceStatusBar(this);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!(this instanceof MainActivity)) {
            setTitle(null);
            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override public void onStart() {
        super.onStart();
        mServiceConnection = (DummyServiceConnection) getLastNonConfigurationInstance();
        if (mServiceConnection == null) {
            mServiceConnection = new DummyServiceConnection();
            mServiceConnection.setListener(this);
            TapchatApp.get().bindService(new Intent(this, TapchatService.class), mServiceConnection, BIND_AUTO_CREATE);
        }

        mStatusBar.registerBus();
    }

    @Override public void onStop() {
        super.onStop();

        boolean isChangingConfigurations;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            isChangingConfigurations = isChangingConfigurations();
        } else {
            isChangingConfigurations = mIsChangingConfigurations;
        }

        if (mServiceConnection != null && (!isChangingConfigurations)) {
            TapchatApp.get().unbindService(mServiceConnection);
            mServiceConnection = null;
        }

        mStatusBar.unregisterBus();
    }

    @Override public Object onRetainNonConfigurationInstance() {
        mIsChangingConfigurations = true;
        return mServiceConnection;
    }

    @Override public void onServiceConnected(TapchatService service) {
    }

    @Override public void onServiceDisconnected(TapchatService service) {
        Toast.makeText(TapchatServiceActivity.this, "Service died", Toast.LENGTH_SHORT).show();
        mServiceConnection = null;
        finish();
    }

    protected TapchatService getService() {
        if (mServiceConnection != null) {
            return mServiceConnection.getService();
        }
        return null;
    }
}
