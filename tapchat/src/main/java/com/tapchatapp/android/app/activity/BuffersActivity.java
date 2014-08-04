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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import android.view.View;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatAnalytics;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.event.BufferSelectedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.ui.BuffersPagerAdapter;
import com.tapchatapp.android.client.TapchatService;
import com.viewpagerindicator.TitlePageIndicator;

import javax.inject.Inject;

import static com.tapchatapp.android.app.ui.BuffersPagerAdapter.BuffersToDisplay;

public class BuffersActivity extends TapchatServiceFragmentActivity {

    @Inject Bus mBus;
    @Inject TapchatAnalytics mAnalytics;

    private long mConnectionId;

    private BuffersPagerAdapter mTabsAdapter;
    private BuffersPagerAdapter.BufferInfo mCurrentPage;

    private final BroadcastReceiver mPushReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long cid = Long.parseLong(intent.getStringExtra("cid"));
            long bid = Long.parseLong(intent.getStringExtra("bid"));
            if (cid == mConnectionId) {
                if (mCurrentPage != null && bid == mCurrentPage.getBufferId()) {
                    abortBroadcast();
                }
            }
        }
    };

    private final BroadcastReceiver mOpenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long cid = Long.parseLong(intent.getStringExtra("cid"));
            long bid = Long.parseLong(intent.getStringExtra("bid"));
            if (cid == mConnectionId) {
                int index = mTabsAdapter.findBufferIndex(bid);
                if (index >= 0) {
                    final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
                    viewPager.setCurrentItem(index);
                    abortBroadcast();
                }
            }
        }
    };

    private final ViewPager.OnPageChangeListener mPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override public void onPageSelected(int position) {
            if (position == -1) {
                return;
            }
            BuffersPagerAdapter.BufferInfo bufferInfo = mTabsAdapter.getBufferInfo(position);
            mCurrentPage = bufferInfo;

            invalidateOptionsMenu();
            getActionBar().setTitle(bufferInfo.getName());

            mBus.post(new BufferSelectedEvent(bufferInfo.getConnectionId(), bufferInfo.getBufferId(), true));
        }
        @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }
        @Override public void onPageScrollStateChanged(int state) { }
    };

    public static Intent createIntent(Context context, long cid, long bid) {
        Intent intent = new Intent(context, BuffersActivity.class);
        intent.setData(createUri(cid, bid));
        return intent;
    }

    public static Uri createUri(long cid, long bid) {
        return new Uri.Builder().scheme("tapchat")
            .authority(String.valueOf(cid))
            .path(String.valueOf(bid))
            .build();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getString(TapchatApp.PREF_THEME, "light").equals("dark")) {
            setTheme(R.style.TapchatDark);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buffers);

        mConnectionId = Long.parseLong(getIntent().getData().getHost());

        mAnalytics.trackScreenView("buffer");
    }

    @Override public void onStart() {
        super.onStart();

        IntentFilter pushFilter = new IntentFilter(TapchatApp.ACTION_MESSAGE_NOTIFY);
        pushFilter.setPriority(10);
        registerReceiver(mPushReceiver, pushFilter);

        IntentFilter openFilter = new IntentFilter(TapchatApp.ACTION_OPEN_BUFFER);
        openFilter.setPriority(10);
        registerReceiver(mOpenReceiver, openFilter);
    }

    @Override public void onStop() {
        super.onStop();
        unregisterReceiver(mPushReceiver);
        unregisterReceiver(mOpenReceiver);
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
        if (mTabsAdapter != null) {
            mTabsAdapter.registerBus();
        }
    }

    @Override public void onPause() {
        super.onPause();
        if (mCurrentPage != null) {
            mBus.post(new BufferSelectedEvent(mCurrentPage.getConnectionId(), mCurrentPage.getBufferId(), false));
        }
        mBus.unregister(this);
        if (mTabsAdapter != null) {
            mTabsAdapter.unregisterBus();
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            TapchatApp.goHome(this, mConnectionId);
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        final TapchatService service = event.getService();
        if (service.getConnectionState() != TapchatService.STATE_LOADED) {
            return;
        }

        if (mCurrentPage != null) {
            mBus.post(new BufferSelectedEvent(mCurrentPage.getConnectionId(), mCurrentPage.getBufferId(), true));
        }

        Uri data = getIntent().getData();
        final long             connectionId = Long.parseLong(data.getHost());
        final long             bufferId     = Long.parseLong(data.getPath().substring(1));
        final BuffersToDisplay display      = BuffersToDisplay.parseString(data.getQueryParameter("display"));

        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);

        mTabsAdapter = (BuffersPagerAdapter) viewPager.getAdapter();
        if (mTabsAdapter == null) {
            mTabsAdapter = new BuffersPagerAdapter(BuffersActivity.this, connectionId, display);
            mTabsAdapter.registerBus();
            viewPager.setAdapter(mTabsAdapter);

            final TitlePageIndicator tabs = (TitlePageIndicator) findViewById(R.id.pager_tabs);
            tabs.setViewPager(viewPager);
            tabs.setOnPageChangeListener(mPageChangeListener);

            if (display == BuffersToDisplay.ConsoleOnly) {
                tabs.setVisibility(View.GONE);
            }

            int selectedItem = mTabsAdapter.getBufferIndex(bufferId);
            if (selectedItem < 0) {
                // FIXME: What to do here? finish() ?
                return;
            }

            viewPager.setCurrentItem(selectedItem);

            // onPageSelected doesn't fire if selectedItem is already
            // selected, causing title to not display.
            mPageChangeListener.onPageSelected(selectedItem);
        }
    }

    @Override protected void loadFragments() {
        // Do nothing here, must wait for onServiceConnected()...
    }
}