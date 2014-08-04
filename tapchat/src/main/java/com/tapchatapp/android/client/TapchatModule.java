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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.SSLCertificateSocketFactory;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.otto.Bus;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.activity.AboutActivity;
import com.tapchatapp.android.app.activity.AddNetworkActivity;
import com.tapchatapp.android.app.activity.BuffersActivity;
import com.tapchatapp.android.app.activity.EditNetworkActivity;
import com.tapchatapp.android.app.activity.InvalidConnectionCertActivity;
import com.tapchatapp.android.app.activity.LoginActivity;
import com.tapchatapp.android.app.activity.MainActivity;
import com.tapchatapp.android.app.activity.MemberListActivity;
import com.tapchatapp.android.app.activity.NetworksActivity;
import com.tapchatapp.android.app.activity.PreferencesActivity;
import com.tapchatapp.android.app.activity.TapchatActivity;
import com.tapchatapp.android.app.activity.TapchatServiceActivity;
import com.tapchatapp.android.app.activity.TapchatServiceFragmentActivity;
import com.tapchatapp.android.app.activity.WelcomeActivity;
import com.tapchatapp.android.app.fragment.BufferFragment;
import com.tapchatapp.android.app.fragment.ChannelBufferFragment;
import com.tapchatapp.android.app.fragment.ConnectionFragment;
import com.tapchatapp.android.app.fragment.MainFragment;
import com.tapchatapp.android.app.fragment.MemberListFragment;
import com.tapchatapp.android.app.fragment.NetworksFragment;
import com.tapchatapp.android.app.fragment.QueryBufferFragment;
import com.tapchatapp.android.app.ui.BuffersPagerAdapter;
import com.tapchatapp.android.app.ui.ConnectionsPagerAdapter;
import com.tapchatapp.android.app.ui.TapchatServiceStatusBar;
import com.tapchatapp.android.client.message.Message;
import com.tapchatapp.android.network.PusherClient;
import com.tapchatapp.android.network.ssl.MemorizingTrustManager;
import com.tapchatapp.android.util.AndroidBus;

import java.io.IOException;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import dagger.Module;
import dagger.Provides;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;

@Module(
    injects = {
        AboutActivity.class,
        AddNetworkActivity.class,
        BufferFragment.class,
        BuffersActivity.class,
        BuffersPagerAdapter.class,
        ChannelBufferFragment.class,
        ConnectionFragment.class,
        ConnectionsPagerAdapter.class,
        EditNetworkActivity.class,
        InvalidConnectionCertActivity.class,
        LoginActivity.class,
        MainActivity.class,
        MainFragment.class,
        MemberListActivity.class,
        MemberListFragment.class,
        NetworksActivity.class,
        NetworksFragment.class,
        PreferencesActivity.class,
        PusherClient.class,
        QueryBufferFragment.class,
        TapchatActivity.class,
        TapchatApp.class,
        TapchatBouncerConnection.class,
        TapchatService.class,
        TapchatServiceActivity.class,
        TapchatServiceFragmentActivity.class,
        TapchatServiceStatusBar.class,
        WelcomeActivity.class
    }
)
public class TapchatModule {
    private static final String PUSH = "push";
    private static final long MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10 MiB

    private final Context mAppContext;

    public TapchatModule(Context appContext) {
        mAppContext = appContext;
    }

    @Provides @Singleton public TapchatAPI provideAPI(RestAdapter restAdapter) {
        return restAdapter.create(TapchatAPI.class);
    }

    @Provides @Singleton public Bus provideBus() {
        return new AndroidBus();
    }

    @Provides @Singleton public TapchatSession provideSession() {
        return new TapchatSession();
    }

    @Provides @Singleton public Gson provideGson() {
        return new GsonBuilder()
            .registerTypeAdapter(Message.class, new MessageDeserializer())
            .create();
    }

    @Provides @Singleton public OkHttpClient provideOkHttp(SSLSocketFactory sslSocketFactory) {
        try {
            OkHttpClient okHttpClient = new OkHttpClient();
            okHttpClient.setCache(new Cache(mAppContext.getCacheDir(), MAX_CACHE_SIZE));
            okHttpClient.setSslSocketFactory(sslSocketFactory);
            return okHttpClient;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Provides @Singleton public TapchatPushAPI providePushAPI(@Named(PUSH) RestAdapter restAdapter) {
        return restAdapter.create(TapchatPushAPI.class);
    }

    @Provides @Singleton @Named(PUSH) RestAdapter providePushRestAdapter(Gson gson, OkHttpClient okHttpClient) {
        return new RestAdapter.Builder()
            .setEndpoint("https://tapchat.herokuapp.com:443")
            .setConverter(new GsonConverter(gson, "UTF-8"))
            .setClient(new OkClient(okHttpClient))
            .build();
    }

    @Provides @Singleton public RestAdapter provideRestAdapter(Gson gson, OkHttpClient okHttpClient, TapchatSession session, TapchatRequestInterceptor requestInterceptor) {
        return new RestAdapter.Builder()
            .setEndpoint(session)
            .setConverter(new GsonConverter(gson, "UTF-8"))
            .setClient(new OkClient(okHttpClient))
            .setRequestInterceptor(requestInterceptor)
            .build();
    }

    @Provides @Singleton public SSLSocketFactory provideSslSocketFactory(TrustManager[] trustManagers) {
        try {
            SSLCertificateSocketFactory factory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(100);
            factory.setTrustManagers(trustManagers);
            return factory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides @Singleton public TrustManager[] provideTrustManagers() {
        return MemorizingTrustManager.getInstanceList(mAppContext);
    }

    @Provides @Singleton public TapchatAnalytics provideAnalytics() {
        return new TapchatAnalytics(mAppContext);
    }

    @Provides @Singleton public GoogleCloudMessaging provideGCM() {
        return GoogleCloudMessaging.getInstance(mAppContext);
    }

    @Provides @Singleton public PusherClient providePusherClient() {
        SharedPreferences prefs = TapchatApp.get().getPreferences();
        String pushId = prefs.getString(TapchatApp.PREF_PUSH_ID, null);
        String pushKey = prefs.getString(TapchatApp.PREF_PUSH_KEY, null);
        String gcmRegId = prefs.getString(TapchatApp.PREF_GCM_REG_ID, null);
        return new PusherClient(pushId, pushKey, gcmRegId);
    }
}
