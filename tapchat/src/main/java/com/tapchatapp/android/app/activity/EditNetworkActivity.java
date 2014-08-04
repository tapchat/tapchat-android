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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.message.Message;
import com.tapchatapp.android.client.message.ResponseMessage;
import com.tapchatapp.android.client.model.Connection;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnTextChanged;

import static android.text.TextUtils.isEmpty;
import static com.tapchatapp.android.util.FieldValidator.validateFields;

public class EditNetworkActivity extends TapchatServiceActivity {

    public static final String EXTRA_CID = "com.tapchatapp.android.EXTRA_CID";

    private static final String TAG = "EditNetworkActivity";
    private static final String DEFAULT_PORT = "6667";

    protected static final int DIALOG_LOADING = 1;
    protected static final int DIALOG_ERROR = 2;

    private long mConnectionId = -1;

    private Connection mConnection;

    @Inject Bus mBus;
    @Inject TapchatAnalytics mAnalytics;

    @InjectView(R.id.save) Button mButtonSave;
    @InjectView(R.id.hostname) TextView mTextViewHostname;
    @InjectView(R.id.network_name) TextView mTextViewNetworkName;
    @InjectView(R.id.nick) TextView mTextViewNick;
    @InjectView(R.id.port) TextView mTextViewPort;
    @InjectView(R.id.real_name) TextView mTextViewRealName;
    @InjectView(R.id.server_password) TextView mTextViewServerPassword;
    @InjectView(R.id.use_ssl) CheckBox mCheckBoxUseSSL;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_edit_network);
        ButterKnife.inject(this);

        setTitle(R.string.edit_network);

        if (getIntent().hasExtra(EXTRA_CID)) {
            mConnectionId = getIntent().getLongExtra(EXTRA_CID, -1);
        }

        onCheckedChangedUseSSL(false);

        mAnalytics.trackScreenView("edit_network");
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
    }

    @Override public void onPause() {
        super.onPause();
        mBus.unregister(this);
    }

    @Override public Dialog onCreateDialog(int id) {
        if (id == DIALOG_LOADING) {
            ProgressDialog loadingDialog = new ProgressDialog(EditNetworkActivity.this);
            loadingDialog.setCancelable(false);
            loadingDialog.setIndeterminate(true);
            loadingDialog.setMessage(getString(R.string.loading));
            return loadingDialog;
        } else if (id == DIALOG_ERROR) {
            return new AlertDialog.Builder(this)
                .setMessage(R.string.error)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        }
        return null;
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        TapchatService service = event.getService();
        if (service.getConnectionState() != TapchatService.STATE_LOADED) {
            return;
        }

        mConnection = service.getConnection(mConnectionId);
        if (mConnection == null) {
            finish();
            return;
        }

        mTextViewNetworkName.setText(mConnection.getName());
        mTextViewHostname.setText(mConnection.getHostName());
        mTextViewPort.setText(String.valueOf(mConnection.getPort()));
        mTextViewNick.setText(mConnection.getNick());
        mTextViewRealName.setText(mConnection.getRealName());
        mCheckBoxUseSSL.setChecked(mConnection.isSSL());
        mTextViewServerPassword.setText(mConnection.getPassword());
    }

    @OnCheckedChanged(R.id.use_ssl) public void onCheckedChangedUseSSL(boolean checked) {
        if (checked) {
            mTextViewPort.setHint(R.string.port_hint);
        } else {
            mTextViewPort.setHint(DEFAULT_PORT);
        }
    }

    @OnTextChanged(R.id.hostname) public void onTextChangedHostname() {
        String hostname = mTextViewHostname.getText().toString();
        String port = mTextViewPort.getText().toString();
        if (!TextUtils.isEmpty(hostname)) {
            if (!TextUtils.isEmpty(port) && !port.equals(DEFAULT_PORT)) {
                mTextViewNetworkName.setHint(String.format("%s:%s", hostname, port));
            } else {
                mTextViewNetworkName.setHint(hostname);
            }
        } else {
            mTextViewNetworkName.setHint(null);
        }
    }

    @OnTextChanged(R.id.port) public void onTextChangedPort() {
        onTextChangedHostname();
    }

    @OnClick(R.id.save) public void onClickSave() {
        if (!validateFields(EditNetworkActivity.this, R.id.hostname, R.id.port, R.id.nick, R.id.real_name)) {
            return;
        }

        String name = mTextViewNetworkName.getText().toString();
        String hostname = mTextViewHostname.getText().toString();
        String port = mTextViewPort.getText().toString();
        String nickname = mTextViewNick.getText().toString();
        String realname = mTextViewRealName.getText().toString();
        boolean useSSL  = mCheckBoxUseSSL.isChecked();
        String  password = mTextViewServerPassword.getText().toString();

        if (TextUtils.isEmpty(name)) {
            name = mTextViewNetworkName.getHint().toString();
        }

        if (isEmpty(port)) {
            port = DEFAULT_PORT;
        }

        showDialog(DIALOG_LOADING);
        onSubmit(name, hostname, nickname, port, realname, useSSL, password);
    }

    @OnClick(R.id.cancel) public void onClickCancel() {
        finish();
    }

    protected void onSubmit(String name, String hostname, String nickname, String port, String realname, boolean useSSL,
            String password) {

        mConnection.edit(name, hostname, nickname, port, realname, useSSL, password, new TapchatService.PostCallback() {
            @Override
            public void run(ResponseMessage response, Message request) {
                try {
                    Log.d(TAG, "Got response: " + response);

                    dismissDialog(DIALOG_LOADING);

                    if (response.success) {
                        finish();
                        return;
                    }

                    showDialog(DIALOG_ERROR);

                } catch (Exception ex) {
                    Log.d(TAG, "Error adding network", ex);
                    showDialog(DIALOG_ERROR);
                }
            }
        });
    }
}
