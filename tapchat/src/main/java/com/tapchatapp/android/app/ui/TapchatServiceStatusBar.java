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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.WelcomeActivity;
import com.tapchatapp.android.app.event.ServiceErrorEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;

import org.apache.http.client.HttpResponseException;

import javax.inject.Inject;

public class TapchatServiceStatusBar implements View.OnClickListener {

    private Activity mActivity;
    private TapchatService mService;

    @Inject Bus mBus;

    public TapchatServiceStatusBar(Activity activity) {
        mActivity = activity;
        TapchatApp.get().inject(this);
    }

    public void registerBus() {
        mBus.register(this);

        View headerView = findHeaderView();
        if (headerView != null) {
            headerView.findViewById(R.id.reconnect_button).setOnClickListener(this);
        }
    }

    public void unregisterBus() {
        mBus.unregister(this);
    }

    @Override public void onClick(View v) {
        if (v.getId() == R.id.reconnect_button) {
            mService.connect();
        }
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        mService = event.getService();

        int connectionState = mService.getConnectionState();
        switch (connectionState) {
            case TapchatService.STATE_DISCONNECTED:
                showStatusText(R.string.tapchat_disconnected);
                break;
            case TapchatService.STATE_CONNECTING:
                showStatusText(R.string.tapchat_connecting);
                break;
            case TapchatService.STATE_CONNECTED:
                showStatusText(R.string.tapchat_connected);
                break;
            case TapchatService.STATE_LOADING:
                showStatusText(R.string.tapchat_syncing);
                break;
            case TapchatService.STATE_LOADED:
                if (mService.isServerOutdated()) {
                    showStatusText(R.string.server_outdated);
                } else {
                    hideHeader();
                }
                break;
        }

        final boolean isDisconnected = (connectionState == TapchatService.STATE_DISCONNECTED);
        final boolean isLoaded       = (connectionState == TapchatService.STATE_LOADED);
        View headerView = findHeaderView();
        if (headerView != null) {
            headerView.findViewById(R.id.reconnect_button).setVisibility(isDisconnected ? View.VISIBLE : View.GONE);
            headerView.findViewById(R.id.connecting_progress).setVisibility(isLoaded || isDisconnected ? View.GONE : View.VISIBLE);
        }
    }

    @Subscribe public void onServiceError(ServiceErrorEvent event) {
        final Exception error = event.getError();

        if (error instanceof HttpResponseException) {
            HttpResponseException httpError = (HttpResponseException) error;
            if (httpError.getStatusCode() == 403) {
                mService.logout();
                Toast.makeText(mActivity, R.string.unauthorized, Toast.LENGTH_LONG).show();
                mActivity.startActivity(new Intent(mActivity, WelcomeActivity.class));
                mActivity.finish();
                return;
            }
        }

        new AlertDialog.Builder(mActivity)
            .setTitle(R.string.error)
            .setMessage(error.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void showStatusText(int resId) {
        showStatusText(mActivity.getString(resId));
    }

    private void showStatusText(final String message) {
        View headerView = findHeaderView();
        if (headerView == null) {
            return;
        }

        TextView textView = (TextView) headerView.findViewById(R.id.status_text);
        textView.setText(message);

        headerView.setVisibility(View.VISIBLE);
    }

    private void hideHeader() {
        View headerView = findHeaderView();
        if (headerView != null) {
            headerView.setVisibility(View.GONE);
        }
    }

    private View findHeaderView() {
        return mActivity.findViewById(R.id.header);
    }
}
