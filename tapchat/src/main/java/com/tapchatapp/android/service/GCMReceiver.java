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

package com.tapchatapp.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.network.PusherClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

public class GCMReceiver extends BroadcastReceiver {

    private static final String TAG = "TapChatGCMReceiver";

    @Inject PusherClient mPusherClient;
    @Inject GoogleCloudMessaging mGCM;

    public GCMReceiver() {
        TapchatApp.get().inject(this);
    }

    @Override public void onReceive(Context context, Intent intent) {
        String messageType = mGCM.getMessageType(intent);
        if (!messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE)) {
            return;
        }

        try {
            byte[] cipherText = Base64.decode(intent.getStringExtra("payload"), Base64.URL_SAFE | Base64.NO_WRAP);
            byte[] iv = Base64.decode(intent.getStringExtra("iv"), Base64.URL_SAFE | Base64.NO_WRAP);

            byte[] key = mPusherClient.getPushKey();
            if (key == null) {
                // I don't think this will ever happen
                throw new Exception("Received push notification before receiving decryption key.");
            }

            JSONObject message = new JSONObject(new String(decrypt(cipherText, key, iv), "UTF-8"));

            Intent broadcastIntent = new Intent(TapchatApp.ACTION_MESSAGE_NOTIFY);
            addExtras(broadcastIntent, message);
            context.sendOrderedBroadcast(broadcastIntent, null);
        } catch (Exception ex) {
            Log.e(TAG, "Error parsing push notification", ex);
        }
    }

    private void addExtras(Intent intent, JSONObject message) throws JSONException {
        Iterator it = message.keys();
        while (it.hasNext()) {
            String key = (String) it.next();
            intent.putExtra(key, message.get(key).toString());
        }
    }

    private byte[] decrypt(byte[] cipherText, byte[] key, byte[] iv) throws Exception {
        SecretKey keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return aes.doFinal(cipherText);
    }
}
