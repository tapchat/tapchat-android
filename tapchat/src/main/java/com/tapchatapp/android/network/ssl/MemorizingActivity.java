/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.tapchatapp.android.network.ssl;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;

import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.TapchatActivity;

public class MemorizingActivity extends TapchatActivity implements OnClickListener,OnCancelListener {

    private int mDecisionId;

    private String mApp;
    private String mFingerprint;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mApp = intent.getStringExtra(MemorizingTrustManager.DECISION_INTENT_APP);
        mDecisionId = intent.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID);
        mFingerprint = intent.getStringExtra(MemorizingTrustManager.DECISION_INTENT_FINGERPRINT).toUpperCase()
                .replace(":", " ");
	}

	@Override public void onResume() {
		super.onResume();

        new AlertDialog.Builder(this)
            .setTitle(R.string.cert_dialog_title)
            .setMessage(Html.fromHtml(getString(R.string.cert_dialog_message, mFingerprint)))
            .setPositiveButton(R.string._continue, this)
            .setNegativeButton(android.R.string.cancel, this)
            .show();
	}

	@Override public void onClick(DialogInterface dialog, int btnId) {
        dialog.dismiss();

		switch (btnId) {
		    case DialogInterface.BUTTON_POSITIVE:
                sendDecision(MTMDecision.DECISION_ALWAYS);
			    break;
		    case DialogInterface.BUTTON_NEUTRAL:
                sendDecision(MTMDecision.DECISION_ONCE);
                break;
		    default:
			    sendDecision(MTMDecision.DECISION_ABORT);
		}
	}

	@Override public void onCancel(DialogInterface dialog) {
		sendDecision(MTMDecision.DECISION_ABORT);
	}

    private void sendDecision(int decision) {
   		Intent i = new Intent(MemorizingTrustManager.DECISION_INTENT + "/" + mApp);
   		i.putExtra(MemorizingTrustManager.DECISION_INTENT_ID,     mDecisionId);
   		i.putExtra(MemorizingTrustManager.DECISION_INTENT_CHOICE, decision);
   		sendBroadcast(i);
   		finish();
   	}
}
