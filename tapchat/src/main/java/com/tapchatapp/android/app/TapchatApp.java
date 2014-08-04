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

package com.tapchatapp.android.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;

import com.codebutler.android_websockets.WebSocketClient;
import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Bus;
import com.tapchatapp.android.BuildConfig;
import com.tapchatapp.android.app.activity.MainActivity;
import com.tapchatapp.android.client.TapchatModule;
import com.tapchatapp.android.network.PusherClient;

import javax.inject.Inject;
import javax.net.ssl.TrustManager;

import dagger.ObjectGraph;

public class TapchatApp extends Application {

    public static final String PREF_SERVER_HOST         = "com.tapchatapp.android.pref_server_host";
    public static final String PREF_SERVER_PORT         = "com.tapchatapp.android.pref_server_port";
    public static final String PREF_SESSION_ID          = "com.tapchatapp.android.pref_server_pass";
    public static final String PREF_PUSH_KEY            = "com.tapchatapp.android.pref_push_key";
    public static final String PREF_PUSH_ID             = "com.tapchatapp.android.pref_push_id";
    public static final String PREF_GCM_REG_ID          = "com.tapchatapp.android.pref.gcm_reg_id";
    public static final String PREF_THEME               = "com.tapchatapp.android.pref_theme";
    public static final String PREF_SHOW_ARCHIVED       = "com.tapchatapp.android.pref_show_archived";
    public static final String PREF_NOTIFICATIONS       = "com.tapchatapp.android.pref_notifications";
    public static final String PREF_SELECTED_CONNECTION = "com.tapchat.android.pref_selected_connection";
    public static final String PREF_DEBUG               = "com.tapchatapp.android.pref_debug";

    public static final String ACTION_MESSAGE_NOTIFY       = "com.tapchatapp.android.ACTION_MESSAGE_NOTIFY";
    public static final String ACTION_NOTIFICATION_CLICKED = "com.tapchatapp.android.ACTION_NOTIFICATION_CLICKED";
    public static final String ACTION_OPEN_BUFFER          = "com.tapchatapp.android.ACTION_OPEN_BUFFER";
    public static final String ACTION_INVALID_CERT         = "com.tapchatapp.android.ACTION_INVALID_CERT";

    public static final String GCM_SENDER_ID = "263030918280";

    private static final String TAG = "TapchatApp";

    private static TapchatApp sInstance;

    @Inject Bus mBus;
    @Inject PusherClient mPusherClient;
    @Inject TrustManager[] mTrustManagers;

    private ObjectGraph mObjectGraph;
    private SharedPreferences mPreferences;

    public static TapchatApp get() {
        return sInstance;
    }

    @Override public void onCreate() {
        super.onCreate();
        sInstance = this;

        if (!BuildConfig.DEBUG) {
            Crashlytics.start(this);
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mObjectGraph = ObjectGraph.create(new TapchatModule(this));
        mObjectGraph.inject(this);

        mBus.register(this);

        if (mPreferences.getBoolean(PREF_DEBUG, false)) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
        }

        mPusherClient.start();

        WebSocketClient.setTrustManagers(mTrustManagers);
    }

    public static void goHome(Activity activity) {
        goHome(activity, -1);
    }

    public static void goHome(Activity activity, long selectedConnectionId) {
        Intent intent = new Intent(activity, MainActivity.class);
        if (selectedConnectionId > 0) {
            intent.putExtra(MainActivity.EXTRA_SELECTED_CONNECTION, selectedConnectionId);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    public SharedPreferences getPreferences() {
        return mPreferences;
    }

    public void inject(Object object) {
        mObjectGraph.inject(object);
    }

    public void setPushInfo(String id, String key) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();

        if (id != null && key != null) {
            Log.d(TAG, "Got push info! " + id);
            editor.putString(PREF_PUSH_ID,  id);
            editor.putString(PREF_PUSH_KEY, key);
            mPusherClient.setTapchatPushInfo(id, key);
        } else {
            editor.remove(PREF_PUSH_ID);
            editor.remove(PREF_PUSH_KEY);
            mPusherClient.unregister();
        }
        editor.apply();
    }

    public void setLoggedOut() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(TapchatApp.PREF_SERVER_HOST);
        editor.remove(TapchatApp.PREF_SERVER_PORT);
        editor.remove(TapchatApp.PREF_SESSION_ID);
        editor.remove(TapchatApp.PREF_PUSH_ID);
        editor.remove(TapchatApp.PREF_PUSH_KEY);
        editor.apply();

        mPusherClient.unregister();
    }

    public boolean isConfigured() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String host = prefs.getString(TapchatApp.PREF_SERVER_HOST, null);
        String sess = prefs.getString(TapchatApp.PREF_SESSION_ID, null);
        int port = prefs.getInt(TapchatApp.PREF_SERVER_PORT, -1);
        return (host != null && sess != null && port > 0);
    }

    public boolean isIRCCloud() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String host = prefs.getString(PREF_SERVER_HOST, null);
        return (host != null && (host.equals("irccloud.com")));
    }
}
