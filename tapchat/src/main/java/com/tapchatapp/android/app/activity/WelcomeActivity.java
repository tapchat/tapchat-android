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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.TapchatApp;

import javax.inject.Inject;

public class WelcomeActivity extends TapchatActivity {

    private static final int REQUEST_LOGIN = 1;

    @Inject TapchatAnalytics mAnalytics;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        mAnalytics.trackScreenView("welcome");
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            TapchatApp.goHome(this);
        }
    }

    public void onLoginClick(View view) {
        startActivityForResult(new Intent(this, LoginActivity.class), REQUEST_LOGIN);
    }
}