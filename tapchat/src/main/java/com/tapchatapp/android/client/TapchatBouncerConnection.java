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

import android.net.Uri;
import android.text.TextUtils;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.WebSocket;
import com.squareup.okhttp.WebSocketListener;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.message.Message;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import okio.Buffer;
import okio.BufferedSource;

public class TapchatBouncerConnection implements WebSocketListener {

    public static interface Callback {
        void onBouncerConnect();
        void onBouncerDisconnect();
        void onBouncerReceiveMessage(Message message);
        void onBouncerError(Exception ex);
    }

    @Inject Gson mGson;
    @Inject OkHttpClient mOkHttpClient;

    private Callback mCallback;
    private WebSocket mSocket;

    private final Executor mExecutor = Executors.newCachedThreadPool();

    public TapchatBouncerConnection(TapchatSession session, Callback callback) {
        TapchatApp.get().inject(this);

        mCallback = callback;

        boolean isSecure = session.getUri().getScheme().equals("https");
        Uri uri = session.getUri().buildUpon()
                .scheme(isSecure ? "wss" : "ws")
                .build();

        Request request = new Request.Builder()
                .url(uri.toString())
                .addHeader("Cookie", String.format("session=%s", session.getSessionId()))
                .build();

        mSocket = mOkHttpClient.newWebSocket(request);
    }

    public void start() {
        mExecutor.execute(new Runnable() {
            @Override public void run() {
                try {
                    Response response = mSocket.connect(TapchatBouncerConnection.this);
                    if (response.code() == 101) {
                        mCallback.onBouncerConnect();
                    } else {
                        String error = String.format("Failed to connect: %s %s", response.code(), response.message());
                        mCallback.onBouncerError(new Exception(error));
                    }
                } catch (IOException e) {
                    mCallback.onBouncerError(e);
                }
            }
        });
    }

    public void stop() {
        mExecutor.execute(new Runnable() {
            @Override public void run() {
                try {
                    mSocket.close(0, null);
                } catch (IOException e) {
                    mCallback.onBouncerError(e);

                }
            }
        });
    }

    public void send(final Message message) {
        mExecutor.execute(new Runnable() {
            @Override public void run() {
                Buffer payload = new Buffer().writeUtf8(mGson.toJson(message));
                try {
                    mSocket.sendMessage(WebSocket.PayloadType.TEXT, payload);
                } catch (IOException e) {
                    mCallback.onBouncerError(e);
                }
            }
        });
    }

    @Override public void onMessage(BufferedSource source, WebSocket.PayloadType payloadType) throws IOException {
        if (payloadType != WebSocket.PayloadType.TEXT) {
            return;
        }

        String message = source.readUtf8();
        source.close();

        if (TextUtils.isEmpty(message)) {
            return;
        }

        mCallback.onBouncerReceiveMessage(mGson.fromJson(message, Message.class));
    }

    @Override public void onClose(int code, String reason) {
        mCallback.onBouncerDisconnect();
    }

    @Override public void onFailure(IOException error) {
        mCallback.onBouncerError(error);
    }
}
