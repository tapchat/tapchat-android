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

package com.tapchatapp.android.app.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;

import com.tapchatapp.android.R;
import com.tapchatapp.android.client.TapchatService;

public class InvalidConnectionCertActivity extends TapchatServiceActivity {

    public static final String EXTRA_CID = "cid";
    public static final String EXTRA_HOSTNAME = "hostname";
    public static final String EXTRA_FINGERPRINT = "fingerprint";
    public static final String EXTRA_ERROR = "error";

    private long mCid;
    private String mHostname;
    private String mFingerprint;
    private String mError;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mCid = intent.getLongExtra(EXTRA_CID, -1);
        mHostname = intent.getStringExtra(EXTRA_HOSTNAME);
        mFingerprint = intent.getStringExtra(EXTRA_FINGERPRINT);
        mError = intent.getStringExtra(EXTRA_ERROR);
    }

    @Override public void onServiceConnected(final TapchatService service) {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean accept = (which == DialogInterface.BUTTON_POSITIVE);
                service.getConnection(mCid).acceptCert(mFingerprint, accept);
                finish();
            }
        };

        new AlertDialog.Builder(this)
            .setTitle(R.string.cert_dialog_title)
            .setMessage(Html.fromHtml(getString(R.string.connection_cert_dialog_message, mHostname, mError, mFingerprint)))
            .setPositiveButton(R.string.accept, listener)
            .setNegativeButton(R.string.reject, listener)
            .show();
    }
}