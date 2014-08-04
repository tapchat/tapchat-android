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

package com.tapchatapp.android.app.ui;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.app.event.ConnectionAddedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ConnectionRemovedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.fragment.ConnectionFragment;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.Connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.inject.Inject;

public class ConnectionsPagerAdapter extends TapchatFragmentStatePagerAdapter {

    private static final String TAG = "ConnectionsPagerAdapter";

    private static final String KEY_CONNECTIONS = "com.tapchatapp.android.key_connections";

    private final Activity mActivity;

    private final Object mLock = new Object();

    private ArrayList<ConnectionInfo> mConnections = new ArrayList<>();

    private int mServiceState;
    private boolean mIsLoaded;

    @Inject Bus mBus;

    public ConnectionsPagerAdapter(Activity activity) {
        super(activity.getFragmentManager());
        TapchatApp.get().inject(this);

        mActivity = activity;
    }

    public void registerBus() {
        mBus.register(this);
    }

    public void unregisterBus() {
        mBus.unregister(this);
    }

    public boolean isLoaded() {
        return mIsLoaded;
    }

    @Override public Parcelable saveState() {
        Bundle bundle = (Bundle) super.saveState();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putParcelableArrayList(KEY_CONNECTIONS, mConnections);
        return bundle;
    }

    @Override public void restoreState(Parcelable state, ClassLoader loader) {
        super.restoreState(state, loader);
        Bundle bundle = (Bundle) state;
        mConnections = bundle.getParcelableArrayList(KEY_CONNECTIONS);
        notifyDataSetChanged();
    }

    @Override public int getCount() {
        return mConnections.size();
    }

    @Override public String getPageTitle(int position) {
        return mConnections.get(position).mName.toUpperCase();
    }

    @Override public Fragment getItem(int position) {
        ConnectionInfo info = mConnections.get(position);

        if (mActivity.getFragmentManager().findFragmentByTag(String.valueOf(info.mId)) != null) {
            throw new RuntimeException("eeeeek");
        }

        Bundle args = new Bundle();
        args.putLong(ConnectionFragment.ARG_CONNECTION_ID, info.mId);

        return Fragment.instantiate(mActivity, ConnectionFragment.class.getName(), args);
    }

    @Override public int getItemPosition(Object object) {
        ConnectionFragment fragment = (ConnectionFragment) object;
        int index = findConnectionIndex(fragment.getConnectionId());
        if (index >= 0) {
            return index;
        } else {
            return POSITION_NONE;
        }
    }

    @Override public String getTag(int position) {
        return String.valueOf(mConnections.get(position).getId());
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        final TapchatService service = event.getService();

        mServiceState = service.getConnectionState();

        if (mServiceState != TapchatService.STATE_LOADED) {
            postNotifyDataSetChanged();
            return;
        }

        ArrayList<ConnectionInfo> connections = new ArrayList<>();
        for (Connection connection : service.getConnections()) {
            connections.add(ConnectionInfo.forConnection(connection));
        }

        Collections.sort(connections, ConnectionInfo.COMPARATOR);
        mConnections = connections;
        postNotifyDataSetChanged();
    }

    @Subscribe public void onConnectionAdded(ConnectionAddedEvent event) {
        final Connection connection = event.getConnection();

        if (mServiceState != TapchatService.STATE_LOADED) {
            return;
        }

        synchronized (mLock) {
            if (findConnection(connection.getId()) != null) {
                return;
            }

            ArrayList<ConnectionInfo> connections = new ArrayList<>(mConnections);
            connections.add(ConnectionInfo.forConnection(connection));
            Collections.sort(connections, ConnectionInfo.COMPARATOR);

            mConnections = connections;
            postNotifyDataSetChanged();
        }
    }

    @Subscribe public void onConnectionChanged(ConnectionChangedEvent event) {
        final Connection connection = event.getConnection();
        updateConnnection(connection);
    }

    @Subscribe public void onConnectionRemoved(ConnectionRemovedEvent event) {
        final Connection connection = event.getConnection();
        removeConnnection(connection);
    }

    public ConnectionInfo getConnectionInfo(int position) {
        return mConnections.get(position);
    }

    private ConnectionInfo findConnection(long connectionId) {
        for (ConnectionInfo info : mConnections) {
            if (info.getId() == connectionId) {
                return info;
            }
        }
        return null;
    }

    private int findConnectionIndex(long connectionId) {
        for (int i = 0; i < mConnections.size(); i++) {
            ConnectionInfo info = mConnections.get(i);
            if (info.getId() == connectionId) {
                return i;
            }
        }
        return -1;
    }

    private void updateConnnection(Connection connection) {
        synchronized (mLock) {
            ConnectionInfo info = findConnection(connection.getId());
            if (info != null) {
                info.mName     = connection.getDisplayName();
                info.mHostname = connection.getHostName();
                notifyDataSetChanged();
            }
        }
    }

    private void removeConnnection(Connection connection) {
        synchronized (mLock) {
            for (int i = 0; i < mConnections.size(); i++) {
                ConnectionInfo info = mConnections.get(i);
                if (info.mId == connection.getId()) {
                    mConnections.remove(i);
                    notifyDataSetChanged();
                    break;
                }
            }
        }
    }

    private void postNotifyDataSetChanged() {
        if (mServiceState == TapchatService.STATE_LOADED) {
            mIsLoaded = true;
        }
        notifyDataSetChanged();
    }

    public static final class ConnectionInfo implements Parcelable {
        private long mId;
        private String mName;
        private String mHostname;

        @SuppressWarnings("UnusedDeclaration")
        public static final Creator<ConnectionInfo> CREATOR = new Creator<ConnectionInfo>() {
            @Override
            public ConnectionInfo createFromParcel(Parcel parcel) {
                return new ConnectionInfo(parcel.readLong(),  parcel.readString(), parcel.readString());
            }

            @Override
            public ConnectionInfo[] newArray(int size) {
                return new ConnectionInfo[size];
            }
        };

        public static final Comparator<ConnectionInfo> COMPARATOR = new Comparator<ConnectionInfo>() {
            @Override
            public int compare(ConnectionInfo connection, ConnectionInfo connection2) {
                int result = connection.getName().compareToIgnoreCase(connection2.getName());
                if (result == 0) {
                    result = connection.getHostName().compareToIgnoreCase(connection2.getHostName());
                }
                return result;
            }
        };

        public static ConnectionInfo forConnection(Connection connection) {
            return new ConnectionInfo(connection.getId(), connection.getDisplayName(), connection.getHostName());
        }

        public ConnectionInfo(long id, String name, String hostname) {
            mId       = id;
            mName     = name;
            mHostname = hostname;
        }

        public long getId() {
            return mId;
        }

        public String getName() {
            return mName;
        }

        public String getHostName() {
            return mHostname;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeLong(mId);
            parcel.writeString(mName);
            parcel.writeString(mHostname);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public String toString() {
            return "ConnectionInfo{" +
                    "mId=" + mId +
                    ", mName='" + mName + '\'' +
                    ", mHostname='" + mHostname + '\'' +
                    '}';
        }
    }
}
