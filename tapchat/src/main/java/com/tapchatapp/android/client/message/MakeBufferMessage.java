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

public class MakeBufferMessage extends Message {

    public static final String TYPE = "makebuffer";

    public String buffer_type;
    public String name;
    public long last_seen_eid;
    public boolean archived;
    public boolean hidden;
    public boolean joined; // FIXME: channel specific?
}
