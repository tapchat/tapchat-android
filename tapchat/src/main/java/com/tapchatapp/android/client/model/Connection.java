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

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.ImmutableMap;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.InvalidConnectionCertActivity;
import com.tapchatapp.android.app.event.BufferAddedEvent;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.client.MessageHandler;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.message.BufferArchivedMessage;
import com.tapchatapp.android.client.message.BufferEventMessage;
import com.tapchatapp.android.client.message.BufferUnarchivedMessage;
import com.tapchatapp.android.client.message.ConnectedMessage;
import com.tapchatapp.android.client.message.ConnectingCancelledMessage;
import com.tapchatapp.android.client.message.ConnectingFailedMessage;
import com.tapchatapp.android.client.message.ConnectingFinishedMessage;
import com.tapchatapp.android.client.message.ConnectingMessage;
import com.tapchatapp.android.client.message.ConnectingRetryMessage;
import com.tapchatapp.android.client.message.EndOfBacklogMessage;
import com.tapchatapp.android.client.message.InvalidCertMessage;
import com.tapchatapp.android.client.message.MakeBufferMessage;
import com.tapchatapp.android.client.message.MakeServerMessage;
import com.tapchatapp.android.client.message.Message;
import com.tapchatapp.android.client.message.OpenBufferMessage;
import com.tapchatapp.android.client.message.ResponseMessage;
import com.tapchatapp.android.client.message.ServerDetailsChangedMessage;
import com.tapchatapp.android.client.message.SocketClosedMessage;
import com.tapchatapp.android.client.message.WaitingToRetryMessage;
import com.tapchatapp.android.client.message.YouNickchangeMessage;
import com.tapchatapp.android.client.message.request.AcceptCertMessage;
import com.tapchatapp.android.client.message.request.DeleteBufferMessage;
import com.tapchatapp.android.client.message.request.DeleteConnectionMessage;
import com.tapchatapp.android.client.message.request.DisconnectMessage;
import com.tapchatapp.android.client.message.request.EditServerMessage;
import com.tapchatapp.android.client.message.request.JoinMessage;
import com.tapchatapp.android.client.message.request.PartMessage;
import com.tapchatapp.android.client.message.request.ReconnectMessage;
import com.tapchatapp.android.client.message.request.SayMessage;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Connection {

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING   = 1;
    public static final int STATE_RETRYING     = 2;
    public static final int STATE_CONNECTED    = 3;

    public static final Comparator<Connection> COMPARATOR = new Comparator<Connection>() {
        @Override
        public int compare(Connection connection, Connection connection2) {
            int result = connection.getName().compareToIgnoreCase(connection2.getName());
            if (result == 0) {
                result = connection.getHostName().compareToIgnoreCase(connection2.getHostName());
            }
            return result;
        }
    };

    private static final String TAG = "Connection";

    private TapchatService mService;

    private boolean mExists = false;
    private boolean mIsBacklog = true;
    private final Map<Long, Buffer> mBuffers = Collections.synchronizedMap(new TreeMap<Long, Buffer>());
    private ConsoleBuffer mConsoleBuffer;

    private int mState;
    private String mName;
    private long mId;
    private String mNick;
    private boolean mSSL;
    private String mHostName;
    private String mRealName;
    private int mPort;
    private String mPassword;

    private String mPendingOpenBuffer;

    public Connection(TapchatService service, MakeServerMessage message) throws Exception {
        mService = service;

        /*
        {
            "bid":-1,
            "eid":-1,
            "type":"makeserver",
            "time":-1,
            "highlight":false,
            "num_buffers":5,
            "cid":2135,
            "name":"IRCCloud",
            "nick":"fR",
            "nickserv_nick":"fR",
            "nickserv_pass":"",
            "realname":"Eric",
            "hostname":"irc.irccloud.com",
            "port":6667,
            "away":"",
            "disconnected":false,
            "away_timeout":0,
            "autoback":true,
            "ssl":false,
            "server_pass":""
        }
        */

        mId = message.cid;

        updateDetails(message);
    }

    public String getName() {
        return mName;
    }

    public String getDisplayName() {
        if (!TextUtils.isEmpty(mName)) {
            return mName;
        } else {
            return mHostName;
        }
    }

    public String getDisplayState(Context context) {
        switch (mState) {
            case STATE_DISCONNECTED:
                return context.getString(R.string.disconnected_format, mName);
            case Connection.STATE_CONNECTING:
                return context.getString(R.string.connecting_format, mName);
            case Connection.STATE_RETRYING:
                return context.getString(R.string.retrying_format, mName);
            case Connection.STATE_CONNECTED:
                return context.getString(R.string.connected_format, mName);
        }
        return null;
    }

    public long getId() {
        return mId;
    }

    public boolean isBacklog() {
        return mIsBacklog;
    }

    public String getNick() {
        return mNick;
    }

    public ConsoleBuffer getConsoleBuffer() {
        return mConsoleBuffer;
    }

    public List<Buffer> getBuffers() {
        synchronized (mBuffers) {
            return new ArrayList<Buffer>(mBuffers.values());
        }
    }

    public int getBufferCount() {
        synchronized (mBuffers) {
            return mBuffers.size();
        }
    }

    public Buffer getBuffer(long id) {
        synchronized (mBuffers) {
            if (mBuffers.containsKey(id))
                return mBuffers.get(id);
            else if (mConsoleBuffer != null && mConsoleBuffer.getId() == id)
                return mConsoleBuffer;
            return null;
        }
    }

    public int getBufferIndex(long bufferId) {
        synchronized (mBuffers) {
            return getBuffers().indexOf(getBuffer(bufferId));
        }
    }

    public Buffer findBuffer(String name) {
        synchronized (mBuffers) {
            for (Buffer buffer : mBuffers.values()) {
                if (buffer.getName().equalsIgnoreCase(name)) {
                    return buffer;
                }
            }
            return null;
        }
    }

    public boolean isSSL() {
        return mSSL;
    }

    public String getPassword() {
        return mPassword;
    }

    public String getHostName() {
        return mHostName;
    }

    public String getRealName() {
        return mRealName;
    }

    public int getPort() {
        return mPort;
    }

    public int getState() {
        return mState;
    }

    public void join(String channelName, TapchatService.PostCallback callback) {
        ChannelBuffer channel = (ChannelBuffer) findBuffer(channelName);
        if (channel != null && channel.isJoined()) {
            startBufferActivity(channel);
            if (callback != null) {
                callback.run(null, null);
            }
            return;
        }

        JoinMessage message = new JoinMessage();
        message.channel = channelName;
        post(message, callback);
    }

    public void part(String channelName, TapchatService.PostCallback callback) {
        PartMessage message = new PartMessage();
        message.channel = channelName;
        post(message, callback);
    }

    public void say(String to, String text, TapchatService.PostCallback callback) {
        SayMessage message = new SayMessage();
        message.to = to;
        message.msg = text;
        post(message, callback);
    }

    public void acceptCert(String fingerprint, boolean accept) {
        AcceptCertMessage message = new AcceptCertMessage();
        message.fingerprint = fingerprint;
        message.accept = accept;
        post(message, null);
    }

    public void openBuffer(String nick) {
        Buffer buffer = findBuffer(nick);
        if (buffer == null) {
            say(nick, null, null);
        } else {
            buffer.unarchive();
            startBufferActivity(buffer);
        }
    }

    public void reconnect() {
        post(new ReconnectMessage(), null);
    }

    public void disconnect() {
        post(new DisconnectMessage(), null);
    }

    public void delete() {
        post(new DeleteConnectionMessage(), null);
    }

    public void edit(String name, String hostname, String nickname, String port, String realname, boolean useSSL,
                     String password, TapchatService.PostCallback callback) {

        EditServerMessage message = new EditServerMessage();
        message.name = name;
        message.hostname = hostname;
        message.nickname = nickname;
        message.port = port;
        message.realname = realname;
        message.ssl = useSSL ? "1" : "0";
        message.server_pass = password;
        // FIXME: Not implemented yet
        // message.channels = "";
        // message.nspass = "";

        post(message, callback);
    }

    TapchatService getService() {
        return mService;
    }

    public synchronized void processMessage(Message message) throws Exception {
        String type = message.type;

        if (mMessageHandlers.containsKey(type)) {
            mMessageHandlers.get(type).handleMessage(message);
        }


        boolean isBacklogMessage = message.is_backlog;
        if ((!isBacklogMessage) && (!mIsBacklog) && mInitializedMessageHandlers.containsKey(type)) {
            mInitializedMessageHandlers.get(type).handleMessage(message);
        }

        if (message.bid != null && !message.type.equals(MakeBufferMessage.TYPE)) {
            Buffer buffer = getBuffer(message.bid);
            if (buffer != null) {
                buffer.processMessage((BufferEventMessage) message);
            }
        }
    }

    public void handleResponse(ResponseMessage response, Message request) {
        String type = response.type;
        if (type != null && type.equals("open_buffer")) {
            String bufferName = ((OpenBufferMessage) response.msg).name;
            Buffer buffer = findBuffer(bufferName);
            if (buffer == null) {
                mPendingOpenBuffer = bufferName;
            } else {
                startBufferActivity(buffer);
                mPendingOpenBuffer = null;
            }
        }
    }

    void notifyChanged() {
        mService.postToBus(new ConnectionChangedEvent(this));
    }

    public void reload(MakeServerMessage message) throws JSONException {
        updateDetails(message);
        // FIXME: Notify all buffers that we're reloading?
    }

    public void post(Message message, TapchatService.PostCallback callback) {
        message.cid = getId();
        mService.post(message, callback);
    }

    private void startBufferActivity(Buffer buffer) {
        Context appContext = TapchatApp.get();

        Intent intent = new Intent(TapchatApp.ACTION_OPEN_BUFFER);
        intent.putExtra("cid", String.valueOf(buffer.getConnection().getId()));
        intent.putExtra("bid", String.valueOf(buffer.getId()));
        appContext.sendOrderedBroadcast(intent, null);
    }

    @Override
    public String toString() {
        return String.format("Connection{id=%s, name=%s}", getId(), getName());
    }

    private Map<String, MessageHandler> mMessageHandlers = ImmutableMap.<String, MessageHandler>builder()
        .put(EndOfBacklogMessage.TYPE, new MessageHandler<EndOfBacklogMessage>() {
            @Override
            public void handleMessage(EndOfBacklogMessage message) throws Exception {
                mIsBacklog = false;
                mService.updateLoadingProgress();

                synchronized (mBuffers) {
                    for (Buffer buffer : mBuffers.values()) {
                        if (!buffer.exists()) {
                            removeBuffer(buffer);
                        }
                    }
                }
            }
        })
        .put(MakeBufferMessage.TYPE, new MessageHandler<MakeBufferMessage>() {
            @Override public void handleMessage(MakeBufferMessage message) throws Exception {
                long bid = message.bid;

                Buffer buffer = getBuffer(bid);

                if (buffer != null) {
                    buffer.reload(message);
                    return;
                }

                String bufferType = message.buffer_type;
                switch (bufferType) {
                    case "channel":
                        buffer = new ChannelBuffer(Connection.this, message);
                        break;
                    case "conversation":
                        buffer = new ConversationBuffer(Connection.this, message);
                        break;
                    case "console":
                        buffer = new ConsoleBuffer(Connection.this, message);
                        mConsoleBuffer = (ConsoleBuffer) buffer;
                        return;
                    default:
                        throw new Exception("Unknown buffer type: " + bufferType);
                }

                synchronized (mBuffers) {
                    mBuffers.put(bid, buffer);
                }

                final Buffer theBuffer = buffer;
                mService.postToBus(new BufferAddedEvent(theBuffer));

                if (mPendingOpenBuffer != null && mPendingOpenBuffer.equals(buffer.getName())) {
                    startBufferActivity(buffer);
                    mPendingOpenBuffer = null;
                }
            }
        })
        .build();

    private Map<String, MessageHandler> mInitializedMessageHandlers = new ImmutableMap.Builder<String, MessageHandler>()
        .put(ServerDetailsChangedMessage.TYPE, new MessageHandler<ServerDetailsChangedMessage>() {
            @Override
            public void handleMessage(ServerDetailsChangedMessage message) throws Exception {
                updateDetails(message);
            }
        })
        .put(YouNickchangeMessage.TYPE, new MessageHandler<YouNickchangeMessage>() {
            @Override public void handleMessage(YouNickchangeMessage message) throws Exception {
                mNick = message.newnick;
                notifyChanged();
            }
        })
        .put(ConnectingMessage.TYPE, new MessageHandler<ConnectingMessage>() {
            @Override public void handleMessage(ConnectingMessage message) throws Exception {
                mNick = message.nick;
                mState = STATE_CONNECTING;
                notifyChanged();
            }
        })
        .put(ConnectingRetryMessage.TYPE, new MessageHandler<ConnectingRetryMessage>() {
            @Override public void handleMessage(ConnectingRetryMessage message) throws Exception {
                mState = STATE_RETRYING;
                notifyChanged();
            }
        })
        .put(WaitingToRetryMessage.TYPE, new MessageHandler<WaitingToRetryMessage>() {
            @Override public void handleMessage(WaitingToRetryMessage message) throws Exception {
                mState = STATE_RETRYING;
                notifyChanged();
            }
        })
        .put(ConnectingCancelledMessage.TYPE, new MessageHandler<ConnectingCancelledMessage>() {
            @Override public void handleMessage(ConnectingCancelledMessage message) throws Exception {
                mState = STATE_DISCONNECTED;
                notifyChanged();
            }
        })
        .put(ConnectingFailedMessage.TYPE, new MessageHandler<ConnectingFailedMessage>() {
            @Override public void handleMessage(ConnectingFailedMessage message) throws Exception {
                mState = STATE_DISCONNECTED;
                notifyChanged();
            }
        })
        .put(ConnectedMessage.TYPE, new MessageHandler<ConnectedMessage>() {
            @Override public void handleMessage(ConnectedMessage message) throws Exception {
                // nop, just means the socket is established, wait for connecting_finished
            }
        })
        .put(ConnectingFinishedMessage.TYPE, new MessageHandler<ConnectingFinishedMessage>() {
            @Override public void handleMessage(ConnectingFinishedMessage message) throws Exception {
                mState = STATE_CONNECTED;
                notifyChanged();
            }
        })
        .put(SocketClosedMessage.TYPE, new MessageHandler<SocketClosedMessage>() {
            @Override public void handleMessage(SocketClosedMessage message) throws Exception {
                mState = STATE_DISCONNECTED;
                notifyChanged();
            }
        })
        .put(InvalidCertMessage.TYPE, new MessageHandler<InvalidCertMessage>() {
            @Override public void handleMessage(InvalidCertMessage message) throws Exception {
                Context appContext = TapchatApp.get();

                Intent intent = new Intent(TapchatApp.ACTION_INVALID_CERT);
                intent.putExtra(InvalidConnectionCertActivity.EXTRA_CID,         message.cid);
                intent.putExtra(InvalidConnectionCertActivity.EXTRA_HOSTNAME,    message.hostname);
                intent.putExtra(InvalidConnectionCertActivity.EXTRA_FINGERPRINT, message.fingerprint);
                intent.putExtra(InvalidConnectionCertActivity.EXTRA_ERROR,       message.error);
                appContext.sendBroadcast(intent, null);
            }
        })
        .put(DeleteBufferMessage.TYPE, new MessageHandler<DeleteBufferMessage>() {
            @Override public void handleMessage(DeleteBufferMessage message) throws Exception {
                Buffer buffer = getBuffer(message.bid);
                removeBuffer(buffer);
            }
        })
        .put(BufferArchivedMessage.TYPE, new MessageHandler<BufferArchivedMessage>() {
            @Override public void handleMessage(BufferArchivedMessage message) throws Exception {
                Buffer buffer = getBuffer(message.bid);
                buffer.setArchived(true);
                mService.postToBus(new BufferChangedEvent(buffer));
            }
        })
        .put(BufferUnarchivedMessage.TYPE, new MessageHandler<BufferUnarchivedMessage>() {
            @Override public void handleMessage(BufferUnarchivedMessage message) throws Exception {
                long bid = message.bid;
                Buffer buffer = getBuffer(bid);
                buffer.setArchived(false);
                mService.postToBus(new BufferChangedEvent(buffer));
            }
        })
        .build();

    private void removeBuffer(Buffer buffer) {
        buffer.notifyRemoved();

        synchronized (mBuffers) {
            mBuffers.remove(buffer.getId());
        }

        mService.postToBus(new BufferRemovedEvent(buffer));
    }

    private void updateDetails(ServerDetailsChangedMessage message) throws JSONException {
        mExists   = true;
        mName     = message.name;
        mNick     = message.nick;
        mRealName = message.realname;
        mHostName = message.hostname;
        mPort     = message.port;

        mSSL = message.ssl;

        mPassword = message.server_pass;

        mIsBacklog = (mService.getConnectionState() != TapchatService.STATE_LOADED);

        if (message.disconnected) {
            // FIXME: What if the state should be "connecting"?
            mState = STATE_DISCONNECTED;
        } else {
            mState = STATE_CONNECTED;
        }

        notifyChanged();
    }

    public void serviceDisconnected() {
        mExists = false;

        synchronized (mBuffers) {
            for (Buffer buffer : mBuffers.values()) {
                buffer.serviceDisconnected();
            }
        }
    }

    public boolean exists() {
        return mExists;
    }
}
