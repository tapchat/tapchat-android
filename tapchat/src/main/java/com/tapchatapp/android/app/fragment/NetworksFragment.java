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

import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.activity.AddNetworkActivity;
import com.tapchatapp.android.app.activity.BuffersActivity;
import com.tapchatapp.android.app.activity.EditNetworkActivity;
import com.tapchatapp.android.app.event.ConnectionAddedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ConnectionRemovedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.ui.FilterableListAdapter;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.Connection;

import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

public class NetworksFragment extends ListFragment {

    @Inject Bus mBus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TapchatApp.get().inject(this);
        setHasOptionsMenu(true);
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
    }

    @Override public void onPause() {
        super.onPause();
        mBus.unregister(this);
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(getListView());
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.networks, menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_network) {
            startActivity(new Intent(getActivity(), AddNetworkActivity.class));
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            getActivity().finish();
            return true;
        }
        return false;
    }

    @Override public void onListItemClick(ListView l, View v, int position, long id) {
        l.showContextMenuForChild(v);
    }

    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.network, menu);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Connection connection = (Connection) getListAdapter().getItem(info.position);

        boolean isDisconnected = (connection.getState() == Connection.STATE_DISCONNECTED);
        menu.findItem(R.id.connect).setVisible(isDisconnected);
        menu.findItem(R.id.disconnect).setVisible(!isDisconnected);
    }

    @Override public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        final Connection connection = (Connection) getListAdapter().getItem(info.position);

        switch (item.getItemId()) {
            case R.id.connect: {
                connection.reconnect();
                return true;

            } case R.id.disconnect: {
                connection.disconnect();
                return true;

            } case R.id.edit_network: {
                Intent intent = new Intent(getActivity(), EditNetworkActivity.class);
                intent.putExtra(EditNetworkActivity.EXTRA_CID, connection.getId());
                startActivity(intent);
                return true;

            } case R.id.remove_network:
                new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.confirm_delete_network)
                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            connection.delete();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return true;

            case R.id.view_console: {
                Intent intent = new Intent(getActivity(), BuffersActivity.class);
                intent.setData(new Uri.Builder()
                    .scheme("tapchat")
                    .authority(String.valueOf(connection.getId()))
                    .path(String.valueOf(connection.getConsoleBuffer().getId()))
                    .appendQueryParameter("display", "console")
                    .build());
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        final TapchatService service = event.getService();
        if (service.getConnectionState() == TapchatService.STATE_LOADED) {
            if (getView() != null) {
                setListShown(true);
            }

            if (getListAdapter() == null) {
                setListAdapter(new NetworksListAdapter(service.getConnections()));
            } else {
                ((NetworksListAdapter) getListAdapter()).updateItems(service.getConnections());
            }
        }
    }

    @Subscribe public void onConnectionAdded(ConnectionAddedEvent event) {
        final Connection connection = event.getConnection();

        final NetworksListAdapter adapter = (NetworksListAdapter) getListAdapter();
        if (adapter != null && !adapter.contains(connection)) {
            adapter.addItem(connection);
        }
    }

    @Subscribe public void onConnectionChanged(ConnectionChangedEvent event) {
        final NetworksListAdapter adapter = (NetworksListAdapter) getListAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Subscribe public void onConnectionRemoved(ConnectionRemovedEvent event) {
        final Connection connection = event.getConnection();

        final NetworksListAdapter adapter = (NetworksListAdapter) getListAdapter();
        if (adapter != null && adapter.contains(connection)) {
            adapter.removeItem(connection);
        }
    }

    private class NetworksListAdapter extends FilterableListAdapter<Connection> {
        private NetworksListAdapter(List<Connection> connections) {
            super();
            updateItems(connections);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup group) {
            Connection connection = getItem(position);

            if (convertView == null) {
                convertView = View.inflate(getActivity(), android.R.layout.simple_list_item_2, null);
            }

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(connection.getDisplayName());
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(connection.getDisplayState(getActivity()));

            return convertView;
        }

        @Override
        protected Comparator<? super Connection> getComparator() {
            return Connection.COMPARATOR;
        }
    }
}
