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

package com.tapchatapp.android.service;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.tapchatapp.android.client.TapchatService;

import java.lang.ref.WeakReference;

public class DummyServiceConnection implements ServiceConnection {

    private TapchatService mService;
    private WeakReference<Listener> mListener;

    public TapchatService getService() {
        return mService;
    }

    public void setListener(Listener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override public void onServiceConnected(ComponentName componentName, IBinder binder) {
        mService = ((TapchatService.LocalBinder) binder).getService();

        Listener listener;
        if (mListener != null && (listener = mListener.get()) != null) {
            listener.onServiceConnected(mService);
        }
    }

    @Override public void onServiceDisconnected(ComponentName componentName) {
        Listener listener;
        if (mListener != null && (listener = mListener.get()) != null) {
            listener.onServiceDisconnected(mService);
        }

        mService = null;
    }

    public static interface Listener {
        public void onServiceConnected(TapchatService service);
        public void onServiceDisconnected(TapchatService service);
    }

}
