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

package com.tapchatapp.android.network.ssl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Base64;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.squareup.okhttp.internal.tls.OkHostnameVerifier;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class MemorizingHostnameVerifier implements HostnameVerifier {

    public static final String EXTRA_DECISION_ID = "com.tapchatapp.android.EXTRA_DECISION_ID";
    public static final String EXTRA_HOSTNAME = "com.tapchatapp.android.EXTRA_HOSTNAME";
    public static final String EXTRA_FINGERPRINT = "com.tapchatapp.android.EXTRA_FINGERPRINT";

    private static final String PREFS_FILENAME = "known_hosts";

    private static int sLastDecisionId = 0;

    private static final Map<Integer, Decision> sDecisions = new HashMap<>();

    private final Context mContext;
    private final SharedPreferences mPreferences;

    private final Handler mHandler = new Handler();
    private final Gson mGson = new Gson();

    public MemorizingHostnameVerifier(Context context, Bus bus) {
        mContext = context;
        mPreferences = mContext.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE);

        bus.register(this);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override public boolean verify(final String hostname, final SSLSession session) {
        if (OkHostnameVerifier.INSTANCE.verify(hostname, session)) {
            return true;
        }

        final byte[] encodedCertificate = CertUtil.getEncodedCertificate(session);
        final String fingerprint = CertUtil.certHash(encodedCertificate, CertUtil.SHA1);
        final String base64Certificate = Base64.encodeToString(encodedCertificate, Base64.DEFAULT);

        if (getKnownCertificates(hostname).contains(base64Certificate)) {
            return true;
        }

        final Decision decision = createDecision();

        mHandler.post(new Runnable() {
            @Override public void run() {
                Intent intent = new Intent(mContext, VerifyHostnameActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(EXTRA_DECISION_ID, decision.id);
                intent.putExtra(EXTRA_HOSTNAME, hostname);
                intent.putExtra(EXTRA_FINGERPRINT, fingerprint);
                mContext.startActivity(intent);
            }
        });

        try {
            synchronized (decision) {
                decision.wait();
            }
        } catch (InterruptedException ignored) { }

        if (decision.allow) {
            addKnownCertificate(hostname, base64Certificate);
        }

        return decision.allow;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Subscribe public void onHostnameVerifyDecisionEvent(HostnameVerifyDecisionEvent event) {
        final Decision decision = getDecision(event.getDecisionId());
        if (decision == null) {
            return;
        }
        synchronized (decision) {
            decision.allow = event.isDecisionAllow();
            decision.notify();
        }
    }

    private Set<String> getKnownCertificates(String hostname) {
        if (!mPreferences.contains(hostname)) {
            return new HashSet<>();
        }
        Type type = new TypeToken<Set<String>>() { }.getType();
        return mGson.fromJson(mPreferences.getString(hostname, null), type);
    }

    private void addKnownCertificate(String hostname, String base64Certificate) {
        Set<String> knownCertificates = new HashSet<>(getKnownCertificates(hostname));
        knownCertificates.add(base64Certificate);

        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(hostname, mGson.toJson(knownCertificates));
        editor.apply();
    }

    private static Decision createDecision() {
        synchronized (sDecisions) {
            Decision decision = new Decision(sLastDecisionId);
            sDecisions.put(sLastDecisionId, decision);
            sLastDecisionId++;
            return decision;
        }
    }

    private static Decision getDecision(int decisionId) {
        synchronized (sDecisions) {
            return sDecisions.remove(decisionId);
        }
    }

    private static class Decision {
        final int id;
        boolean allow;
        private Decision(int id) {
            this.id = id;
        }
    }
}
