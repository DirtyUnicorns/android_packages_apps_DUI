/**
 * Copyright (C) 2016 The DirtyUnicorns Project
 * 
 * @author: Randall Rushing <randall.rushing@gmail.com>
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
 *
 * A simple, semi-pojo ContentObserver subclass to eliminate too many observers
 * in too many places. A class implementing SmartObservable simply provides
 * the desired uris as a set to observe and handles onChange(Uri uri) as normal
 *
 */

package com.android.systemui.navigation.utils;

import java.util.HashSet;
import java.util.Set;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.text.TextUtils;

public class SmartObserver extends ContentObserver {
    public interface SmartObservable {
        public Set<Uri> onGetUris();
        public void onChange(Uri uri);
    }

    private ContentResolver mResolver;
    private Set<SmartObservable> mListeners = new HashSet<SmartObservable>();

    public SmartObserver(Handler handler, ContentResolver resolver) {
        super(handler);
        mResolver = resolver;
    }

    public void addListener(SmartObservable listener) {
        if (listener != null) {
            mListeners.add(listener);
            for (Uri uri : listener.onGetUris()) {
                mResolver.registerContentObserver(uri, false, this, UserHandle.USER_ALL);
            }
        }
    }

    /**
     * Just in case the ContentObserver is unregistered
     * but we want to keep callbacks registered
     */
    public void registerListeners() {
        for (SmartObservable listener : mListeners) {
            for (Uri uriz : listener.onGetUris()) {
                mResolver.registerContentObserver(uriz, false, this, UserHandle.USER_ALL);
            }
        }
    }

    public void cleanUp() {
        mListeners.clear();
        mResolver.unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        for (SmartObservable listener : mListeners) {
            for (Uri uriz : listener.onGetUris()) {
                if (TextUtils.equals(uriz.toString(), uri.toString())) {
                    listener.onChange(uri);
                }
            }
        }
    }
}
