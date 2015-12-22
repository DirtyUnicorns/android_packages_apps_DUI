/**
 * Copyright (C) 2014 The TeamEos Project
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
 * Manage SmartButtonView action states and action dispatch
 *
 */

package com.android.internal.navigation.smartbar;

import java.util.Random;

import com.android.internal.utils.du.ActionHandler;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewConfiguration;

public class SmartActionHandler {
    private static final int DT_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();
    private static final int LP_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    final int LP_TIMEOUT_MAX = LP_TIMEOUT;
    // no less than 25ms longer than single tap timeout
    final int LP_TIMEOUT_MIN = 25;

    private View mNavigationBarView;
    private Context mContext;
    private ContentResolver mResolver;
    private boolean mKeyguardShowing;

    public SmartActionHandler(View v) {
        mNavigationBarView = v;
        mContext = v.getContext();
        mResolver = v.getContext().getContentResolver();
    }

    public void setKeyguardShowing(boolean showing) {
        if (mKeyguardShowing != showing) {
            mKeyguardShowing = showing;
        }
    }

    public boolean isSecureToFire(String action) {
        return action == null
                || !mKeyguardShowing
                || (mKeyguardShowing && ActionHandler.SYSTEMUI_TASK_BACK.equals(action));
    }
/*
    private int getLongPressTimeout() {
        int lpTimeout = Settings.System
                .getIntForUser(mResolver, Settings.System.SOFTKEY_LONGPRESS_TIMEOUT, LP_TIMEOUT,
                        UserHandle.USER_CURRENT);
        if (lpTimeout > LP_TIMEOUT_MAX) {
            lpTimeout = LP_TIMEOUT_MAX;
        } else if (lpTimeout < LP_TIMEOUT_MIN) {
            lpTimeout = LP_TIMEOUT_MIN;
        }
        return lpTimeout;
    }
*/

}
