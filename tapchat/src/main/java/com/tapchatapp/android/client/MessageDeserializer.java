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

import android.util.Log;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tapchatapp.android.client.message.AwayMessage;
import com.tapchatapp.android.client.message.BacklogCompleteMessage;
import com.tapchatapp.android.client.message.BannedMessage;
import com.tapchatapp.android.client.message.BufferArchivedMessage;
import com.tapchatapp.android.client.message.BufferMeMsgMessage;
import com.tapchatapp.android.client.message.BufferMsgMessage;
import com.tapchatapp.android.client.message.BufferUnarchivedMessage;
import com.tapchatapp.android.client.message.ChannelInitMessage;
import com.tapchatapp.android.client.message.ChannelModeIsMessage;
import com.tapchatapp.android.client.message.ChannelModeMessage;
import com.tapchatapp.android.client.message.ChannelTimestampMessage;
import com.tapchatapp.android.client.message.ChannelTopicMessage;
import com.tapchatapp.android.client.message.ChannelUrlMessage;
import com.tapchatapp.android.client.message.ConnectedMessage;
import com.tapchatapp.android.client.message.ConnectingCancelledMessage;
import com.tapchatapp.android.client.message.ConnectingFailedMessage;
import com.tapchatapp.android.client.message.ConnectingFinishedMessage;
import com.tapchatapp.android.client.message.ConnectingMessage;
import com.tapchatapp.android.client.message.ConnectingRetryMessage;
import com.tapchatapp.android.client.message.ConnectionDeletedMessage;
import com.tapchatapp.android.client.message.EndOfBacklogMessage;
import com.tapchatapp.android.client.message.ErrorMessage;
import com.tapchatapp.android.client.message.HeaderMessage;
import com.tapchatapp.android.client.message.HeartbeatEchoMessage;
import com.tapchatapp.android.client.message.IdleMessage;
import com.tapchatapp.android.client.message.InvalidCertMessage;
import com.tapchatapp.android.client.message.JoinedChannelMessage;
import com.tapchatapp.android.client.message.KickedChannelMessage;
import com.tapchatapp.android.client.message.MakeBufferMessage;
import com.tapchatapp.android.client.message.MakeServerMessage;
import com.tapchatapp.android.client.message.Message;
import com.tapchatapp.android.client.message.NickchangeMessage;
import com.tapchatapp.android.client.message.NoticeMessage;
import com.tapchatapp.android.client.message.OobIncludeMessage;
import com.tapchatapp.android.client.message.OpenBufferMessage;
import com.tapchatapp.android.client.message.PartedChannelMessage;
import com.tapchatapp.android.client.message.QuitServerMessage;
import com.tapchatapp.android.client.message.ResponseMessage;
import com.tapchatapp.android.client.message.ServerDetailsChangedMessage;
import com.tapchatapp.android.client.message.SocketClosedMessage;
import com.tapchatapp.android.client.message.StatUserMessage;
import com.tapchatapp.android.client.message.SysMsgsMessage;
import com.tapchatapp.android.client.message.UnknownMessage;
import com.tapchatapp.android.client.message.UserAwayMessage;
import com.tapchatapp.android.client.message.UserBackMessage;
import com.tapchatapp.android.client.message.UserChannelModeMessage;
import com.tapchatapp.android.client.message.UserDetailsMessage;
import com.tapchatapp.android.client.message.UserModeMessage;
import com.tapchatapp.android.client.message.WaitingToRetryMessage;
import com.tapchatapp.android.client.message.YouJoinedChannelMessage;
import com.tapchatapp.android.client.message.YouNickchangeMessage;
import com.tapchatapp.android.client.message.YouPartedChannelMessage;
import com.tapchatapp.android.client.message.request.QuitMessage;

import java.lang.reflect.Type;
import java.util.Map;

public class MessageDeserializer implements JsonDeserializer<Message> {

    private static final Map<String, Class<? extends Message>> TYPES
        = ImmutableMap.<String, Class<? extends Message>>builder()
            .put(AwayMessage.TYPE, AwayMessage.class)
            .put(BacklogCompleteMessage.TYPE, BacklogCompleteMessage.class)
            .put(BannedMessage.TYPE, BannedMessage.class)
            .put(BufferArchivedMessage.TYPE, BufferArchivedMessage.class)
            .put(BufferMeMsgMessage.TYPE, BufferMeMsgMessage.class)
            .put(BufferMsgMessage.TYPE, BufferMsgMessage.class)
            .put(BufferUnarchivedMessage.TYPE, BufferUnarchivedMessage.class)
            .put(ChannelInitMessage.TYPE, ChannelInitMessage.class)
            .put(ChannelModeIsMessage.TYPE, ChannelModeIsMessage.class)
            .put(ChannelModeMessage.TYPE, ChannelModeMessage.class)
            .put(ChannelTimestampMessage.TYPE, ChannelTimestampMessage.class)
            .put(ChannelTopicMessage.TYPE, ChannelTopicMessage.class)
            .put(ChannelUrlMessage.TYPE, ChannelUrlMessage.class)
            .put(ConnectedMessage.TYPE, ConnectedMessage.class)
            .put(ConnectingCancelledMessage.TYPE, ConnectingCancelledMessage.class)
            .put(ConnectingFailedMessage.TYPE, ConnectingFailedMessage.class)
            .put(ConnectingFinishedMessage.TYPE, ConnectingFinishedMessage.class)
            .put(ConnectingMessage.TYPE, ConnectingMessage.class)
            .put(ConnectingRetryMessage.TYPE, ConnectingRetryMessage.class)
            .put(ConnectionDeletedMessage.TYPE, ConnectionDeletedMessage.class)
            .put(EndOfBacklogMessage.TYPE, EndOfBacklogMessage.class)
            .put(ErrorMessage.TYPE, ErrorMessage.class)
            .put(HeaderMessage.TYPE, HeaderMessage.class)
            .put(HeartbeatEchoMessage.TYPE, HeartbeatEchoMessage.class)
            .put(IdleMessage.TYPE, IdleMessage.class)
            .put(InvalidCertMessage.TYPE, InvalidCertMessage.class)
            .put(JoinedChannelMessage.TYPE, JoinedChannelMessage.class)
            .put(KickedChannelMessage.TYPE, KickedChannelMessage.class)
            .put(MakeBufferMessage.TYPE, MakeBufferMessage.class)
            .put(MakeServerMessage.TYPE, MakeServerMessage.class)
            .put(NickchangeMessage.TYPE, NickchangeMessage.class)
            .put(NoticeMessage.TYPE, NoticeMessage.class)
            .put(OobIncludeMessage.TYPE, OobIncludeMessage.class)
            .put(OpenBufferMessage.TYPE, OpenBufferMessage.class)
            .put(PartedChannelMessage.TYPE, PartedChannelMessage.class)
            .put(QuitMessage.TYPE, QuitMessage.class)
            .put(QuitServerMessage.TYPE, QuitServerMessage.class)
            .put(ServerDetailsChangedMessage.TYPE, ServerDetailsChangedMessage.class)
            .put(SocketClosedMessage.TYPE, SocketClosedMessage.class)
            .put(StatUserMessage.TYPE, StatUserMessage.class)
            .put(SysMsgsMessage.TYPE, SysMsgsMessage.class)
            .put(UserAwayMessage.TYPE, UserAwayMessage.class)
            .put(UserBackMessage.TYPE, UserBackMessage.class)
            .put(UserChannelModeMessage.TYPE, UserChannelModeMessage.class)
            .put(UserDetailsMessage.TYPE, UserDetailsMessage.class)
            .put(UserModeMessage.TYPE, UserModeMessage.class)
            .put(WaitingToRetryMessage.TYPE, WaitingToRetryMessage.class)
            .put(YouJoinedChannelMessage.TYPE, YouJoinedChannelMessage.class)
            .put(YouNickchangeMessage.TYPE, YouNickchangeMessage.class)
            .put(YouPartedChannelMessage.TYPE, YouPartedChannelMessage.class)
            .build();

    @Override public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        Log.d("MessageDeserializer", "DESERIALIZE: " + json.toString());

        JsonObject jsonObject = json.getAsJsonObject();

        Class<? extends Message> klass;

        if (jsonObject.has("_reqid") && !jsonObject.get("_reqid").isJsonNull()) {
           klass = ResponseMessage.class;
        } else {
            JsonElement messageType = jsonObject.get("type");
            if (messageType != null) {
                String type = messageType.getAsString();
                klass = TYPES.get(type);
                if (klass == null) {
                    klass = UnknownMessage.class;
                }
            } else {
                klass = UnknownMessage.class;
            }
        }

        Message message = context.deserialize(json, klass);
        message.setOriginalJson(json.toString());
        return message;
    }
}
