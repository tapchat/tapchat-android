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

public class Member {
    private String mNick;

    public Member(String nick) {
        mNick = nick;
    }

    public String getNick() {
        return mNick;
    }

    public boolean isOp() {
        return false; // FIXME
    }

    public boolean isVoiced() {
        return false; // FIXME
    }

    void setNick(String newNick) {
        mNick = newNick;
    }
}
