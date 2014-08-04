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

import java.net.HttpCookie;

import javax.inject.Inject;

import retrofit.RequestInterceptor;

public class TapchatRequestInterceptor implements RequestInterceptor {

    @Inject TapchatSession mSession;

    @Inject public TapchatRequestInterceptor() { }

    @Override public void intercept(RequestFacade requestFacade) {
        Uri uri = mSession.getUri();
        String sessionId = mSession.getSessionId();
        if (uri != null && sessionId != null) {
            HttpCookie cookie = new HttpCookie("session", sessionId);
            cookie.setDomain("." + uri.getHost());
            cookie.setPath("/");
            requestFacade.addHeader("Cookie", cookie.toString());
        }
    }
}
