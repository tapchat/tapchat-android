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

package com.tapchatapp.android.util;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.google.common.base.Function;
import com.google.common.primitives.Ints;
import com.tapchatapp.android.R;

import static com.google.common.collect.Iterables.toArray;
import static com.google.common.collect.Iterables.transform;

public final class FieldValidator {

    private FieldValidator() { }

    public static boolean validateFields(final Activity activity, int... viewIds) {
        Iterable<View> views = transform(Ints.asList(viewIds), new Function<Integer, View>() {
            @Override public View apply(Integer viewId) {
                View view = activity.findViewById(viewId);
                if (view == null) {
                    throw new IllegalArgumentException("no view with id: " + viewId);
                }
                return view;
            }
        });

        return validateFields(toArray(views, View.class));
    }

    public static boolean validateFields(final View parentView, int... viewIds) {
        Iterable<View> views = transform(Ints.asList(viewIds), new Function<Integer, View>() {
            @Override public View apply(Integer viewId) {
                View view = parentView.findViewById(viewId);
                if (view == null) {
                    throw new IllegalArgumentException("no view with id: " + viewId);
                }
                return view;
            }
        });

        return validateFields(toArray(views, View.class));
    }

    public static boolean validateFields(View... views) {
        View firstInvalidView = null;

        for (View view : views) {
            if (!view.isShown()) {
                continue;
            }

            boolean viewIsVaild = true;

            if (view instanceof EditText) {
                viewIsVaild = validateEditText((EditText) view);
            }

            if (!viewIsVaild && firstInvalidView == null) {
                firstInvalidView = view;
            }
        }

        if (firstInvalidView != null) {
            firstInvalidView.requestFocus();
            return false;
        }

        return true;
    }

    private static boolean validateEditText(EditText editText) {
        boolean valid = true;

        String text = editText.getText().toString();

        boolean isEmail   = (editText.getInputType() & InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        boolean isNumeric = (editText.getInputType() & InputType.TYPE_NUMBER_FLAG_DECIMAL) == InputType.TYPE_NUMBER_FLAG_DECIMAL;

        if (TextUtils.isEmpty(text)) {
            if (!isNumeric || !TextUtils.isDigitsOnly(editText.getHint())) {
                valid = false;
            }

        } else if (isEmail) {
            valid = android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches();
        }

        if (!valid) {
            Context context = editText.getContext();
            if (isEmail) {
                editText.setError(context.getString(R.string.error_invalid_email));
            } else {
                editText.setError(context.getString(R.string.error_blank));
            }
            return false;
        }

        editText.setError(null);
        return true;
    }
}
