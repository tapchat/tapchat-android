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

import android.net.Uri;

import retrofit.Endpoint;

public class TapchatSession implements Endpoint {

    private Uri mUri;
    private String mSessionId;

    public Uri getUri() {
        return mUri;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

    public String getSessionId() {
        return mSessionId;
    }

    public void setSessionId(String sessionId) {
        mSessionId = sessionId;
    }

    @Override public String getName() {
        return null;
    }

    @Override public String getUrl() {
        return mUri.toString();
    }
}
