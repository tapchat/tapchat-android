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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.util.Log;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatApp;

public class MessagePushReceiver extends BroadcastReceiver {
    private static final String TAG = "MessagePushReceiver";

    @Override public void onReceive(Context context, Intent intent) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean notificationsEnabled = prefs.getBoolean(TapchatApp.PREF_NOTIFICATIONS, true);
            if (!notificationsEnabled) {
                return;
            }

            Intent broadcastIntent = new Intent(TapchatApp.ACTION_NOTIFICATION_CLICKED);
            broadcastIntent.putExtra("cid",  intent.getStringExtra("cid"));
            broadcastIntent.putExtra("bid",  intent.getStringExtra("bid"));
            PendingIntent contentIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            String text  = intent.getStringExtra("text");
            String title = intent.getStringExtra("title");

            Notification notification = new Notification.Builder(context)
                .setLargeIcon(((BitmapDrawable) context.getResources().getDrawable(R.drawable.app_icon)).getBitmap())
                .setSmallIcon(com.tapchatapp.android.R.drawable.app_icon_small)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .setLights(0xFF00FF00, 2000, 3000)
                .setContentTitle(title)
                .setContentText(text)
                .setTicker(text)
                .setContentIntent(contentIntent)
                .getNotification();

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(0, notification);

        } catch (Exception e) {
            Log.e(TAG, "Error handling message push", e);
        }
    }
}
