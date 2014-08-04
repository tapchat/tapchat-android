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

package com.tapchatapp.android.app.fragment;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.Lists;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.tapchatapp.android.R;
import com.tapchatapp.android.app.TapchatApp;
import com.tapchatapp.android.app.event.BufferChangedEvent;
import com.tapchatapp.android.app.event.BufferLineAddedEvent;
import com.tapchatapp.android.app.event.BufferRemovedEvent;
import com.tapchatapp.android.app.event.ConnectionChangedEvent;
import com.tapchatapp.android.app.event.ServiceStateChangedEvent;
import com.tapchatapp.android.client.model.Buffer;
import com.tapchatapp.android.client.model.BufferEvent;
import com.tapchatapp.android.client.model.BufferEventItem;
import com.tapchatapp.android.client.model.ChannelBuffer;
import com.tapchatapp.android.client.model.Connection;
import com.tapchatapp.android.client.model.ConsoleBuffer;
import com.tapchatapp.android.client.TapchatService;
import com.tapchatapp.android.client.message.BufferEventMessage;
import com.tapchatapp.android.app.ui.BufferEventRenderer;
import com.tapchatapp.android.app.ui.ConnectionStatusBar;
import com.tapchatapp.android.app.ui.FilterableListAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.LayoutParams;

public class BufferFragment extends ListFragment {

    public static final String ARG_CONNECTION_ID = "com.tapchatapp.android.arg_connection_id";
    public static final String ARG_BUFFER_ID = "com.tapchatapp.android.arg_buffer_id";

    private static final String TAG = "BufferFragment";

    private static final Pattern URL_PATTERN = Pattern.compile("\\(?\\bhttps?://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]");

    protected long mBufferId;

    protected Connection mConnection;

    protected Buffer mBuffer;

    private int mConnectionState;
    private long mConnectionId;

    private BufferEventRenderer mRenderer;
    private BufferEventRenderer mMenuRenderer;
    private ConnectionStatusBar mStatusBar;

    @Inject Bus mBus;

    public static BufferFragment create(int type, long connectionId, long bufferId) {
        BufferFragment fragment;

        switch (type) {
            case Buffer.CHANNEL_TYPE:
                fragment = new ChannelBufferFragment();
                break;

            case Buffer.CONVERSATION_TYPE:
                fragment = new QueryBufferFragment();
                break;

            default:
                fragment = new BufferFragment();
                break;
        }

        Bundle args = new Bundle();
        args.putLong(BufferFragment.ARG_CONNECTION_ID, connectionId);
        args.putLong(BufferFragment.ARG_BUFFER_ID, bufferId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TapchatApp.get().inject(this);

        setHasOptionsMenu(true);

        mConnectionId = getArguments().getLong(ARG_CONNECTION_ID);
        mBufferId = getArguments().getLong(ARG_BUFFER_ID);

        mStatusBar = new ConnectionStatusBar(this, mConnectionId);
    }

    @Override public void onResume() {
        super.onResume();
        mBus.register(this);
        mStatusBar.registerBus(mBus);
    }

    @Override public void onPause() {
        super.onPause();
        mBus.unregister(this);
        mStatusBar.unregisterBus(mBus);

        if (mBuffer != null) {
            mBuffer = null;
        }
        mConnection = null;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        mStatusBar.destroy();
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setSelector(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mRenderer = new BufferEventRenderer(getActivity());
        mMenuRenderer = new BufferEventRenderer(getActivity(), true);
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.buffer, menu);
    }

    @Override public void onPrepareOptionsMenu(Menu menu) {
        boolean hasBuffer = mBuffer != null && (!(mBuffer instanceof ConsoleBuffer));
        boolean isArchived = (hasBuffer && mBuffer.isArchived());
        menu.findItem(R.id.archive).setVisible(hasBuffer && !isArchived);
        menu.findItem(R.id.unarchive).setVisible(hasBuffer && isArchived);
        menu.findItem(R.id.delete).setVisible(hasBuffer);
        //menu.findItem(R.id.star).setVisible(hasBuffer);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.archive) {
            mBuffer.archive();
            TapchatApp.goHome(getActivity(), mConnection.getId());
            return true;

        } else if (item.getItemId() == R.id.unarchive) {
            mBuffer.unarchive();
            return true;

        } else if (item.getItemId() == R.id.delete) {
            new AlertDialog.Builder(getActivity())
                .setMessage(R.string.confirm_delete_buffer)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mBuffer.delete();
                        TapchatApp.goHome(getActivity(), mConnection.getId());
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
            return true;
        }
        return false;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
       LinearLayout layout = new LinearLayout(getActivity());
       layout.setOrientation(LinearLayout.VERTICAL);
       layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        View connectionHeaderView = inflater.inflate(R.layout.connection_header, null);
        layout.addView(connectionHeaderView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        View bufferHeaderView = inflater.inflate(R.layout.fragment_buffer_header, null);
        layout.addView(bufferHeaderView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        ListView listView = new ListView(getActivity());
        listView.setId(android.R.id.list);
        listView.setDivider(null);
        listView.setStackFromBottom(true);
        listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        listView.setSmoothScrollbarEnabled(false);
//        listView.setCacheColorHint(Color.TRANSPARENT);

        LayoutParams params = new LayoutParams(MATCH_PARENT, 0);
        params.weight = 1;
        layout.addView(listView, params);
        View footerView = inflater.inflate(R.layout.fragment_buffer_footer, null);
        layout.addView(footerView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        ((TextView) footerView.findViewById(R.id.text_entry)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }

                sendMessage();
                return true;
            }
        });

        footerView.findViewById(R.id.send_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        return layout;
    }

    @Override public void onListItemClick(ListView l, View v, int position, long id) {
        final BufferEvent event = ((BufferEventListAdapter) getListAdapter()).getItem(position);

        List<ContextMenuItem> menuItems = Lists.newArrayList();

        if (event.getItems().size() == 1) {
            populateMenuItems(menuItems, event.getFirstItem());
        } else {
            populateMenuItems(menuItems, event.getItems());
        }

        showContextMenu(menuItems);
    }

    @Subscribe public void onServiceStateChanged(ServiceStateChangedEvent event) {
        TapchatService service = event.getService();

        if (service.getConnectionState() == TapchatService.STATE_LOADED) {
            if (mConnection == null && mBuffer == null) {
                mConnection = service.getConnection(mConnectionId);
                if (mConnection == null) {
                    throw new IllegalStateException("Connection not found. " + mConnectionId + " connections: " + service.getConnections());
                }

                mBuffer = mConnection.getBuffer(mBufferId);
                if (mBuffer == null) {
                    throw new IllegalStateException("Buffer not found. " + mBufferId + " buffers: " + mConnection.getBuffers());
                }
            }
        } else {
            mConnection = null;
            mBuffer     = null;
        }

        mConnectionState = service.getConnectionState();
        updateUI();
    }

    @Subscribe public void onConnectionChanged(ConnectionChangedEvent event) {
        if (event.getConnection().getId() == mConnectionId) {
          updateUI();
        }
    }

    @Subscribe public void onBufferChanged(BufferChangedEvent event) {
        if (event.getBuffer().getId() != mBufferId) {
            return;
        }
        updateUI();
    }

    @Subscribe public void onBufferLineAdded(final BufferLineAddedEvent event) {
        if (event.getBuffer().getId() != mBufferId || mConnectionState != TapchatService.STATE_LOADED) {
            return;
        }

        BufferEventListAdapter adapter = (BufferEventListAdapter) getListAdapter();
        if (adapter != null && !adapter.contains(event.getBufferEvent())) {
            adapter.addItem(event.getBufferEvent());
        }
    }

    @Subscribe public void onBufferRemoved(BufferRemovedEvent event) {
        if (event.getBuffer().getId() == mBufferId) {
            TapchatApp.goHome(getActivity());
        }
    }

    public long getBufferId() {
        return mBufferId;
    }

    private void sendMessage() {
        EditText textEntry = (EditText) getView().findViewById(R.id.text_entry);
        final String text = textEntry.getText().toString();

        if (TextUtils.isEmpty(text)) {
            return;
        }

        if (text.startsWith("/")) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.commands_not_supported)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        textEntry.setText("");

        mConnection.say(mBuffer.getName(), text, null);
    }

    private void showContextMenu(List<ContextMenuItem> menuItems) {
        new AlertDialog.Builder(getActivity())
            .setAdapter(new ContextMenuItemsAdapter(menuItems), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ContextMenuItemsAdapter adapter = (ContextMenuItemsAdapter) ((AlertDialog) dialog).getListView().getAdapter();
                    ContextMenuItem menuItem = adapter.getItem(which);
                    menuItem.onClick();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void populateMenuItems(List<ContextMenuItem> menuItems, List<BufferEventItem> eventItems) {
        for (BufferEventItem item : eventItems) {
            menuItems.add(new BufferEventItemContextMenuItem(item));
        }
    }

    private void populateMenuItems(List<ContextMenuItem> menuItems, BufferEventItem item) {
        menuItems.add(new CopyContextMenuItem(item));

        BufferEventMessage message = item.getMessage();

        final String nick = TextUtils.isEmpty(message.from) ? message.nick : message.from;
        if (!TextUtils.isEmpty(nick)) {
            if (mBuffer instanceof ChannelBuffer) {
                if (((ChannelBuffer) mBuffer).isInChannel(nick)) {
                    menuItems.add(new MessageUserContextMenuItem(nick));
                    menuItems.add(new MentionUserContextMenuItem(nick));
                }
            } else {
                menuItems.add(new MessageUserContextMenuItem(nick));
            }
        }

        if (!TextUtils.isEmpty(message.getMsgString())) {
            Matcher matcher = URL_PATTERN.matcher(message.getMsgString());
            while (matcher.find()) {
                menuItems.add(new URLContextMenuItem(matcher.group(0)));
            }
        }
    }

    protected void updateUI() {
        if (getView() == null) {
            Log.w(TAG, "View is gone??!");
            return;
        }

        if (mConnectionState == TapchatService.STATE_LOADED && mBuffer != null) {
            if (getListAdapter() == null) {
                setListAdapter(new BufferEventListAdapter(mBuffer.getBacklog()));
            } else {
                ((BufferEventListAdapter) getListAdapter()).updateItems(mBuffer.getBacklog());
            }

            getView().findViewById(R.id.text_entry).setEnabled(mBuffer.isActive());
            getView().findViewById(R.id.send_button).setEnabled(mBuffer.isActive());

        } else {
            getView().findViewById(R.id.text_entry).setEnabled(false);
            getView().findViewById(R.id.send_button).setEnabled(false);
        }
    }

    private class BufferEventListAdapter extends FilterableListAdapter<BufferEvent> {
        private int mHighlightBgColor;
        private SimpleDateFormat mDateFormat = new SimpleDateFormat("MMMM d, yyyy");

        private BufferEventListAdapter(BufferEvent[] items) {
            super(Buffer.MAX_EVENTS);

            TypedArray typedArray = getActivity().obtainStyledAttributes(R.styleable.Tapchat);
            mHighlightBgColor = typedArray.getColor(R.styleable.Tapchat_highlightBgColor, Color.TRANSPARENT);

            updateItems(items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup group) {
            if (convertView == null) {
                convertView = View.inflate(getActivity(), R.layout.buffer_line, null);
            }

            BufferEvent event = getItem(position);
            BufferEvent prevEvent = (position > 0) ? getItem(position - 1) : null;

            BufferEventItem firstItem = event.getFirstItem();
            BufferEventMessage firstItemMessage = firstItem.getMessage();

            boolean isNewDay = ((prevEvent != null) && (!firstItem.isSameDay(prevEvent.getFirstItem())));
            TextView dayView = (TextView) convertView.findViewById(R.id.day);
            dayView.setVisibility(isNewDay ? View.VISIBLE : View.GONE);
            if (isNewDay) {
                dayView.setText(mDateFormat.format(firstItemMessage.getDate()));
            }

            TextView timestampView = (TextView) convertView.findViewById(R.id.timestamp);
            TextView textView      = (TextView) convertView.findViewById(R.id.text);

            Date date = firstItemMessage.getDate();
            if (date != null) {
                timestampView.setText(new SimpleDateFormat("h:mm aa").format(date));
                timestampView.setVisibility(View.VISIBLE);
            } else {
                timestampView.setVisibility(View.GONE);
            }
            CharSequence text = mRenderer.renderEvent(event);
            if ((!(mBuffer instanceof ConsoleBuffer)) && (!(text instanceof SpannableString))) {
                SpannableString span = new SpannableString(text);
                span.setSpan(new StyleSpan(Typeface.ITALIC), 0, span.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                span.setSpan(new ForegroundColorSpan(Color.GRAY), 0, span.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                textView.setText(span);
            } else {
                textView.setText(text);
            }

            boolean highlight = firstItem.isHighlight() && (mBuffer instanceof ChannelBuffer);
            convertView.findViewById(R.id.inner).setBackgroundColor(highlight ? mHighlightBgColor : Color.TRANSPARENT);

            return convertView;
        }
    }

    private class BufferEventItemContextMenuItem extends ContextMenuItem<BufferEventItem> {
        protected BufferEventItemContextMenuItem(BufferEventItem eventItem) {
            super(eventItem);
        }

        @Override
        public CharSequence getText() {
            return mMenuRenderer.renderEventItem(getObject());
        }

        @Override
        public void onClick() {
            List<ContextMenuItem> menuItems = Lists.newArrayList();
            populateMenuItems(menuItems, getObject());
            showContextMenu(menuItems);
        }
    }

    private class MessageUserContextMenuItem extends ContextMenuItem<String> {
        public MessageUserContextMenuItem(String nick) {
            super(nick);
        }

        @Override
        public CharSequence getText() {
            return getString(R.string.message_user_format, getObject());
        }

        @Override
        public void onClick() {
            mConnection.openBuffer(getObject());
        }
    }

    private class MentionUserContextMenuItem extends ContextMenuItem<String> {
        public MentionUserContextMenuItem(String nick) {
            super(nick);
        }

        @Override
        public CharSequence getText() {
            return (getString(R.string.mention_user_format, getObject()));
        }

        @Override
        public void onClick() {
            final EditText editText = (EditText) getView().findViewById(R.id.text_entry);
            editText.requestFocus();
            editText.setText(String.format("%s: %s", getObject(), editText.getText()));
            editText.setSelection(editText.getText().length());
        }
    }

    private class URLContextMenuItem extends ContextMenuItem<String> {
        protected URLContextMenuItem(String object) {
            super(object);
        }

        @Override
        public CharSequence getText() {
            return getObject();
        }

        @Override
        public void onClick() {
            String url = getObject();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    private class CopyContextMenuItem extends ContextMenuItem<BufferEventItem> {
        public CopyContextMenuItem(BufferEventItem eventItem) {
            super(eventItem);
        }

        public CharSequence getText() {
            return getString(R.string.copy_text);
        }

        public void onClick() {
            @SuppressWarnings("deprecation")
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(mRenderer.renderEventItem(getObject()).toString());
            Toast.makeText(getActivity(), R.string.text_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private abstract class ContextMenuItem<T> {
        private T mObject;

        protected ContextMenuItem(T object) {
            mObject = object;
        }

        public T getObject() {
            return mObject;
        }

        public abstract CharSequence getText();
        public abstract void onClick();
    }

    private class ContextMenuItemsAdapter extends FilterableListAdapter<ContextMenuItem> {
        public ContextMenuItemsAdapter(List<ContextMenuItem> items) {
            updateItems(items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(getActivity(), android.R.layout.simple_list_item_1, null);
            }
            ((TextView) convertView).setText(getItem(position).getText());
            return convertView;
        }
    }
}
