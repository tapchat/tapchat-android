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

package com.tapchatapp.android.network;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatPushAPI;

import java.io.IOException;
import java.util.Arrays;

import javax.inject.Inject;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class PusherClient {
    private static final String TAG = "TapChatPusherClient";

    private final boolean mDebug;

    private String mPushId;
    private byte[] mPushKey;
    private String mRegId;
    private boolean mRequestSent;

    @Inject TapchatPushAPI mPushApi;
    @Inject GoogleCloudMessaging mGcm;

    public PusherClient(String id, String key, String gcmRegId) {
        TapchatApp.get().inject(this);

        mDebug = TapchatApp.get().getPreferences().getBoolean(TapchatApp.PREF_DEBUG, false);

        setTapchatPushInfo(id, key);
        mRegId = gcmRegId;
    }

    public void setTapchatPushInfo(String id, String key) {
        if (mDebug) Log.d(TAG, String.format("setTapchatPushInfo %s %s", id, key));
        mPushId  = id;
        mPushKey = (key != null) ? Base64.decode(key, Base64.URL_SAFE | Base64.NO_WRAP) : null;
        submitIfReady();
    }

    public void start() {
        if (mDebug) Log.d(TAG, "start()");
        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... unused) {
                try {
                    return mGcm.register(TapchatApp.GCM_SENDER_ID);
                } catch (IOException e) {
                    if (mDebug) Log.e(TAG, "GCM registration failed", e);
                    return null;
                }
            }

            @Override protected void onPostExecute(String regId) {
                if (mDebug) Log.d(TAG, String.format("Got GCM regId: %s", regId));

                SharedPreferences prefs = TapchatApp.get().getPreferences();
                SharedPreferences.Editor editor = prefs.edit();

                if (regId == null) {
                    editor.remove(TapchatApp.PREF_GCM_REG_ID);
                    editor.apply();
                    return;
                }

                if (regId.equals(prefs.getString(TapchatApp.PREF_GCM_REG_ID, null))) {
                    if (mDebug) Log.d(TAG, "GCM ID didn't change.");
                    return;
                }

                editor.putString(TapchatApp.PREF_GCM_REG_ID, regId);
                editor.apply();

                mRegId = regId;
                submitIfReady();
            }
        }.execute();
    }

    public void unregister() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    mGcm.unregister();

                    // FIXME: If mPushId is null...should we wait for that?
                    final String oldPushId = mPushId;
                    final String oldRegId  = mRegId;

                    mPushId  = null;
                    mPushKey = null;
                    mRegId   = null;

                    mRequestSent = false;

                    if (oldPushId != null && oldRegId != null) {
                        mPushApi.unregister(new TapchatPushAPI.Body(oldPushId, oldRegId), new Callback<TapchatPushAPI.Result>() {
                            @Override public void success(TapchatPushAPI.Result result, Response response) {
                                if (mDebug) Log.d(TAG, "Unregistered id: " + oldPushId + " reg: " + oldRegId);
                            }
                            @Override public void failure(RetrofitError e) {
                                if (mDebug) Log.e(TAG, "Failed to unregister id: " + oldPushId + " reg: " + oldRegId, e);
                            }
                        });
                    }
                } catch (Exception ex) {
                    Crashlytics.logException(ex);
                    Log.e(TAG, "Failed to unregister", ex);
                }
            }
        }).start();
    }

    public byte[] getPushKey() {
        return mPushKey;
    }

    private synchronized void submitIfReady() {
        if (mDebug) {
            Log.d(TAG, String.format("submitIfReady() sent: %s, id: %s, key: %s, regId: %s",
                    mRequestSent, mPushId, Arrays.toString(mPushKey), mRegId));
        }

        if (mPushId == null || mPushKey == null || mRegId == null) {
            // Not yet ready
            return;
        }

        if (mRequestSent) {
            return;
        }

        mRequestSent = true;

        mPushApi.register(new TapchatPushAPI.Body(mPushId, mRegId), new Callback<TapchatPushAPI.Result>() {
            @Override public void success(TapchatPushAPI.Result result, Response response) {
                if (mDebug) Log.d(TAG, "Registered id: " + mPushId + " reg: " + mRegId);
            }
            @Override public void failure(RetrofitError e) {
                if (mDebug) Log.e(TAG, "Failed to register id: " + mPushId + " reg: " + mRegId, e);
                mRequestSent = false;
            }
        });
    }
}
