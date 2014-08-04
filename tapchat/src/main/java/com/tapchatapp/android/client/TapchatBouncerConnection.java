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

package com.tapchatapp.android.client;

import android.text.TextUtils;
import android.util.Log;

import com.codebutler.android_websockets.WebSocketClient;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.message.Message;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;

public class TapchatBouncerConnection implements WebSocketClient.Listener {

    private static final String TAG = "TapchatBouncerConnection";

    public static interface Callback {
        void onBouncerConnect();
        void onBouncerDisconnect();
        void onBouncerReceiveMessage(Message message);
        void onBouncerError(Exception ex);
    }

    @Inject Gson mGson;

    private Callback mCallback;
    private WebSocketClient mClient;

    public TapchatBouncerConnection(TapchatSession session, Callback callback) {
        TapchatApp.get().inject(this);

        mCallback = callback;
        try {
            URI uri = new URI("wss", null, session.getUri().getHost(), session.getUri().getPort(), null, null, null);
            mClient = new WebSocketClient(uri, this, ImmutableList.of(
                new BasicNameValuePair("Cookie", String.format("session=%s", session.getSessionId()))
            ));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        mClient.connect();
    }

    public void stop() {
        mClient.disconnect();
    }

    public void send(Message message) {
        mClient.send(mGson.toJson(message));
    }

    @Override public void onConnect() {
        mCallback.onBouncerConnect();
    }

    @Override public void onMessage(String message) {
        if (!TextUtils.isEmpty(message)) {
            mCallback.onBouncerReceiveMessage(mGson.fromJson(message, Message.class));
        } else {
            Log.d(TAG, "Got an empty message?");
        }
    }

    @Override public void onMessage(byte[] data) {}

    @Override public void onDisconnect(int code, String reason) {
        mCallback.onBouncerDisconnect();
    }

    @Override public void onError(Exception error) {
        mCallback.onBouncerError(error);
    }
}
