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

import android.app.ListFragment;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.app.ui.FilterableListAdapter;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.model.ChannelBuffer;
import com.tapchatapp.android.client.model.Member;

import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

public class MemberListFragment extends ListFragment {

    private ChannelBuffer mChannel;

    @Inject Bus mBus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TapchatApp.get().inject(this);
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setSelector(R.drawable.list_selector);
        updateView();
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
    }

    @Override public void onPause() {
        super.onPause();
        mBus.unregister(this);

        mChannel = null;
    }

    @Override public void onListItemClick(ListView l, View v, int position, long id) {
        Member member = ((MemberListAdapter) getListAdapter()).getItem(position);
        mChannel.getConnection().openBuffer(member.getNick());
        getActivity().finish();
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        TapchatService service = event.getService();

        if (mChannel == null && service.getConnectionState() == TapchatService.STATE_LOADED) {
            long connectionId = getArguments().getLong(BufferFragment.ARG_CONNECTION_ID);
            long bufferId     = getArguments().getLong(BufferFragment.ARG_BUFFER_ID);

            mChannel = (ChannelBuffer) service.getBuffer(connectionId, bufferId);
            if (mChannel == null) {
                throw new IllegalStateException("Channel buffer not found. " + connectionId + " " + bufferId);
            }
        }

        updateView();
    }

    private void updateView() {
        if (getView() == null) {
            return;
        }

        if (mChannel != null) {
            int numMembers = mChannel.getMembers().size();
            String title = getResources().getQuantityString(R.plurals.channel_members_format, numMembers, String.valueOf(numMembers), mChannel.getName());
            getActivity().setTitle(title);

            if (getListAdapter() == null) {
                setListAdapter(new MemberListAdapter(mChannel.getMembers()));
            } else {
                ((MemberListAdapter) getListAdapter()).updateItems(mChannel.getMembers());
            }
        }
    }

    private class MemberListAdapter extends FilterableListAdapter<Member> {
        private MemberListAdapter(List<Member> members) {
            updateItems(members);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup group) {
            if (convertView == null) {
                convertView = View.inflate(getActivity(), android.R.layout.simple_list_item_1, null);
            }

            Member member = getItem(position);

            ((TextView) convertView).setText(member.getNick());

            return convertView;
        }

        @Override protected Comparator<? super Member> getComparator() {
            return new Comparator<Member>() {
                @Override
                public int compare(Member member, Member member1) {
                    Integer weight1 = getMemberWeight(member);
                    Integer weight2 = getMemberWeight(member1);
                    if (weight1.equals(weight2)) {
                        return member.getNick().compareToIgnoreCase(member1.getNick());
                    } else {
                        return weight2.compareTo(weight1);
                    }
                }
            };
        }

        private int getMemberWeight(Member member) {
            int weight = 0;
            if (member.isOp()) {
                weight += 2;
            } else if (member.isVoiced()) {
                weight += 1;
            }
            return weight;
        }
    }
}
