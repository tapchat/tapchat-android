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

package com.tapchatapp.android.client;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.event.BufferSelectedEvent;
import com.tapchatapp.android.app.event.ConnectionAddedEvent;
import com.tapchatapp.android.app.event.ConnectionRemovedEvent;
import com.tapchatapp.android.app.event.ServiceDestroyedEvent;
import com.tapchatapp.android.app.event.ServiceErrorEvent;
import com.tapchatapp.android.app.event.ServiceReadyEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.client.message.BacklogCompleteMessage;
import com.tapchatapp.android.client.message.ConnectionDeletedMessage;
import com.tapchatapp.android.client.message.HeaderMessage;
import com.tapchatapp.android.client.message.HeartbeatEchoMessage;
import com.tapchatapp.android.client.message.IdleMessage;
import com.tapchatapp.android.client.message.MakeServerMessage;
import com.tapchatapp.android.client.message.Message;
import com.tapchatapp.android.client.message.OobIncludeMessage;
import com.tapchatapp.android.client.message.ResponseMessage;
import com.tapchatapp.android.client.message.StatUserMessage;
import com.tapchatapp.android.client.message.SysMsgsMessage;
import com.tapchatapp.android.client.message.request.AddServerMessage;
import com.tapchatapp.android.client.message.request.HeartbeatMessage;
import com.tapchatapp.android.client.model.Buffer;
import com.tapchatapp.android.client.model.Connection;
import com.tapchatapp.android.client.model.HeartbeatState;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import javax.inject.Inject;

import retrofit.client.Response;

public class TapchatService extends Service implements TapchatBouncerConnection.Callback {

    private static final String TAG = "TapchatService";

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_LOADING = 3;
    public static final int STATE_LOADED = 4;

    private static final int RECOMMENDED_SERVER_VERSION = 41;

    private final IBinder mBinder = new LocalBinder();
    private final List<Message> mMessageCache = Lists.newArrayList();
    private final Map<Integer, PostCallbackInfo> mResponseHandlers = Maps.newHashMap();
    private final Map<Long, Connection> mConnections = Collections.synchronizedMap(new TreeMap<Long, Connection>());

    private final Map<String, MessageHandler> mMessageHandlers = ImmutableMap.<String, MessageHandler>builder()
            .put(HeaderMessage.TYPE, new MessageHandler<HeaderMessage>() {
                @Override public void handleMessage(HeaderMessage message) throws Exception {
                    // FIXME: mTimeOffset = new Date() - message.getLong("time");
                    // mMaxIdle = message.getLong("idle_interval");
                    mLoadingOobBacklog = false;
                    setConnectionState(STATE_LOADING);

                    if (!TextUtils.isEmpty(message.version_name) && message.version_code != null) {
                        mServerVersionName = message.version_name;
                        mServerVersionCode = message.version_code;
                    }

                    TapchatApp app = TapchatApp.get();
                    if (!TextUtils.isEmpty(message.push_id) && !TextUtils.isEmpty(message.push_key)) {
                        app.setPushInfo(message.push_id, message.push_key);
                    } else {
                        app.setPushInfo(null, null);
                    }
                }
            })
            .put(StatUserMessage.TYPE, new MessageHandler<StatUserMessage>() {
                @Override public void handleMessage(StatUserMessage message) throws Exception {
                    // FIXME: Store user info
                    mActiveConnections = message.num_active_connections;
                }
            })
            .put(OobIncludeMessage.TYPE, new MessageHandler<OobIncludeMessage>() {
                @Override public void handleMessage(OobIncludeMessage message) throws Exception {

                    oobInclude(message.url);
                }
            })
            .put(BacklogCompleteMessage.TYPE, new MessageHandler<BacklogCompleteMessage>() {
                @Override public void handleMessage(BacklogCompleteMessage message) throws Exception {
                    for (Connection connection : mConnections.values()) {
                        if (!connection.exists()) {
                            Log.d(TAG, "Removing deleted connection! " + connection.getId() + " "
                                    + connection.getDisplayName());
                            removeConnection(connection);
                        }
                    }
                    setConnectionState(STATE_LOADED);
                    startHeartbeat();
                }
            })
            .put(MakeServerMessage.TYPE, new MessageHandler<MakeServerMessage>() {
                @Override public void handleMessage(MakeServerMessage message) throws Exception {
                    Connection connection = mConnections.get(message.cid);
                    if (connection != null) {
                        Log.i("Connection", "Re-using connection!");
                        connection.reload(message);
                    } else {
                        connection = new Connection(TapchatService.this, message);
                        mConnections.put(connection.getId(), connection);
                        mBus.post(new ConnectionAddedEvent(connection));
                    }
                }
            })
            .put(ConnectionDeletedMessage.TYPE, new MessageHandler<ConnectionDeletedMessage>() {
                @Override public void handleMessage(ConnectionDeletedMessage message) throws Exception {
                    Connection connection = mConnections.get(message.cid);
                    removeConnection(connection);
                }
            })
            .put(HeartbeatEchoMessage.TYPE, new MessageHandler<HeartbeatEchoMessage>() {
                @Override public void handleMessage(HeartbeatEchoMessage message) throws Exception {
                    Map<String, Map<String, Long>> seenEids = message.seenEids;
                    for (String cid : seenEids.keySet()) {
                        Connection connection = mConnections.get(Long.valueOf(cid));
                        if (connection != null) {
                            Map<String, Long> buffers = seenEids.get(cid);
                            for (String bid : buffers.keySet()) {
                                Buffer buffer = connection.getBuffer(Long.valueOf(bid));
                                if (buffer != null) {
                                    buffer.markRead(buffers.get(bid));
                                }
                            }
                        }
                    }
                }
            })
            .put(IdleMessage.TYPE, new MessageHandler<IdleMessage>() {
                @Override public void handleMessage(IdleMessage message) throws Exception {
                    // Ignore, mLastMessageAt will still be updated above.
                }
            })
            .put(SysMsgsMessage.TYPE, new MessageHandler<SysMsgsMessage>() {
                @Override public void handleMessage(SysMsgsMessage message) throws Exception {
                    // FIXME
                    // {"bid":-1,"eid":-1,"type":"sys_msgs","time":1332374270,"highlight":false,"hardzombie":1332021930}
                }
            })
            .build();

    private int mReqId = 0;
    private boolean mDebug;
    private int mConnectionState;
    private int mActiveConnections;
    private boolean mLoadingOobBacklog;
    private int mServerVersionCode = -1;
    private Buffer mSelectedBuffer;
    private Date mLastMessageAt;
    private Handler mHandler;
    private HeartbeatState mState;
    private String mServerVersionName;
    private TapchatBouncerConnection mBouncerConnection;
    private Timer mHeartbeatTimer;

    @Inject Bus mBus;
    @Inject Gson mGson;
    @Inject TapchatAPI mAPI;
    @Inject TapchatSession mSession;

    public void addServer(String name, String hostname, String nickname, String port, String realname, boolean useSSL,
                         String password, TapchatService.PostCallback callback) {

        AddServerMessage message = new AddServerMessage();
        message.name = name;
        message.hostname = hostname;
        message.nickname = nickname;
        message.port = port;
        message.realname = realname;
        message.ssl = useSSL ? "1" : "0";
        message.server_pass = password;
        post(message, callback);
    }

    public void connect() {
        if (mConnectionState != STATE_DISCONNECTED)
            return;

        setConnectionState(STATE_CONNECTING);

        if (mBouncerConnection == null) {
            mBouncerConnection = new TapchatBouncerConnection(mSession, this);
        }

        mBouncerConnection.start();
    }

    public void disconnect() {
        if (mConnectionState == STATE_DISCONNECTED)
            return;

        setConnectionState(STATE_DISCONNECTED);
        mConnections.clear();
        if (mHeartbeatTimer != null) {
            mHeartbeatTimer.cancel();
            mHeartbeatTimer = null;
        }
        if (mBouncerConnection != null) {
            mBouncerConnection.stop();
            mBouncerConnection = null;
        }
    }

    public Buffer getBuffer(long connectionId, long bufferId) {
        Connection connection = getConnection(connectionId);
        if (connection == null) {
            return null;
        }
        return connection.getBuffer(bufferId);
    }

    public Connection getConnection(long id) {
        return mConnections.get(id);
    }

    public int getConnectionCount() {
        return mConnections.size();
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    private void setConnectionState(int state) {
        mConnectionState = state;

        if (state == TapchatService.STATE_DISCONNECTED) {
            synchronized (mConnections) {
                for (Connection connection : mConnections.values()) {
                    connection.serviceDisconnected();
                }
            }
        }

        mBus.post(new ServiceStateChangedEvent(this));
    }

    public List<Connection> getConnections() {
        return new ArrayList<>(mConnections.values());
    }

    public int getNextReqId() {
        return ++mReqId;
    }

    public Buffer getSelectedBuffer() {
        return mSelectedBuffer;
    }

    public boolean isServerOutdated() {
        return mServerVersionCode != -1 && mServerVersionCode < RECOMMENDED_SERVER_VERSION;
    }

    public void logout() {
        disconnect();
        TapchatApp.get().setLoggedOut();
    }

    public void post(Message message, PostCallback callback) {
        message.session = mSession.getSessionId();
        message._reqid = getNextReqId();

        mResponseHandlers.put(message._reqid, new PostCallbackInfo(message, callback));

        if (mBouncerConnection == null) {
            throw new IllegalStateException("No connection");
        }

        Log.d(TAG, "Sending: " + message.toString());
        mBouncerConnection.send(message);
    }

    public void postToBus(Object event) {
        mBus.post(event);
    }

    public void updateLoadingProgress() {
        int numFinished = 0;
        synchronized (mConnections) {
            for (Connection conn : mConnections.values()) {
                if (!conn.isBacklog())
                    numFinished++;
            }
        }
        // numFinished mActiveConnections
    }

    @Override public void onBouncerConnect() {
        setConnectionState(STATE_CONNECTED);
    }

    @Override public void onBouncerReceiveMessage(Message message) {
        processMessage(message, false);
    }

    @Override public void onBouncerError(Exception ex) {
        handleError(ex);
    }

    @Override public void onBouncerDisconnect() {
        setConnectionState(STATE_DISCONNECTED);
        if (mHeartbeatTimer != null) {
            mHeartbeatTimer.cancel();
            mHeartbeatTimer = null;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        TapchatApp.get().inject(this);

        mHandler = new Handler();

        mDebug = TapchatApp.get().getPreferences().getBoolean(TapchatApp.PREF_DEBUG, false);

        if (!TapchatApp.get().isConfigured()) {
            throw new RuntimeException("Server was started before being configured!");
        }

        mBus.register(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String sessionId = prefs.getString(TapchatApp.PREF_SESSION_ID, null);
        String hostname = prefs.getString(TapchatApp.PREF_SERVER_HOST, null);
        int port = prefs.getInt(TapchatApp.PREF_SERVER_PORT, -1);

        Uri.Builder builder = new Uri.Builder()
            .scheme("https")
            .encodedAuthority(String.format("%s:%s", Uri.encode(hostname), port));

        mSession.setSessionId(sessionId);
        mSession.setUri(builder.build());

        connect();

        mBus.post(new ServiceReadyEvent(this));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy!");
        disconnect();

        mBus.post(new ServiceDestroyedEvent(this));
        mBus.unregister(this);
    }

    @Override public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind!");
        return false;
    }

    @Override public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind!");
        return mBinder;
    }

    @Override public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "onRebind!");
    }

    @Produce public ServiceReadyEvent produceServiceReadyEvent() {
        return new ServiceReadyEvent(this);
    }

    @Produce public ServiceStateChangedEvent produceServiceStateChangedEvent() {
        if (mConnectionState != STATE_DISCONNECTED) {
            return new ServiceStateChangedEvent(this);
        }
        return null;
    }

    @Subscribe public void onBufferSelected(BufferSelectedEvent event) {
        Buffer buffer = getBuffer(event.getConnectionId(), event.getBufferId());
        if (buffer == null) {
            mSelectedBuffer = null;
            return;
        }
        if (event.isSelected()) {
            mSelectedBuffer = buffer;
            mSelectedBuffer.markAllRead();
        } else {
            if (mSelectedBuffer == buffer) {
                mSelectedBuffer = null;
            }
        }
    }

    private synchronized void processMessage(Message message, boolean oob) {
        try {
            if (message.error != null && message.error.equals("temp_unavailable")) {
                throw new Exception("temporarily unavailable");
            }

            if ((!mLoadingOobBacklog) || oob) {
                handleMessage(message);
            } else {
                cacheMessage(message);
            }
        } catch (Exception ex) {
            handleError(ex);
        }
    }

    private synchronized void handleMessage(Message message) throws Exception {
        mLastMessageAt = new Date();

        if (mDebug) {
            Log.d(TAG, "Got message: " + message.getOriginalJson());
        }

        if (message._reqid != null) {
            ResponseMessage responseMessage = (ResponseMessage) message;
            int reqid = responseMessage._reqid;

            if (responseMessage.msg != null) {
                message = responseMessage.msg;
            }

            if (mResponseHandlers.containsKey(reqid)) {
                final PostCallbackInfo info = mResponseHandlers.remove(reqid);

                if (message.cid != null) {
                    Connection connection = getConnection(message.cid);
                    if (connection != null) {
                        connection.handleResponse(responseMessage, info.request);
                    }
                }

                if (info.callback != null) {
                    final ResponseMessage finalMessage = responseMessage;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            info.callback.run(finalMessage, info.request);
                        }
                    });
                }
            }
            return;
        }

        String type = message.type;

        if (message.type == null) {
            Log.w(TAG, "Message has no type: " + message);
            return;
        }

        if (mMessageHandlers.containsKey(type)) {
            mMessageHandlers.get(type).handleMessage(message);
        }

        if (message.cid != null) {
            Connection connection = mConnections.get(message.cid);
            if (connection != null) {
                connection.processMessage(message);
            }
        }
    }

    private void oobInclude(String path) throws Exception {
        mLoadingOobBacklog = true;

        Response response = mAPI.oobInclude(path.substring(1));

        JsonReader reader = new JsonReader(new InputStreamReader(response.getBody().in()));
        reader.beginArray();
        while (reader.hasNext()) {
            Message message = mGson.fromJson(reader, Message.class);
            handleMessage(message);
        }
        reader.endArray();
        reader.close();

        mLoadingOobBacklog = false;
        handleMessageCache();
    }

    private void cacheMessage(Message message) {
        mMessageCache.add(message);
    }

    private void handleMessageCache() throws Exception {
        for (Message message : mMessageCache) {
            handleMessage(message);
        }
        mMessageCache.clear();
    }

    private void handleError(final Exception ex) {
        Log.e("TapchatService", "ERROR!!!", ex);
        disconnect();
        mBus.post(new ServiceErrorEvent(ex));
    }

    private void startHeartbeat() {
        mState = getHeartbeatState();
        if (mHeartbeatTimer != null) {
            mHeartbeatTimer.cancel();
        }
        mHeartbeatTimer = new Timer();
        mHeartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendHeartbeat();
            }
        }, 0, 2000);
    }

    private void sendHeartbeat() {
        HeartbeatState newState = getHeartbeatState();
        Map stateDiff = diffHeartbeatState(mState, newState);
        mState = newState;
        if (!stateDiff.isEmpty()) {
            HeartbeatMessage message = new HeartbeatMessage();

            Map<String, Map<String, Long>> seenEids = (Map<String, Map<String, Long>>) stateDiff.get("seenEids");
            if (seenEids != null) {
                message.seenEids = seenEids;
            } else {
                // IRCCloud wants this
                message.seenEids = new HashMap<>();
            }

            if (mSelectedBuffer != null) {
                message.selectedBuffer = mSelectedBuffer.getId();
            }

            if (mBouncerConnection == null) {
                // Race condition with the timer.
                return;
            }

            post(message, null);
        }
    }

    private HeartbeatState getHeartbeatState() {
        HeartbeatState state = new HeartbeatState();

        synchronized (mConnections) {
            for (Connection connection : mConnections.values()) {
                Map<String, Long> connObj = new HashMap<>();
                for (Buffer buffer : connection.getBuffers()) {
                    connObj.put(String.valueOf(buffer.getId()), buffer.getLastSeenEid());
                }
                state.seenEids.put(String.valueOf(connection.getId()), connObj);
            }
        }
        if (mSelectedBuffer != null) {
            state.selectedBuffer = mSelectedBuffer.getId();
        }
        return state;
    }

    // FIXME: ughhhh
    private Map diffHeartbeatState(HeartbeatState oldState, HeartbeatState newState) {
        Map<String, Object> diffState = new HashMap<>();

        if (!Objects.equal(newState.selectedBuffer, oldState.selectedBuffer)) {
            diffState.put("selectedBuffer", newState.selectedBuffer);
        }

        Map<String, Map<String, Long>> newSeenEids = newState.seenEids;
        if (newSeenEids != null) {
            if (oldState.seenEids == null) {
                diffState.put("seenEids", newSeenEids);
            } else {
                Map<String, Object> diffSeenEids = new HashMap<>();

                Map<String, Map<String, Long>> oldSeenEids = oldState.seenEids;

                for (String cid : newSeenEids.keySet()) {
                    Map<String, Long> newConnectionEids = newSeenEids.get(cid);
                    Map<String, Long> oldConnectionEids = oldSeenEids.get(cid);
                    if (!oldSeenEids.containsKey(cid)) {
                        diffSeenEids.put(cid, newConnectionEids);
                    } else {
                        Map<String, Long> diffConnectionEids = (Map<String, Long>) diffState.get(cid);

                        Map<String, Long> newBuffers = newSeenEids.get(cid);
                        for (String bid : newBuffers.keySet()) {
                            long newBufferSeenEid = newConnectionEids.containsKey(bid) ? newConnectionEids.get(bid) : 0;
                            long oldBufferSeenEid = oldConnectionEids.containsKey(bid) ? oldConnectionEids.get(bid) : 0;
                            if (newBufferSeenEid != oldBufferSeenEid) {
                                if (diffConnectionEids == null) {
                                    diffConnectionEids = new HashMap<>();
                                    diffSeenEids.put(cid, diffConnectionEids);
                                }
                                diffConnectionEids.put(bid, newBufferSeenEid);
                            }
                        }
                    }
                }

                if (!diffSeenEids.isEmpty()) {
                    diffState.put("seenEids", diffSeenEids);
                }
            }
        }

        return diffState;
    }

    private void removeConnection(Connection connection) {
        mConnections.remove(connection.getId());
        mBus.post(new ConnectionRemovedEvent(connection));
    }

    public interface PostCallback {
        public void run(ResponseMessage message, Message request);
    }

    public class LocalBinder extends Binder {
        public TapchatService getService() {
            return TapchatService.this;
        }
    }

    private class PostCallbackInfo {
        public final Message request;
        public final PostCallback callback;

        public PostCallbackInfo(Message request, PostCallback callback) {
            this.request = request;
            this.callback = callback;
        }
    }
}
