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

package com.tapchatapp.android.app.ui;


import android.widget.BaseAdapter;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class FilterableListAdapter<T> extends BaseAdapter {

    private final Object mLock = new Object();

    private final int mCapacity;

    private Collection<T> mOriginalItems;
    private List<T> mItems = Lists.newArrayList();

    protected FilterableListAdapter() {
        mCapacity = 0;
    }

    public FilterableListAdapter(int capacity) {
        mCapacity = capacity;
    }

    @Override public int getCount() {
        return mItems.size();
    }

    @Override public T getItem(int position) {
        return mItems.get(position);
    }

    @Override public long getItemId(int position) {
        return position;
    }

    @Override public void notifyDataSetChanged() {
        synchronized (mLock) {
            List<T> items = new ArrayList<>();
            if (mOriginalItems != null) {
                for (T item : mOriginalItems) {
                    if (isVisible(item)) {
                        items.add(item);
                    }
                }
            }
            Comparator<? super T> comparator = getComparator();
            if (comparator != null) {
                Collections.sort(items, comparator);
            }
            mItems = items;
        }
        super.notifyDataSetChanged();
    }

    public boolean isVisible(T item) {
        return true;
    }

    public boolean contains(T item) {
        synchronized (mLock) {
            return mOriginalItems.contains(item);
        }
    }

    public void addItem(T item) {
        synchronized (mLock) {
            mOriginalItems.add(item);
        }
        notifyDataSetChanged();
    }

    public void removeItem(T item) {
        synchronized (mLock) {
            mOriginalItems.remove(item);
        }
        notifyDataSetChanged();
    }

    public void clearItems() {
        updateItems((List<T>) null);
    }

    public void updateItems(T[] items) {
        updateItems(Arrays.asList(items));
    }

    public void updateItems(List<T> items) {
        synchronized (mLock) {
            Collection<T> newItems = (mCapacity > 0) ? EvictingQueue.<T>create(mCapacity) : new ArrayList<T>();
            if (items != null) {
                newItems.addAll(items);
            }
            mOriginalItems = newItems;
        }
        notifyDataSetChanged();
    }

    protected Comparator<? super T> getComparator() {
        return null;
    }
}
