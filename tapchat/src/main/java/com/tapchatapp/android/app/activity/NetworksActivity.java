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

import android.app.FragmentTransaction;
import android.os.Bundle;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.fragment.NetworksFragment;

import javax.inject.Inject;

public class NetworksActivity extends TapchatServiceFragmentActivity {

    @Inject TapchatAnalytics mAnalytics;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.irc_networks);

        mAnalytics.trackScreenView("networks");
    }

    @Override protected void loadFragments() {
        NetworksFragment fragment = (NetworksFragment) getFragmentManager().findFragmentByTag("networks");
        if (fragment == null) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.add(R.id.content, new NetworksFragment(), "networks");
            transaction.commit();
        }
    }
}