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

package com.tapchatapp.android.network.ssl;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;

import com.squareup.otto.Bus;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.activity.TapchatServiceActivity;

import javax.inject.Inject;

public class VerifyHostnameActivity extends TapchatServiceActivity {

    @Inject Bus mBus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            VerifyHostnameDialogFragment dialog = new VerifyHostnameDialogFragment();
            dialog.setArguments(getIntent().getExtras());
            dialog.show(getFragmentManager(), null);
        }
    }

    private void onDialogResult(int result) {
        int decisionId = getIntent().getIntExtra(MemorizingHostnameVerifier.EXTRA_DECISION_ID, -1);
        boolean allow = result == Dialog.BUTTON_POSITIVE;
        mBus.post(new HostnameVerifyDecisionEvent(decisionId, allow));

        finish();
    }

    public static class VerifyHostnameDialogFragment extends DialogFragment implements Dialog.OnClickListener {
        @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
            String hostname = getArguments().getString(MemorizingHostnameVerifier.EXTRA_HOSTNAME);
            String fingerprint = getArguments().getString(MemorizingHostnameVerifier.EXTRA_FINGERPRINT);

            return new AlertDialog.Builder(getActivity())
                    .setMessage(Html.fromHtml(getString(R.string.verify_hostname_message, hostname, fingerprint)))
                    .setPositiveButton(R.string._continue, this)
                    .setNegativeButton(android.R.string.cancel, this)
                    .create();
        }

        @Override public void onClick(DialogInterface dialog, int which) {
            ((VerifyHostnameActivity) getActivity()).onDialogResult(which);
        }

        @Override public void onCancel(DialogInterface dialog) {
            ((VerifyHostnameActivity) getActivity()).onDialogResult(Dialog.BUTTON_NEGATIVE);
        }
    }
}
