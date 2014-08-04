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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Pair;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.tapchatapp.android.R;
import com.tapchatapp.android.client.message.AwayMessage;
import com.tapchatapp.android.client.message.BannedMessage;
import com.tapchatapp.android.client.message.BufferEventMessage;
import com.tapchatapp.android.client.message.BufferMeMsgMessage;
import com.tapchatapp.android.client.message.BufferMsgMessage;
import com.tapchatapp.android.client.message.ChannelModeIsMessage;
import com.tapchatapp.android.client.message.ChannelModeMessage;
import com.tapchatapp.android.client.message.ChannelTimestampMessage;
import com.tapchatapp.android.client.message.ChannelTopicMessage;
import com.tapchatapp.android.client.message.ChannelUrlMessage;
import com.tapchatapp.android.client.message.ConnectedMessage;
import com.tapchatapp.android.client.message.ConnectingFailedMessage;
import com.tapchatapp.android.client.message.ConnectingMessage;
import com.tapchatapp.android.client.message.ConnectingRetryMessage;
import com.tapchatapp.android.client.message.JoinedChannelMessage;
import com.tapchatapp.android.client.message.KickedChannelMessage;
import com.tapchatapp.android.client.message.NickchangeMessage;
import com.tapchatapp.android.client.message.NoticeMessage;
import com.tapchatapp.android.client.message.PartedChannelMessage;
import com.tapchatapp.android.client.message.QuitServerMessage;
import com.tapchatapp.android.client.message.SocketClosedMessage;
import com.tapchatapp.android.client.message.UserChannelModeMessage;
import com.tapchatapp.android.client.message.UserModeMessage;
import com.tapchatapp.android.client.message.WaitingToRetryMessage;
import com.tapchatapp.android.client.message.YouJoinedChannelMessage;
import com.tapchatapp.android.client.message.YouNickchangeMessage;
import com.tapchatapp.android.client.message.YouPartedChannelMessage;
import com.tapchatapp.android.client.message.request.QuitMessage;
import com.tapchatapp.android.client.model.BufferEvent;
import com.tapchatapp.android.client.model.BufferEventItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;

public class BufferEventRenderer {

    private static final List<String> PRESENCE_TYPES = ImmutableList.of("joined_channel", "parted_channel", "quit");

    private Context mContext;

    private boolean mIncludeTimestamp;

    private final int mNickColor;

    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("h:mm aa");

    public BufferEventRenderer(Context context) {
        this(context, false);
    }

    public BufferEventRenderer(Context context, boolean includeTimestamp) {
        mContext = context;
        mIncludeTimestamp = includeTimestamp;

        TypedArray typedArray = context.obtainStyledAttributes(R.styleable.Tapchat);
        mNickColor = typedArray.getColor(R.styleable.Tapchat_nickColor, Color.BLACK);
    }

    public CharSequence renderEvent(BufferEvent event) {
        return addTimestamp(renderEventReal(event), event.getFirstItem().getMessage().getDate());
    }

    public CharSequence renderEventItem(BufferEventItem event) {
        return addTimestamp(renderEventItemReal(event), event.getMessage().getDate());
    }

    private CharSequence renderEventReal(BufferEvent event) {
        if (event.getItems().size() > 1) {
            Map<String, String> presenceChanges = new HashMap<>();
            List<Pair<String, String>> nickChanges = new ArrayList<>();

            for (BufferEventItem item : event.getItems()) {
                BufferEventMessage message = item.getMessage();
                String type = message.type;
                if (PRESENCE_TYPES.contains(type)) {
                    presenceChanges.put(message.nick, type);

                } else if (type.equals("nickchange")) {
                    NickchangeMessage nickchangeMessage = (NickchangeMessage) message;
                    String oldnick = nickchangeMessage.oldnick;
                    String newnick = nickchangeMessage.newnick;

                    // Update any "joined" events
                    if (presenceChanges.containsKey(oldnick) && presenceChanges.get(oldnick).equals("joined_channel")) {
                        String presence = presenceChanges.remove(oldnick);
                        presenceChanges.put(newnick, presence);
                    }

                    nickChanges.add(new Pair<>(oldnick, newnick));
                }
            }

            List<String> strings = newArrayList();

            List<String> seenNicks = newArrayList();

            appendPresenceEvents(strings, presenceChanges, "joined_channel", R.string.joined_format, nickChanges, seenNicks);
            appendPresenceEvents(strings, presenceChanges, "parted_channel", R.string.parted_format, nickChanges, seenNicks);
            appendPresenceEvents(strings, presenceChanges, "quit",           R.string.quit_format,   nickChanges, seenNicks);

            for (Pair<String, String> nickChange : nickChanges) {
                if (!seenNicks.contains(nickChange.first) && !seenNicks.contains(nickChange.second)) {
                    List<String> chain = getNickChain(nickChanges, nickChange.first);
                    seenNicks.addAll(chain);
                    String firstNick = chain.get(0);
                    String lastNick  = chain.get(chain.size() - 1);
                    strings.add(String.format("%s → %s", firstNick, lastNick));
                }
            }

            return Joiner.on(" • ").join(strings);
        } else {
            return renderEventItem(event.getFirstItem());
        }
    }

    private CharSequence renderEventItemReal(BufferEventItem event) {
        BufferEventMessage message = event.getMessage();
        String type = message.type;

        switch (type) {
            case SocketClosedMessage.TYPE:
                return mContext.getString(R.string.event_socket_closed);

            case ConnectingMessage.TYPE:
                // addStatusLine(String.format("Connecting to %s", message.getString("hostname")));
                return mContext.getString(R.string.event_connecting);

            case ConnectedMessage.TYPE:
                ConnectedMessage connectedMessage = (ConnectedMessage) message;
                return mContext.getString(R.string.event_connected, connectedMessage.hostname);

            case QuitServerMessage.TYPE:
                return mContext.getString(R.string.event_quit_server);

            case NoticeMessage.TYPE:
                if (!TextUtils.isEmpty(message.from)) {
                    SpannableString span = new SpannableString(String.format("%s %s", message.from, event.getMessage().getMsgString()));
                    span.setSpan(new StyleSpan(Typeface.BOLD), 0, message.from.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    span.setSpan(new TypefaceSpan("monospace"), 0, span.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    return span;
                } else {
                    SpannableString span = new SpannableString(event.getMessage().getMsgString());
                    span.setSpan(new TypefaceSpan("monospace"), 0, span.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    return span;
                }

            case YouNickchangeMessage.TYPE:
                YouNickchangeMessage youNickchangeMessage = (YouNickchangeMessage) message;
                return mContext.getString(R.string.event_you_nickchange, youNickchangeMessage.newnick);

            case BannedMessage.TYPE:
                return mContext.getString(R.string.event_banned);

            case ConnectingRetryMessage.TYPE: {
                ConnectingRetryMessage retryMessage = (ConnectingRetryMessage) message;
                return mContext.getString(R.string.event_connecting_retry, retryMessage.interval);

            }
            case WaitingToRetryMessage.TYPE: {
                WaitingToRetryMessage retryMessage = (WaitingToRetryMessage) message;
                return mContext.getString(R.string.event_waiting_to_retry, retryMessage.interval);

            }
            case ConnectingFailedMessage.TYPE:
                return mContext.getString(R.string.event_connecting_failed);

            // FIXME:
            // } else if (type.equals("joining")) {
            //    JSONArray channels = event.getJSONArray("channels");
            //    return mContext.getString(R.string.event_joining, channels);

            case UserModeMessage.TYPE:
                UserModeMessage modeMessage = (UserModeMessage) message;
                return mContext.getString(R.string.event_user_mode, modeMessage.newmode);

            case BufferMsgMessage.TYPE: {
                SpannableString span = new SpannableString(String.format("%s %s", message.from, event.getMessage().msg));
                formatName(span, 0, message.from.length());
                return span;
            }

            case BufferMeMsgMessage.TYPE: {
                SpannableString span = new SpannableString(String.format("• %s %s", message.from, event.getMessage().msg));
                formatName(span, 0, message.from.length() + 2);
                return span;
            }

            case JoinedChannelMessage.TYPE:
                JoinedChannelMessage joinedChannelMessage = (JoinedChannelMessage) message;
                return mContext.getString(R.string.event_joined_channel, joinedChannelMessage.nick);

            case PartedChannelMessage.TYPE:
                PartedChannelMessage partedChannelMessage = (PartedChannelMessage) message;
                return mContext.getString(R.string.event_parted_channel, partedChannelMessage.nick);

            case QuitMessage.TYPE:
                QuitMessage quitMessage = (QuitMessage) message;
                return mContext.getString(R.string.event_quit, quitMessage.nick);

            case AwayMessage.TYPE:
                AwayMessage awayMessage = (AwayMessage) message;
                return mContext.getString(R.string.event_away, awayMessage.nick);

            case KickedChannelMessage.TYPE:
                KickedChannelMessage kickedChannelMessage = (KickedChannelMessage) message;
                return mContext.getString(R.string.event_kicked_channel,
                    kickedChannelMessage.nick,
                    kickedChannelMessage.chan,
                    kickedChannelMessage.kicker,
                    kickedChannelMessage.hostmask,
                    kickedChannelMessage.msg);

            case YouJoinedChannelMessage.TYPE:
                return mContext.getString(R.string.event_you_joined_channel);

            case YouPartedChannelMessage.TYPE:
                return mContext.getString(R.string.event_you_parted_channel);

            case ChannelModeIsMessage.TYPE:
                ChannelModeIsMessage channelModeIsMessage = (ChannelModeIsMessage) message;
                return mContext.getString(R.string.event_channel_mode_is, channelModeIsMessage.newmode);

            case ChannelTimestampMessage.TYPE:
                ChannelTimestampMessage channelTimestampMessage = (ChannelTimestampMessage) message;
                return mContext.getString(R.string.event_channel_timestamp, channelTimestampMessage.timestamp);

            case NickchangeMessage.TYPE:
                NickchangeMessage nickchangeMessage = (NickchangeMessage) message;
                return mContext.getString(R.string.event_nickchange, nickchangeMessage.oldnick, nickchangeMessage.newnick);

            case UserChannelModeMessage.TYPE:
                UserChannelModeMessage userChannelModeMessage = (UserChannelModeMessage) message;
                return mContext.getString(R.string.event_user_channel_mode,
                    userChannelModeMessage.diff,
                    userChannelModeMessage.nick,
                    userChannelModeMessage.from);

            case ChannelUrlMessage.TYPE:
                // FIXME: This should show up next to the topic instead of in the buffer
                ChannelUrlMessage channelUrlMessage = (ChannelUrlMessage) message;
                return mContext.getString(R.string.event_channel_url, channelUrlMessage.url);

            case ChannelTopicMessage.TYPE:
                ChannelTopicMessage channelTopicMessage = (ChannelTopicMessage) message;
                String topic = channelTopicMessage.topic;
                String nick = channelTopicMessage.author.split("!")[0];
                if (!TextUtils.isEmpty(topic)) {
                    return mContext.getString(R.string.event_channel_topic, nick, topic);
                } else {
                    return mContext.getString(R.string.event_channel_topic_cleared, nick);
                }

            case ChannelModeMessage.TYPE:
                ChannelModeMessage channelModeMessage = (ChannelModeMessage) message;
                String mode = channelModeMessage.diff;
                if (TextUtils.isEmpty(mode)) {
                    mode = channelModeMessage.newmode;
                }
                return mContext.getString(R.string.event_channel_mode, mode, message.from);

            default:
                if (!TextUtils.isEmpty(message.getMsgString())) {
                    return message.getMsgString();
                }
                return "Unknown message type '" + type + "': " + event.toString();
        }
    }

    private CharSequence addTimestamp(CharSequence text, Date date) {
        if (mIncludeTimestamp) {
            return new SpannableStringBuilder()
                .append(mDateFormat.format(date))
                .append(" ")
                .append(text);
        }
        return text;
    }

    private void appendPresenceEvents(List<String> strings, final Map<String, String> presenceChanges,
            final String type, int resId, final List<Pair<String, String>> nickChanges, final List<String> seenNicks) {

        List<String> nicks = newArrayList(filter(presenceChanges.keySet(), new Predicate<String>() {
            @Override public boolean apply(String nick) {
                return presenceChanges.get(nick).equals(type);
            }
        }));

        if (nicks.isEmpty()) {
            return;
        }

        nicks = newArrayList(transform(nicks, new Function<String, String>() {
            @Override public String apply(String nick) {
                List<String> nickChain = getNickChainReverse(nickChanges, nick);
                if (nickChain.size() > 1) {
                    // Last item is current nick
                    nickChain.remove(nickChain.size() - 1);

                    // Don't need to show nick change if included in presence event
                    seenNicks.addAll(nickChain);

                    return mContext.getString(R.string.event_was_format, nick, Joiner.on(", ").join(nickChain));
                } else {
                    return nick;
                }
            }
        }));

        strings.add(mContext.getString(resId, Joiner.on(", ").join(nicks)));
    }

    private List<String> getNickChain(List<Pair<String, String>> nickChanges, String startNick) {
        List<String> chain = newArrayList();
        chain.add(startNick);
        for (Pair<String, String> nickChange : nickChanges) {
            String lastNick = chain.get(chain.size() - 1);
            if (nickChange.first.equals(lastNick)) {
                chain.add(nickChange.second);
            }
        }
        return chain;
    }

    private List<String> getNickChainReverse(List<Pair<String, String>> nickChanges, String endNick) {
        List<String> chain = newArrayList();
        chain.add(endNick);
        for (Pair<String, String> nickChange : Lists.reverse(nickChanges)) {
            String lastNick = chain.get(chain.size() - 1);
            if (nickChange.second.equals(lastNick)) {
                chain.add(nickChange.first);
            }
        }
        return Lists.reverse(chain);
    }

    private void formatName(SpannableString span, int start, int end) {
        span.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        span.setSpan(new ForegroundColorSpan(mNickColor), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }
}
