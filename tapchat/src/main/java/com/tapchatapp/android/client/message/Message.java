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

package com.tapchatapp.android.client.message;

import com.google.common.collect.ImmutableList;

import java.util.List;

public abstract class Message {
    public static final List<String> SELF_TYPES = ImmutableList.of(
        "self_away",
        "self_back",
        "you_nickchange",
        "you_joined_channel",
        "you_parted_channel",
        "you_kicked_channel",
        "quit_server"
    );

    public static final List<String> IMPORTANT_TYPES = ImmutableList.of(
        "buffer_msg",
        "buffer_me_msg",
        "notice",
        "channel_invite",
        "callerid"
    );

    public String _method;
    public String session;
    public long eid;
    public boolean highlight;
    public boolean self;
    public Integer _reqid;
    public Long cid;
    public Long bid;
    public String error;
    public String type;
    public boolean is_backlog;

    private String mJson;

    protected Message() { }

    protected Message(String method) {
        this._method = method;
    }

    public boolean isSelf() {
        return self || SELF_TYPES.contains(type);
    }

    public boolean isImportant() {
        return IMPORTANT_TYPES.contains(type);
    }

    public boolean isHighlight() {
        return isImportant() && highlight;
    }

    public void setOriginalJson(String originalJson) {
        mJson = originalJson;
    }

    public String getOriginalJson() {
        return mJson;
    }
}
