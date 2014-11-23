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

import retrofit.Callback;
import retrofit.http.POST;

public interface TapchatPushAPI {
    @POST("/register") void register(@retrofit.http.Body Body body, Callback<Result> callback);
    @POST("/unregister") void unregister(@retrofit.http.Body Body body, Callback<Result> callback);

    public final class Body {
        String pushId;
        String regId;

        public Body(String pushId, String regId) {
            this.pushId = pushId;
            this.regId = regId;
        }
    }

    public final class Result { }
}
