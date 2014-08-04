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

import android.content.Context;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.tapchatapp.android.BuildConfig;

public class TapchatAnalytics {

    private static final String TRACKING_ID = "UA-32802473-1";
    
    private Tracker mTracker;

    public TapchatAnalytics(Context context) {
        GoogleAnalytics googleAnalytics = GoogleAnalytics.getInstance(context);
        mTracker = googleAnalytics.newTracker(TRACKING_ID);
    }

    public void trackScreenView(String screenName) {
        if (BuildConfig.DEBUG) {
            return;
        }
        mTracker.setScreenName(screenName);
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    public void trackEvent(String category, String action, String label, long value) {
        if (BuildConfig.DEBUG) {
            return;
        }
        mTracker.send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .setValue(value)
                .build());
    }
}
