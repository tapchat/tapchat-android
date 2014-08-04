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

import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferLineAddedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;

public class QueryBufferFragment extends BufferFragment {

    @Override public void onCreateOptionsMenu(android.view.Menu menu, android.view.MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.buffer_query, menu);
    }

    @Subscribe @Override public void onServiceStateChanged(ServiceStateChangedEvent event) {
        super.onServiceStateChanged(event);
    }

    @Subscribe @Override public void onConnectionChanged(ConnectionChangedEvent event) {
        super.onConnectionChanged(event);
    }

    @Subscribe @Override public void onBufferChanged(BufferChangedEvent event) {
        super.onBufferChanged(event);
    }

    @Subscribe @Override public void onBufferLineAdded(BufferLineAddedEvent event) {
        super.onBufferLineAdded(event);
    }

    @Subscribe @Override public void onBufferRemoved(BufferRemovedEvent event) {
        super.onBufferRemoved(event);
    }
}
