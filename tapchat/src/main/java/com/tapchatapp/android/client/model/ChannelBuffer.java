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

import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.ImmutableMap;
import com.tapchatapp.android.client.MessageHandler;
import com.tapchatapp.android.client.message.ChannelInitMessage;
import com.tapchatapp.android.client.message.ChannelModeIsMessage;
import com.tapchatapp.android.client.message.ChannelModeMessage;
import com.tapchatapp.android.client.message.ChannelTimestampMessage;
import com.tapchatapp.android.client.message.ChannelTopicMessage;
import com.tapchatapp.android.client.message.JoinedChannelMessage;
import com.tapchatapp.android.client.message.KickedChannelMessage;
import com.tapchatapp.android.client.message.MakeBufferMessage;
import com.tapchatapp.android.client.message.NickchangeMessage;
import com.tapchatapp.android.client.message.PartedChannelMessage;
import com.tapchatapp.android.client.message.UserAwayMessage;
import com.tapchatapp.android.client.message.UserBackMessage;
import com.tapchatapp.android.client.message.UserChannelModeMessage;
import com.tapchatapp.android.client.message.UserDetailsMessage;
import com.tapchatapp.android.client.message.YouJoinedChannelMessage;
import com.tapchatapp.android.client.message.YouNickchangeMessage;
import com.tapchatapp.android.client.message.YouPartedChannelMessage;
import com.tapchatapp.android.client.message.request.QuitMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ChannelBuffer extends ChatBuffer {
    private boolean mJoined;
    private String mTopic;

    private final Map<String, Member> mMembers = Collections.synchronizedMap(new TreeMap<String, Member>());

    ChannelBuffer(Connection connection, MakeBufferMessage message) throws Exception {
        super(connection, message);
        updateDetails(message);
    }

    @Override
    public int getType() {
        return CHANNEL_TYPE;
    }

    public String getTopic() {
        return mTopic;
    }

    public boolean isJoined() {
        return mJoined;
    }

    @Override
    public boolean isActive() {
        return super.isActive() && isJoined();
    }

    public ArrayList<Member> getMembers() {
        synchronized (mMembers) {
            return new ArrayList<Member>(mMembers.values());
        }
    }

    public void join() {
        getConnection().join(getName(), null);
    }

    public void part() {
        getConnection().part(getName(), null);
    }

    @Override
    public void reload(MakeBufferMessage message) {
        super.reload(message);
        updateDetails(message);
    }

    private void updateDetails(MakeBufferMessage message) {
        /*
        {
            "bid":17909,
            "eid":-1,
            "type":"makebuffer",
            "time":-1,
            "highlight":false,
            "name":"#test",
            "buffer_type":"channel",
            "cid":2135,
            "max_eid":503,
            "focus":false,
            "last_seen_eid":389,
            "joined":false,
            "hidden":true,
            "backlog_length":100
        }
         */

        mJoined = message.joined;
        notifyChanged();
    }

    @Override
    protected Map<String, MessageHandler> getMessageHandlers() {
        return new ImmutableMap.Builder<String, MessageHandler>()
            .put(ChannelInitMessage.TYPE, new MessageHandler<ChannelInitMessage>() {
                @Override public void handleMessage(ChannelInitMessage message) throws Exception {
                    ChannelTopic topic = message.topic;
                    if (!TextUtils.isEmpty("topic_text")) {
                        mTopic = topic.topic_text;
                    } else if (!TextUtils.isEmpty(topic.text)) {
                        mTopic = topic.text;
                    }

                    synchronized (mMembers) {
                        List<ChannelInitMessage.Member> members = message.members;
                        for (ChannelInitMessage.Member member : members) {
                            mMembers.put(member.nick, new Member(member.nick));
                        }
                    }

                    mJoined = true; // FIXME ?

                    notifyChanged();
                }
            })
            .put(ChannelTimestampMessage.TYPE, new MessageHandler<ChannelTimestampMessage>() {
                @Override public void handleMessage(ChannelTimestampMessage message) throws Exception {
                    // FIXME:
                }
            })
            .put(UserAwayMessage.TYPE, new MessageHandler<UserAwayMessage>() {
                @Override public void handleMessage(UserAwayMessage message) throws Exception {
                    // FIXME:
                }
            })
            .put(UserBackMessage.TYPE, new MessageHandler<UserBackMessage>() {
                @Override public void handleMessage(UserBackMessage message) throws Exception {
                    // FIXME:
                }
            })
            .put(UserDetailsMessage.TYPE, new MessageHandler<UserDetailsMessage>() {
                @Override public void handleMessage(UserDetailsMessage message) throws Exception {
                    // FIXME:
                }
            })
            .build();
    }

    @Override
    protected Map<String, MessageHandler> getInitializedMessageHandlers() {
        return new ImmutableMap.Builder<String, MessageHandler>()
            .put(ChannelTopicMessage.TYPE, new MessageHandler<ChannelTopicMessage>() {
                @Override public void handleMessage(ChannelTopicMessage message) throws Exception {
                    mTopic = message.topic;
                    notifyChanged();
                }
            })
            .put(UserChannelModeMessage.TYPE, new MessageHandler<UserChannelModeMessage>() {
                @Override public void handleMessage(UserChannelModeMessage message) throws Exception {
                    // FIXME: mMembers.get(message.getString("nick")).setMode(message);
                }
            })
            .put(ChannelModeMessage.TYPE, new MessageHandler<ChannelModeMessage>() {
                @Override public void handleMessage(ChannelModeMessage message) throws Exception {
                    // FIXME:
                    // {"bid":106792,"eid":40,"type":"channel_mode","time":1332377704,"highlight":false,"channel":"#iv","server":"efnet.xs4all.nl","cid":13599,"diff":"+nt","newmode":"nt","ops":{"add":[{"mode":"t","param":""},{"mode":"n","param":""}],"remove":[]}}
                }
            })
            .put(ChannelModeIsMessage.TYPE, new MessageHandler<ChannelModeIsMessage>() {
                @Override public void handleMessage(ChannelModeIsMessage message) throws Exception {
                    // FIXME
                }
            })
            .put(JoinedChannelMessage.TYPE, new MessageHandler<JoinedChannelMessage>() {
                @Override public void handleMessage(JoinedChannelMessage message) throws Exception {
                    addMember(new Member(message.nick));
                }
            })
            .put(PartedChannelMessage.TYPE, new MessageHandler<PartedChannelMessage>() {
                @Override public void handleMessage(PartedChannelMessage message) throws Exception {
                    removeMember(message.nick);
                }
            })
            .put(QuitMessage.TYPE, new MessageHandler<QuitMessage>() {
                @Override public void handleMessage(QuitMessage message) throws Exception {
                    if (!TextUtils.isEmpty(message.nick)) {
                        removeMember(message.nick);
                    }
                }
            })
            .put(KickedChannelMessage.TYPE, new MessageHandler<KickedChannelMessage>() {
                @Override public void handleMessage(KickedChannelMessage message) throws Exception {
                    removeMember(message.nick);
                }
            })
            .put(YouJoinedChannelMessage.TYPE, new MessageHandler<YouJoinedChannelMessage>() {
                @Override public void handleMessage(YouJoinedChannelMessage message) throws Exception {
                    mJoined = true;
                    notifyChanged();
                }
            })
            .put(YouPartedChannelMessage.TYPE, new MessageHandler<YouPartedChannelMessage>() {
                @Override public void handleMessage(YouPartedChannelMessage message) throws Exception {
                    mJoined = false;
                    notifyChanged();
                }
            })
            .put(NickchangeMessage.TYPE, new MessageHandler<NickchangeMessage>() {
                @Override public void handleMessage(NickchangeMessage message) throws Exception {
                    updateMemberNick(message);
                }
            })
            .put(YouNickchangeMessage.TYPE, new MessageHandler<YouNickchangeMessage>() {
                @Override public void handleMessage(YouNickchangeMessage message) throws Exception {
                    updateMemberNick(message);
                }
            })
            .build();
    }

    private void updateMemberNick(NickchangeMessage message) throws Exception {
        String oldNick = message.oldnick;
        String newNick = message.newnick;
        synchronized (mMembers) {
            Member member = mMembers.get(oldNick);
            if (member != null) {
                member.setNick(newNick);
                mMembers.remove(oldNick);
                mMembers.put(newNick, member);
            } else {
                // FIXME: Why is this happening?!
                Log.w("ChannelBuffer", "Couldn't find member for nickchange!");
            }
        }
    }

    private void addMember(Member member) {
        synchronized (mMembers) {
            mMembers.put(member.getNick(), member);
        }
        // FIXME: Fire onChannelMemberAdded event
        notifyChanged();
    }

    private void removeMember(String nick) {
        synchronized (mMembers) {
            mMembers.remove(nick);
        }
        // FIXME: Fire onChannelMemberRemoved event
        notifyChanged();
    }

    public boolean isInChannel(String nick) {
        synchronized (mMembers) {
            return mMembers.containsKey(nick);
        }
    }
}
