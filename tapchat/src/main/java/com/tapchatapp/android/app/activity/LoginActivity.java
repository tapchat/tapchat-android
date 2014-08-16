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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.PasswordTransformationMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatAPI;
import com.tapchatapp.android.client.TapchatSession;
import com.tapchatapp.android.util.FieldValidator;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class LoginActivity extends TapchatActivity implements CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {

    private static final int DEFAULT_PORT = 8067;
    private static final ImmutableList<String> SERVERS = ImmutableList.of("irccloud.com:443");

    @Inject TapchatAPI mAPI;
    @Inject TapchatAnalytics mAnalytics;
    @Inject TapchatSession mSession;

    @InjectView(R.id.login) Button mLoginButton;
    @InjectView(R.id.username) EditText mUsernameEditText;
    @InjectView(R.id.password) EditText mPasswordEditText;
    @InjectView(R.id.server) AutoCompleteTextView mServerEditText;
    @InjectView(R.id.show_password) CheckBox mShowPasswordCheckBox;
    @InjectView(R.id.tapchat_server_instructions) TextView mInstructionsTextView;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_login);
        ButterKnife.inject(this);

        mShowPasswordCheckBox.setOnCheckedChangeListener(this);
        mInstructionsTextView.setText(Html.fromHtml(getString(R.string.tapchat_server_instructions)));
        mLoginButton.setOnClickListener(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, SERVERS);
        mServerEditText.setAdapter(adapter);
    }

    @Override public void onClick(View view) {
        if (view == mLoginButton) {
            if (!FieldValidator.validateFields(this, R.id.server, R.id.username, R.id.password)) {
                return;
            }

            final String email = mUsernameEditText.getText().toString();
            final String password = mPasswordEditText.getText().toString();

            final Uri baseUri = getBaseUri();
            mSession.setUri(baseUri);

            mAPI.login(new TapchatAPI.LoginBody(email, password), new Callback<TapchatAPI.LoginResult>() {
                @Override public void success(TapchatAPI.LoginResult loginResult, Response response) {
                    String sessionId = loginResult.session;

                    SharedPreferences.Editor editor = TapchatApp.get().getPreferences().edit();
                    editor.putString(TapchatApp.PREF_SERVER_HOST, baseUri.getHost());
                    editor.putInt(TapchatApp.PREF_SERVER_PORT, baseUri.getPort());
                    editor.putString(TapchatApp.PREF_SESSION_ID, sessionId);
                    editor.apply();

                    mAnalytics.trackEvent("Setup", "Login", "TapChat", 1L);

                    setResult(RESULT_OK);
                    finish();
                }
                @Override public void failure(RetrofitError ex) {
                    if (isFinishing()) {
                        return;
                    }
                    new AlertDialog.Builder(LoginActivity.this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.unauthorized)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
            });
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mShowPasswordCheckBox) {
            mPasswordEditText.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
        }
    }

    private Uri getBaseUri() {
        String hostname = mServerEditText.getText().toString();
        Uri uri = Uri.parse(String.format("https://%s/chat/", hostname));
        if (uri.getPort() <= 0) {
            uri = Uri.parse(String.format("https://%s:%s/chat/", hostname, DEFAULT_PORT));
        }
        return uri;
    }
}
