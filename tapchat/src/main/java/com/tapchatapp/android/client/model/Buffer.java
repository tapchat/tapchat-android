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

package com.tapchatapp.android.client.model;

import android.util.Log;

import com.google.common.collect.EvictingQueue;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferLineAddedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.client.MessageHandler;
import com.tapchatapp.android.client.message.BufferEventMessage;
import com.tapchatapp.android.client.message.MakeBufferMessage;
import com.tapchatapp.android.client.message.request.ArchiveBufferMessage;
import com.tapchatapp.android.client.message.request.DeleteBufferMessage;
import com.tapchatapp.android.client.message.request.UnarchiveBufferMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Buffer {
    public static final int CHANNEL_TYPE      = 1;
    public static final int CONVERSATION_TYPE = 2;
    public static final int CONSOLE_TYPE      = 3;

    public static final int MAX_EVENTS = 500;

    public static final Comparator<? super Buffer> COMPARATOR = new Comparator<Buffer>() {
        @Override
        public int compare(Buffer buffer, Buffer buffer1) {
            Integer weight1 = buffer.getWeight();
            Integer weight2 = buffer1.getWeight();
            if (weight1.equals(weight2)) {
                return buffer.getName().compareToIgnoreCase(buffer1.getName());
            } else {
                return weight2.compareTo(weight1);
            }
        }
    };

    private static final String TAG = "Buffer";

    private Connection mConnection;

    private boolean mExists = false;
    private long mId;
    private String mName;
    private boolean mArchived;

    private final EvictingQueue<BufferEvent> mEvents = EvictingQueue.create(MAX_EVENTS);

    private List<Long> mMessageIds = new ArrayList<Long>();

    private Map<String, MessageHandler> mMessageHandlers;
    private Map<String, MessageHandler> mInitializedMessageHandlers;

    private long    mLastSeenEid;
    private long    mLastEid;
    private boolean mUnread;
    private int     mHighlightCount;

    Buffer(Connection connection, MakeBufferMessage message) throws Exception {
        mConnection  = connection;
        mId          = message.bid;

        mMessageHandlers            = getMessageHandlers();
        mInitializedMessageHandlers = getInitializedMessageHandlers();

        reload(message);
    }

    public long getId() {
        return mId;
    }

    public abstract int getType();

    public String getName() {
        return mName;
    }

    public String getDisplayName() {
        return mName;
    }

    public Connection getConnection() {
        return mConnection;
    }

    public boolean isArchived() {
        return mArchived;
    }

    public boolean isActive() {
        return (getConnection().getState() == Connection.STATE_CONNECTED);
    }

    public BufferEvent[] getBacklog() {
        synchronized (mEvents) {
            return (BufferEvent[]) mEvents.toArray(new BufferEvent[mEvents.size()]);
        }
    }

    public BufferEvent getLastEvent() {
        synchronized (mEvents) {
            BufferEvent[] backlog = getBacklog();
            if (backlog.length > 0) {
                return backlog[backlog.length - 1];
            }
        }
        return null;
    }

    public BufferEvent getLastMessage() {
        synchronized (mEvents) {
            BufferEvent[] backlog = getBacklog();
            BufferEvent event;
            if (backlog.length > 0) {
                for (int i = backlog.length - 1; i >= 0; i--) {
                    event = backlog[i];
                    if (event.getFirstItem().getMessage().type.equals("buffer_msg")) {
                        return event;
                    }
                }
            }
            return null;
        }
    }

    void setArchived(boolean archived) {
        mArchived = archived;
    }

    boolean hasFocus() {
        return getConnection().getService().getSelectedBuffer() == this;
    }

    public int getHighlightCount() {
        return mHighlightCount;
    }

    public boolean isUnread() {
        return mUnread;
    }

    public void markAllRead() {
        BufferEvent[] backlog = getBacklog();
        if (backlog.length > 0) {
            long lastEid = backlog[backlog.length - 1].getLastItem().getEid();
            markRead(lastEid);
        }
    }

    public void markRead(long eid) {
        if (eid < mLastEid) {
            return;
        }

        mLastSeenEid    = eid;
        mUnread         = false;
        mHighlightCount = 0;
        notifyChanged();
    }

    public void archive() {
        ArchiveBufferMessage message = new ArchiveBufferMessage();
        message.id = getId();
        mConnection.post(message, null);
    }

    public void unarchive() {
        UnarchiveBufferMessage message = new UnarchiveBufferMessage();
        message.id = getId();
        mConnection.post(message, null);
    }

    public void delete() {
        DeleteBufferMessage message = new DeleteBufferMessage();
        message.id = getId();
        mConnection.post(message, null);
    }

    synchronized void processMessage(BufferEventMessage message) throws Exception {
        long eid = message.eid;
        String type = message.type;

        if (eid > -1) { // FIXME
            if (mMessageIds.contains(eid)) {
                Log.w("Buffer", "Got duplicate message! " + message);
                return;
            }
            mMessageIds.add(eid);
        }

        if (eid > mLastEid) {
            mLastEid = eid;
        }

        if (eid > 0 && (!UNRENDERED_MESSAGES.contains(type))) {
            BufferEvent lastEvent = getLastEvent();
            BufferEventItem item = new BufferEventItem(message);
            if (lastEvent != null && lastEvent.shouldMerge(item)) {
                lastEvent.addItem(item);
            } else {
                addEvent(new BufferEvent(item));
            }

            if (eid > mLastSeenEid) {
                if (hasFocus() || message.isSelf()) {
                    markRead(eid);
                } else {
                    if (message.isImportant() && !mUnread) {
                        mUnread = true;
                        notifyChanged();
                    }
                    if (message.isHighlight()) {
                        mHighlightCount ++;
                        notifyChanged();
                    }
                }
            }
        }

        boolean isBacklogMessage = message.is_backlog;
        if ((!isBacklogMessage) && (!getConnection().isBacklog()) && mInitializedMessageHandlers.containsKey(type)) {
            mInitializedMessageHandlers.get(type).handleMessage(message);
        }

        if (mMessageHandlers.containsKey(type)) {
            mMessageHandlers.get(type).handleMessage(message);
        }
    }

    void notifyChanged() {
        getConnection().getService().postToBus(new BufferChangedEvent(this));
    }

    void notifyRemoved() {
        getConnection().getService().postToBus(new BufferRemovedEvent(this));
    }

    protected Map<String, MessageHandler> getMessageHandlers() {
        return new HashMap<>();
    }

    protected Map<String, MessageHandler> getInitializedMessageHandlers() {
        return new HashMap<>();
    }

    private void addEvent(BufferEvent event) {
        synchronized (mEvents) {
            mEvents.add(event);
        }
        getConnection().getService().postToBus(new BufferLineAddedEvent(this, event));
    }

    private static final List<String> UNRENDERED_MESSAGES = Arrays.asList(
        "makebuffer",
        "channel_init",
        "connecting_finished",
        // Channel status
        "user_away",
        "user_back",
        "user_details",
        "channel_timestamp",
        // Conversation status
        "whois_response",
        "away",
        // Connection status
        "isupport_params",
        "self_away",
        "self_back",
        // Overlay
        "names_reply",
        "query_too_long",
        "try_again",
        "accept_list",
        "ban_list",
        "ban_exception_list",
        "links_response",
        "silence_list",
        "trace_response",
        "who_response",
        "ison",
        "list_response_toomany",
        "list_response",
        "list_response_fetching",
        "map_list",
        "remote_isupport_params",
        "userhost",
        // Prompt
        "channel_full",
        "too_many_targets",
        "no_messages_from_non_registered",
        "not_registered",
        "already_registered",
        "no_such_nick",
        "bad_channel_name",
        "bad_channel_key",
        "banned_from_channel",
        "invite_only_chan",
        "need_registered_nick",
        "no_such_channel",
        "too_many_channels"
    );

    public long getLastSeenEid() {
        return mLastSeenEid;
    }

    public void reload(MakeBufferMessage message) {
        mExists         = true;
        mName           = message.name;
        mArchived       = (message.archived || message.hidden);
        mLastSeenEid    = message.last_seen_eid;
        mHighlightCount = 0;
    }

    public boolean exists() {
        return mExists;
    }

    @Override
    public String toString() {
        return String.format("%s{id=%s, name=%s, connection={%s}}", getClass().getName(), getId(), getName(), getConnection());
    }

    void serviceDisconnected() {
        mExists = false;
    }

    public int getWeight() {
        int weight = 0;
        if (isArchived()) {
            weight -= 99;
        }
        if (this instanceof ChannelBuffer) {
            weight ++; // Show channels first
            ChannelBuffer channelBuffer = (ChannelBuffer) this;
            if (channelBuffer.isJoined()) {
                weight ++; // Show joined channels first
            }
        }
        return weight;
    }
}
