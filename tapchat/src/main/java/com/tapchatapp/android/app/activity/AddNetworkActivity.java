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

import android.os.Bundle;
import android.util.Log;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.message.Message;
import com.tapchatapp.android.client.message.ResponseMessage;

import javax.inject.Inject;

public class AddNetworkActivity extends EditNetworkActivity {

    private static final String TAG = "AddNetworkActivity";

    @Inject TapchatAnalytics mAnalytics;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mButtonSave.setText(R.string.connect);

        mAnalytics.trackScreenView("add_network");

        setTitle(R.string.add_network);
    }

    @Override protected void onSubmit(String name, String hostname, String nickname, String port, String realname,
            boolean useSSL, String password) {

        getService().addServer(name, hostname, nickname, port, realname, useSSL, password, new TapchatService.PostCallback() {
            @Override
            public void run(final ResponseMessage response, Message request) {
                try {
                    dismissDialog(DIALOG_LOADING);

                    if (response.success) {
                        finish();
                        return;
                    }

                    showDialog(DIALOG_ERROR);

                } catch (Exception ex) {
                    Log.e(TAG, "Error adding network", ex);
                    showDialog(DIALOG_ERROR);
                }
            }
        });
    }
}
