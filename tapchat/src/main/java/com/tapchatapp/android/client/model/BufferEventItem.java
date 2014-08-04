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

import com.tapchatapp.android.client.message.BufferEventMessage;

import java.util.Date;

public class BufferEventItem<T extends BufferEventMessage> {
    private T mMessage;

    BufferEventItem(T message) {
        mMessage = message;
    }

    public T getMessage() {
        return mMessage;
    }

    public long getEid() {
        return mMessage.eid;
    }

    public boolean isHighlight() {
        return mMessage.highlight;
    }

    @Override
    public String toString() {
        return "BufferEventItem{" + mMessage.toString() + "}";
    }

    public boolean isSameDay(BufferEventItem otherItem) {
        Date date = mMessage.getDate();
        Date otherDate = otherItem.getMessage().getDate();
        return (date == null || otherDate == null) ||
            (date.getYear()  == otherDate.getYear() &&
             date.getMonth() == otherDate.getMonth() &&
             date.getDate()  == otherDate.getDate());
    }
}
