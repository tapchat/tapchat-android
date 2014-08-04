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

package com.tapchatapp.android.app.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.AddNetworkActivity;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.ui.ConnectionsPagerAdapter;
import com.tapchatapp.android.app.TapchatApp;
import com.viewpagerindicator.TitlePageIndicator;

import javax.inject.Inject;

public class MainFragment extends Fragment {

    private int mPendingSelectItem = -1;

    private ConnectionsPagerAdapter mTabsAdapter;

    private ViewPager.OnPageChangeListener mPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override public void onPageSelected(int position) {
            if (getActivity() == null) {
                return;
            }

            getActivity().invalidateOptionsMenu();

            SharedPreferences.Editor editor = TapchatApp.get().getPreferences().edit();
            editor.putInt(TapchatApp.PREF_SELECTED_CONNECTION, position);
            editor.apply();
        }
        @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }
        @Override public void onPageScrollStateChanged(int state) { }
    };

    @Inject Bus mBus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TapchatApp.get().inject(this);

        setHasOptionsMenu(true);
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
       mBus.unregister(this);

       if (mTabsAdapter != null) {
           mTabsAdapter.unregisterBus();
       }
   }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, null);
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        getView().findViewById(R.id.add_network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), AddNetworkActivity.class));
            }
        });

        final ViewPager viewPager = (ViewPager) getView().findViewById(R.id.pager);

        mTabsAdapter = (ConnectionsPagerAdapter) viewPager.getAdapter();
        if (mTabsAdapter == null) {
            mTabsAdapter = new ConnectionsPagerAdapter(getActivity());
            mTabsAdapter.registerBus();

            viewPager.setAdapter(mTabsAdapter);

            final TitlePageIndicator tabs = (TitlePageIndicator) getView().findViewById(R.id.pager_tabs);
            tabs.setViewPager(viewPager);
            tabs.setOnPageChangeListener(mPageChangeListener);

            int selectedItem = TapchatApp.get().getPreferences().getInt(TapchatApp.PREF_SELECTED_CONNECTION, 0);
            if (viewPager.getChildCount() > selectedItem) {
                viewPager.setCurrentItem(selectedItem);
            } else {
                mPendingSelectItem = selectedItem;
            }
        }
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        mTabsAdapter.notifyDataSetChanged();

        final TitlePageIndicator tabs = (TitlePageIndicator) getView().findViewById(R.id.pager_tabs);
        tabs.notifyDataSetChanged();

        final ViewPager viewPager = (ViewPager) getView().findViewById(R.id.pager);

        View view = getView();
        if (view != null) {
            boolean showNoConnections = (mTabsAdapter.isLoaded() && mTabsAdapter.getCount() == 0);
            boolean showPager = mTabsAdapter.getCount() > 0;
            view.findViewById(R.id.no_connections).setVisibility(showNoConnections ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.pager).setVisibility(showPager ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.pager_tabs).setVisibility(showPager ? View.VISIBLE : View.GONE);
        }

        Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }

        // FIXME: Fix ViewPager to do this properly above.
        if (mPendingSelectItem >= 0 && mTabsAdapter.getCount() > mPendingSelectItem) {
            viewPager.setCurrentItem(mPendingSelectItem);
            mPendingSelectItem = -1;
        }
    }

    public ConnectionsPagerAdapter getTabsAdapter() {
        return mTabsAdapter;
    }
}
