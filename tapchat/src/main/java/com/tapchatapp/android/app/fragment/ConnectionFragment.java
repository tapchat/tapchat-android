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
import android.app.ListFragment;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.MainActivity;
import com.tapchatapp.android.app.event.BufferAddedEvent;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.ui.ConnectionStatusBar;
import com.tapchatapp.android.app.ui.FilterableListAdapter;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.Buffer;
import com.tapchatapp.android.client.model.ChannelBuffer;
import com.tapchatapp.android.client.model.Connection;
import com.tapchatapp.android.client.model.ConsoleBuffer;

import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

public class ConnectionFragment extends ListFragment {

    public static final String ARG_CONNECTION_ID = "com.tapchatapp.android.arg_connection_id";

    private long mConnectionId;
    private int mListHighlightTextColor;
    private int mListTextColor;
    private int mServiceState;

    private Connection mConnection;
    private ConnectionStatusBar mStatusBar;

    @Inject Bus mBus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TapchatApp.get().inject(this);

        mConnectionId = getArguments().getLong(ARG_CONNECTION_ID);
        mStatusBar = new ConnectionStatusBar(this, mConnectionId);
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
        mStatusBar.registerBus(mBus);
    }

    @Override public void onPause() {
        super.onPause();
        mBus.unregister(this);
        mStatusBar.unregisterBus(mBus);

        mConnection = null;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        mStatusBar.destroy();
    }

    @Override public void onAttach(Activity activity) {
        super.onAttach(activity);

        TypedArray typedArray = activity.obtainStyledAttributes(R.styleable.Tapchat);
        mListHighlightTextColor = typedArray.getColor(R.styleable.Tapchat_listHighlightTextColor, Color.RED);
        mListTextColor          = typedArray.getColor(R.styleable.Tapchat_listTextColor,          Color.RED);
        typedArray.recycle();
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View content = super.onCreateView(inflater, container, savedInstanceState);
        View header  = inflater.inflate(R.layout.connection_header, container, false);

        LinearLayout wrapper = new LinearLayout(getActivity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        return wrapper;
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setBackgroundColor(Color.TRANSPARENT);
        getListView().setCacheColorHint(Color.TRANSPARENT);
        getListView().setSelector(R.drawable.list_selector);
        setListShownNoAnimation(true);
    }

    @Override public void onListItemClick(ListView l, View v, int position, long id) {
        if (mConnection == null) {
            return;
        }

        Buffer buffer = (Buffer) getListAdapter().getItem(position);
        if (buffer == null) {
            return;
        }

        ((MainActivity) getActivity()).showBuffer(buffer.getType(), mConnection.getId(), buffer.getId(), buffer.isArchived());
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        TapchatService service = event.getService();
        mServiceState = service.getConnectionState();
        if (mServiceState == TapchatService.STATE_LOADED) {
            if (mConnection == null) {
                mConnection = service.getConnection(mConnectionId);
            }
        } else {
            mConnection = null;
        }

        updateView();
    }

    @Subscribe public void onConnectionChanged(ConnectionChangedEvent event) {
        if (event.getConnection().getId() != mConnectionId) {
            return;
        }
        updateView();
    }

    @Subscribe public void onBufferAdded(BufferAddedEvent event) {
        if (event.getConnection().getId() != mConnectionId || mServiceState != TapchatService.STATE_LOADED) {
            return;
        }

        final Buffer buffer = event.getBuffer();

        BufferListAdapter adapter = (BufferListAdapter) getListAdapter();
        if (adapter != null && !adapter.contains(buffer)) {
            adapter.addItem(buffer);
        }
    }

    @Subscribe public void onBufferChanged(BufferChangedEvent event) {
        if (event.getConnection().getId() != mConnectionId) {
            return;
        }

        BufferListAdapter adapter = (BufferListAdapter) getListAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Subscribe public void onBufferRemoved(BufferRemovedEvent event) {
        if (event.getConnection().getId() != mConnectionId) {
            return;
        }

        final Buffer buffer = event.getBuffer();

        BufferListAdapter adapter = (BufferListAdapter) getListAdapter();
        if (adapter != null) {
            adapter.removeItem(buffer);
        }
    }

    public Connection getConnection() {
        return mConnection;
    }

    public long getConnectionId() {
        return mConnectionId;
    }

    private void updateView() {
        if (getView() == null) {
            return;
        }

        getListView().setEnabled(mConnection != null);

        if (mConnection != null) {
            BufferListAdapter adapter = (BufferListAdapter) getListAdapter();
            if (adapter != null) {
                adapter.updateItems(mConnection.getBuffers());
            } else {
                setListAdapter(new BufferListAdapter(mConnection.getBuffers()));
            }

            if (mConnection.getState() == Connection.STATE_CONNECTED) {
                setEmptyText(getString(R.string.no_channels));
            } else {
                setEmptyText(null);
            }
        } else {
            setEmptyText(null);
        }

        getActivity().invalidateOptionsMenu();
    }

    private class BufferListAdapter extends FilterableListAdapter<Buffer> {
        private SharedPreferences mPrefs;

        public BufferListAdapter(List<Buffer> buffers) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            updateItems(buffers);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Buffer buffer = getItem(position);

            if (convertView == null) {
                convertView = View.inflate(getActivity(), R.layout.buffer_item, null);
            }

            TextView notificationCount = (TextView) convertView.findViewById(R.id.highlight_count);
            notificationCount.setText(String.valueOf(buffer.getHighlightCount()));
            notificationCount.setVisibility(buffer.getHighlightCount() > 0 ? View.VISIBLE : View.GONE);

            TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
            TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);

            text1.setText(buffer.getName());

            if (buffer.isUnread()) {
                text1.setTextColor(mListHighlightTextColor);
                text1.setTypeface(null, Typeface.BOLD);
                text2.setTextColor(mListHighlightTextColor);
            } else {
                text1.setTextColor(mListTextColor);
                text1.setTypeface(null, Typeface.NORMAL);
                text2.setTextColor(mListTextColor);
            }

            if (buffer instanceof ChannelBuffer) {
                ChannelBuffer channelBuffer = (ChannelBuffer) buffer;

                if (channelBuffer.isJoined()) {
                    text2.setText(channelBuffer.getTopic());
                    /*
                    BufferEvent lastEvent = channelBuffer.getLastMessage();
                    if (lastEvent != null) {
                        BufferEventItem lastItem = lastEvent.getFirstItem();
                        text2.setText(mEventRenderer.renderEventItem(lastItem).toString());
                    } else {
                        text2.setText(channelBuffer.getTopic());
                    }
                    */
                } else {
                    text2.setText(R.string.not_in_channel);
                }
                text1.setVisibility(View.VISIBLE);
            } else {
                text2.setVisibility(View.GONE);
            }

            boolean isActiveAndNotArchived = buffer.isActive() && !buffer.isArchived();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                text1.setAlpha(isActiveAndNotArchived ? 1f : 0.4f);
                text2.setAlpha(isActiveAndNotArchived ? 1f : 0.4f);
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getType();
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isVisible(Buffer item) {
            boolean showArchived = mPrefs.getBoolean(TapchatApp.PREF_SHOW_ARCHIVED, false);
            return (!(item instanceof ConsoleBuffer) && (showArchived || !item.isArchived()));
        }

        @Override
        protected Comparator<? super Buffer> getComparator() {
            return Buffer.COMPARATOR;
        }
    }
}
