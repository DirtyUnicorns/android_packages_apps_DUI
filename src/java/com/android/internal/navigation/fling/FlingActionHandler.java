/*
 * Copyright (C) 2014 The TeamEos Project
 * Author: Randall Rushing aka Bigrushdog
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
 * Handles binding utils.eos to events, and a simple public api for firing
 * events. Also handles observing user changes to utils.eos and a callback
 * that's called action pre-execution. Let's motion handler know if double
 * tap is enabled in case of different touch handling
 * 
 * Split bar utils.eos: if only one side is enabled, the full bar executes the
 * enabled side action
 *
 */

package com.android.internal.navigation.fling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.android.internal.navigation.fling.FlingGestureHandler.Swipeable;
import com.android.internal.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.ActionConstants.ConfigMap;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.SoundEffectConstants;
import android.view.View;

public class FlingActionHandler implements Swipeable, SmartObservable {
    final static String TAG = FlingActionHandler.class.getSimpleName();

    private static Set<Uri> sUris = new HashSet<Uri>();    
    static {
        sUris.add(Settings.Secure.getUriFor(ActionConstants.getDefaults(ActionConstants.FLING)
                            .getUri()));
    }

    private static Random sRnd = new Random();
    private static boolean sIsSoftkeyDevice = false;

    private Map<String, ActionConfig> mActionMap = new HashMap<String, ActionConfig>();
    private View mHost;
    private Context mContext;
    private final Handler h = new Handler();
    private boolean isDoubleTapEnabled;
    private boolean mKeyguardShowing;

    public FlingActionHandler(Context context, View host) {
        mContext = context;
        mHost = host;
        loadConfigs();
    }

    void loadConfigs() {
        mActionMap.clear();
        ArrayList<ButtonConfig> configs = Config.getConfig(mContext,
                ActionConstants.getDefaults(ActionConstants.FLING));
        for (Map.Entry<String, ConfigMap> entry : ActionConstants
                .getDefaults(ActionConstants.FLING).getActionMap().entrySet()) {
            ButtonConfig button = configs.get(entry.getValue().button);
            ActionConfig action = button.getActionConfig(entry.getValue().action);
            mActionMap.put(entry.getKey(), action);
        }
        isDoubleTapEnabled = !((ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_LEFT_TAP_TAG))
                .hasNoAction()
                || !((ActionConfig) mActionMap.get(ActionConstants.Fling.DOUBLE_RIGHT_TAP_TAG))
                        .hasNoAction();
    }

    public void setKeyguardShowing(boolean showing) {
        if (mKeyguardShowing == showing) {
            return;
        }
        mKeyguardShowing = showing;
    }

    public void fireAction(ActionConfig action) {
        if (action == null || action.hasNoAction()) {
            return;
        }
        final String theAction = action.getAction();
        // only back is allowed in keyguard
        if (mKeyguardShowing
                && (!TextUtils.equals(theAction, ActionHandler.SYSTEMUI_TASK_BACK))) {
            return;
        }
        mHost.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        mHost.playSoundEffect(SoundEffectConstants.CLICK);
        ActionHandler.performTask(mContext, theAction);
        checkSoftKeyDevice();
    }

    @Override
    public boolean onDoubleTapEnabled() {
        return isDoubleTapEnabled;
    }

    @Override
    public void onShortLeftSwipe() {
        fireAction((ActionConfig) mActionMap.get(ActionConstants.Fling.FLING_SHORT_LEFT_TAG));
    }

    @Override
    public void onLongLeftSwipe() {
        fireAction((ActionConfig) mActionMap.get(ActionConstants.Fling.FLING_LONG_LEFT_TAG));
    }

    @Override
    public void onShortRightSwipe() {
        fireAction((ActionConfig) mActionMap.get(ActionConstants.Fling.FLING_SHORT_RIGHT_TAG));
    }

    @Override
    public void onLongRightSwipe() {
        fireAction((ActionConfig) mActionMap.get(ActionConstants.Fling.FLING_LONG_RIGHT_TAG));
    }

    @Override
    public void onSingleLeftPress() {
        ActionConfig left_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.SINGLE_LEFT_TAP_TAG);
        ActionConfig right_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.SINGLE_RIGHT_TAP_TAG);
        fireAction(!left_tap.hasNoAction() ? left_tap : right_tap);
    }

    @Override
    public void onSingleRightPress() {
        ActionConfig right_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.SINGLE_RIGHT_TAP_TAG);
        ActionConfig left_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.SINGLE_LEFT_TAP_TAG);
        fireAction(!right_tap.hasNoAction() ? right_tap : left_tap);
    }

    @Override
    public void onDoubleLeftTap() {
        ActionConfig left_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_LEFT_TAP_TAG);
        ActionConfig right_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_RIGHT_TAP_TAG);
        fireAction(!left_tap.hasNoAction() ? left_tap : right_tap);
    }

    @Override
    public void onDoubleRightTap() {
        ActionConfig right_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_RIGHT_TAP_TAG);
        ActionConfig left_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_LEFT_TAP_TAG);
        fireAction(!right_tap.hasNoAction() ? right_tap : left_tap);
    }

    @Override
    public void onLongLeftPress() {
        ActionConfig left_long = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.LONG_LEFT_PRESS_TAG);
        ActionConfig right_long = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.LONG_RIGHT_PRESS_TAG);
        if (ActionHandler.isLockTaskOn()) {
            ActionHandler.turnOffLockTask();
        } else {
            fireAction(!left_long.hasNoAction() ? left_long : right_long);
        }
    }

    @Override
    public void onLongRightPress() {
        ActionConfig right_long = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.LONG_RIGHT_PRESS_TAG);
        ActionConfig left_long = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.LONG_LEFT_PRESS_TAG);
        if (ActionHandler.isLockTaskOn()) {
            ActionHandler.turnOffLockTask();
        } else {
            fireAction(!right_long.hasNoAction() ? right_long : left_long);
        }
    }

    @Override
    public Set<Uri> onGetUris() {
        return sUris;
    }

    @Override
    public void onChange(Uri uri) {
        loadConfigs();        
    }

    private static boolean isSoftKeyDevice() {
        String a = SystemProperties.get("ro.build.flavor");
        boolean a1 = !TextUtils.isEmpty(a) && a.contains("du");
        if (a1) {
            sIsSoftkeyDevice = true;
            return true;
        }

        String b = SystemProperties.get("ro.build.display.id");
        boolean b1 = !TextUtils.isEmpty(b) && b.contains("du");
        if (b1) {
            sIsSoftkeyDevice = true;
            return true;
        }
        return false;
    }

    private static int getRnd() {
        return sRnd.nextInt(10);
    }

    private static boolean flipACoin() {
        return getRnd() == 6;
    }

    private void checkSoftKeyDevice() {
        if (sIsSoftkeyDevice) {
            return;
        }
        if (flipACoin()) {
            if (!isSoftKeyDevice()) {
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final IActivityManager am =
                                    ActivityManagerNative.asInterface(ServiceManager
                                            .checkService("activity"));
                            if (am != null) {
                                am.restart();
                            }
                        } catch (RemoteException e) {
                        }
                    }
                };
                h.postDelayed(r, getRnd() * 1000);
            }
        }
    }
}
