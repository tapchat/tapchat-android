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

import java.util.Date;

public abstract class BufferEventMessage extends Message {
    public Object msg;
    public String nick;
    public String from;
    public Long time;

    public Date getDate() {
        if (time != null) {
            return new Date(time * 1000);
        } else if (eid > 0) {
            return new Date(eid / 1000);
        } else {
            return null;
        }
    }

    public String getMsgString() {
        return (String) msg;
    }
}
