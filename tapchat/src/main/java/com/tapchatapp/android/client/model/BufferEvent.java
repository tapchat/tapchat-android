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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Iterables.transform;

public class BufferEvent {

    private static final List<String> MERGEABLE_TYPES = ImmutableList.of("joined_channel", "parted_channel", "quit",
            "nickchange");

    private final List<BufferEventItem> mItems = Collections.synchronizedList(new ArrayList<BufferEventItem>());

    public BufferEvent(BufferEventItem firstItem) {
        addItem(firstItem);
    }

    public BufferEventItem getFirstItem() {
        synchronized (mItems) {
            return mItems.get(0);
        }
    }

    public BufferEventItem getLastItem() {
        synchronized (mItems) {
            return mItems.get(mItems.size() - 1);
        }
    }

    public boolean shouldMerge(BufferEventItem item) {
        String type = item.getMessage().type;
        String firstType = getFirstItem().getMessage().type;
        synchronized (mItems) {
            return MERGEABLE_TYPES.contains(firstType) &&
                   MERGEABLE_TYPES.contains(type) &&
                   getFirstItem().isSameDay(item);
        }
    }

    public void addItem(BufferEventItem item) {
        synchronized (mItems) {
            mItems.add(item);
        }
    }

    public List<BufferEventItem> getItems() {
        synchronized (mItems) {
            return new ArrayList<>(mItems);
        }
    }

    @Override public String toString() {
        synchronized (mItems) {
            String objects = Joiner.on(",").join(transform(mItems, new Function<BufferEventItem, String>() {
                @Override public String apply(BufferEventItem item) {
                    return item.toString();
                }
            }));
            return String.format("BufferEvent{%s}", objects);
        }
    }
}
