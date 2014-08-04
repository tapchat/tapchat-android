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
import android.text.TextUtils;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.app.event.BufferAddedEvent;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.fragment.BufferFragment;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.Buffer;
import com.tapchatapp.android.client.model.Connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.inject.Inject;

public class BuffersPagerAdapter extends TapchatFragmentStatePagerAdapter {

    private static final String KEY_BUFFERS = "com.tapchatapp.android.buffers";

    private final long mConnectionId;

    private final BuffersToDisplay mDisplay;
    private final Object mLock = new Object();

    private ArrayList<BufferInfo> mBuffers = new ArrayList<>();

    private int mConnectionState;

    @Inject Bus mBus;

    public BuffersPagerAdapter(Activity activity, long connectionId, BuffersToDisplay display) {
        super(activity.getFragmentManager());
        TapchatApp.get().inject(this);

        mConnectionId = connectionId;
        mDisplay = display;
    }

    public void registerBus() {
        mBus.register(this);
    }

    public void unregisterBus() {
        mBus.unregister(this);
    }

    @Override public Parcelable saveState() {
        Bundle bundle = (Bundle) super.saveState();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putParcelableArrayList(KEY_BUFFERS, mBuffers);
        return bundle;
    }

    @Override public void restoreState(Parcelable state, ClassLoader loader) {
        super.restoreState(state, loader);
        if (state != null && state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            mBuffers = bundle.getParcelableArrayList(KEY_BUFFERS);
            notifyDataSetChanged();
        }
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        TapchatService service = event.getService();

        mConnectionState = service.getConnectionState();
        if (mConnectionState != TapchatService.STATE_LOADED) {
            return;
        }

        Connection connection = service.getConnection(mConnectionId);

        final ArrayList<BufferInfo> buffers = new ArrayList<>();

        // Add new buffers.
        if (mDisplay == BuffersToDisplay.ConsoleOnly) {
            buffers.add(BufferInfo.forBuffer(connection.getConsoleBuffer()));
        } else {
            for (Buffer buffer : connection.getBuffers()) {
                if ((!buffer.isArchived()) || mDisplay == BuffersToDisplay.ShowArchived) {
                    buffers.add(BufferInfo.forBuffer(buffer));
                }
            }
        }

        Collections.sort(buffers, BufferInfo.COMPARATOR);
        mBuffers = buffers;
        notifyDataSetChanged();
    }

    @Subscribe public void onBufferAdded(final BufferAddedEvent event) {
        if (event.getConnection().getId() != mConnectionId) {
            return;
        }

        if (mConnectionState != TapchatService.STATE_LOADED) {
            return;
        }

        Buffer buffer = event.getBuffer();

        synchronized (mLock) {
            if (findBuffer(buffer.getId()) != null) {
                return;
            }

            final ArrayList<BufferInfo> buffers = new ArrayList<>(mBuffers);
            buffers.add(BufferInfo.forBuffer(buffer));
            Collections.sort(buffers, BufferInfo.COMPARATOR);

            mBuffers = buffers;
            notifyDataSetChanged();
        }
    }

    @Subscribe public void onBufferChanged(final BufferChangedEvent event) {
        if (event.getConnection().getId() != mConnectionId) {
            return;
        }

        if (mConnectionState != TapchatService.STATE_LOADED) {
            return;
        }

        synchronized (mLock) {
            Buffer buffer = event.getBuffer();
            BufferInfo info = findBuffer(buffer.getId());
            if (info != null) {
                String newName = buffer.getDisplayName();
                int newWeight = buffer.getWeight();

                if (buffer.isArchived() && mDisplay == BuffersToDisplay.Normal) {
                    mBuffers.remove(info);
                    notifyDataSetChanged();
                    return;
                }

                if ((!TextUtils.equals(info.mName, newName)) || info.mWeight != newWeight) {
                    info.mName = newName;
                    info.mWeight = newWeight;
                    Collections.sort(mBuffers, BufferInfo.COMPARATOR);
                    notifyDataSetChanged();
                }
            } else {
                if ((!buffer.isArchived()) || buffer.isArchived() && mDisplay == BuffersToDisplay.ShowArchived) {
                    final ArrayList<BufferInfo> buffers = new ArrayList<>(mBuffers);
                    buffers.add(BufferInfo.forBuffer(buffer));
                    Collections.sort(buffers, BufferInfo.COMPARATOR);
                    mBuffers = buffers;
                    notifyDataSetChanged();
                }
            }
        }
    }

    @Subscribe public void onBufferRemoved(final BufferRemovedEvent event) {
        if (event.getConnection().getId() != mConnectionId) {
            return;
        }

        if (mConnectionState != TapchatService.STATE_LOADED) {
            return;
        }

        Buffer buffer = event.getBuffer();
        synchronized (mLock) {
            for (int i = 0; i < mBuffers.size(); i++) {
                BufferInfo info = mBuffers.get(i);
                if (info.getBufferId() == buffer.getId()) {
                    mBuffers.remove(i);
                    notifyDataSetChanged();
                    return;
                }
            }
        }
    }

    @Override
    public int getCount() {
        return mBuffers.size();
    }

    @Override public String getPageTitle(int position) {
        return mBuffers.get(position).mName.toUpperCase();
    }

    @Override public Fragment getItem(int position) {
        BufferInfo info = mBuffers.get(position);
        return BufferFragment.create(info.getType(), mConnectionId, info.getBufferId());
    }

    @Override public int getItemPosition(Object object) {
        BufferFragment fragment = (BufferFragment) object;
        int index = findBufferIndex(fragment.getBufferId());
        if (index >= 0) {
            return index;
        } else {
            return POSITION_NONE;
        }
    }

    @Override public String getTag(int position) {
        return String.valueOf(mBuffers.get(position).getBufferId());
    }

    public BufferInfo getBufferInfo(int position) {
        return mBuffers.get(position);
    }

    public int getBufferIndex(long bufferId) {
        for (int i = 0; i < mBuffers.size(); i++) {
            BufferInfo info = mBuffers.get(i);
            if (info.getBufferId() == bufferId) {
                return i;
            }
        }
        return -1;
    }

    private BufferInfo findBuffer(long bufferId) {
        synchronized (mLock) {
            for (BufferInfo info : mBuffers) {
                if (info.getBufferId() == bufferId) {
                    return info;
                }
            }
            return null;
        }
    }

    public int findBufferIndex(long bufferId) {
        synchronized (mLock) {
            for (int i = 0; i < mBuffers.size(); i++) {
                BufferInfo info = mBuffers.get(i);
                if (info.getBufferId() == bufferId) {
                    return i;
                }
            }
            return -1;
        }
    }

    public enum BuffersToDisplay {
        Normal,
        ShowArchived,
        ConsoleOnly;

        public static BuffersToDisplay parseString(String name) {
            if (name == null) {
                return Normal;
            }
            switch (name) {
                case "archived":
                    return ShowArchived;
                case "console":
                    return ConsoleOnly;
                default:
                    return Normal;
            }
        }
    }

    public static final class BufferInfo implements Parcelable {

        private final long mConnectionId;
        private final long mBufferId;
        private final int mType;

        private int mWeight;
        private String mName;

        public static final Comparator<? super BufferInfo> COMPARATOR = new Comparator<BufferInfo>() {
            @Override public int compare(BufferInfo buffer, BufferInfo buffer1) {
                Integer weight1 = buffer.getWeight();
                Integer weight2 = buffer1.getWeight();
                if (weight1.equals(weight2)) {
                    return buffer.getName().compareToIgnoreCase(buffer1.getName());
                } else {
                    return weight2.compareTo(weight1);
                }
            }
        };

        public static final Creator<BufferInfo> CREATOR = new Creator<BufferInfo>() {
            @Override public BufferInfo createFromParcel(Parcel source) {
                return new BufferInfo(source.readLong(), source.readLong(), source.readInt(), source.readInt(),
                        source.readString());
            }

            @Override public BufferInfo[] newArray(int size) {
                return new BufferInfo[size];
            }
        };

        public BufferInfo(long cid, long bid, int type, int weight, String name) {
            mConnectionId = cid;
            mBufferId = bid;
            mType = type;
            mWeight = weight;
            mName = name;
        }

        public static BufferInfo forBuffer(Buffer buffer) {
            return new BufferInfo(buffer.getConnection().getId(), buffer.getId(), buffer.getType(), buffer.getWeight(),
                    buffer.getDisplayName());
        }

        public long getConnectionId() {
            return mConnectionId;
        }

        public long getBufferId() {
            return mBufferId;
        }

        public int getType() {
            return mType;
        }

        public String getName() {
            return mName;
        }

        public int getWeight() {
            return mWeight;
        }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mConnectionId);
            dest.writeLong(mBufferId);
            dest.writeInt(mType);
            dest.writeInt(mWeight);
            dest.writeString(mName);
        }

        @Override public int describeContents() {
            return 0;
        }
    }
}
