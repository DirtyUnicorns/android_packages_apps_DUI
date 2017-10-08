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
 * Handles binding actions to events, and a simple public api for firing
 * events. Also handles observing user changes to actions and a callback
 * that's called action pre-execution. Let's motion handler know if double
 * tap is enabled in case of different touch handling 
 *
 */

package com.android.systemui.navigation.fling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.android.systemui.navigation.fling.FlingGestureHandler.Swipeable;
import com.android.systemui.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.ActionConstants.ConfigMap;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.SoundEffectConstants;
import android.view.View;

public class FlingActionHandler implements Swipeable, SmartObservable {
    final static String TAG = FlingActionHandler.class.getSimpleName();

    private static Set<Uri> sUris = new HashSet<Uri>();
    static {
        sUris.add(Settings.Secure.getUriFor(
        	ActionConstants.getDefaults(ActionConstants.FLING).getUri()));
    }

    private Map<String, ActionConfig> mActionMap = new HashMap<String, ActionConfig>();
    private View mHost;
    private Context mContext;
    private boolean isDoubleTapEnabled;
    private boolean mUseKbCursors;
    private boolean mKeyguardShowing;
    private boolean mOnTapPreloadedRecents;
    private boolean mOnSwipePreloadedRecents;

    // TODO: move these to ActionConstants and make the whole
    // preload code more granular to avoid unneeded preload tasks
    private final ArrayList<String> mRightTapActions = new ArrayList<>();
    private final ArrayList<String> mLeftTapActions = new ArrayList<>();
    private final ArrayList<String> mSwipeActions = new ArrayList<>();

    private void setRightTapActions() {
        mRightTapActions.clear();
        mRightTapActions.add("single_right_tap");
        mRightTapActions.add("double_right_tap");
        mRightTapActions.add("long_right_press");
    }

    private void setLeftTapActions() {
        mLeftTapActions.clear();
        mLeftTapActions.add("single_left_tap");
        mLeftTapActions.add("double_left_tap");
        mLeftTapActions.add("long_left_press");
    }

    private void setSwipeActions() {
        mSwipeActions.add("fling_short_right");
        mSwipeActions.add("fling_long_right");
        mSwipeActions.add("fling_right_up");
        mSwipeActions.add("fling_short_left");
        mSwipeActions.add("fling_long_left");
        mSwipeActions.add("fling_left_up");
    }

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
        setDoubleTapEnabled();
        setLeftTapActions();
        setRightTapActions();
        setSwipeActions();
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
    public void onUpRightSwipe() {
        ActionConfig left_swipe = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.FLING_LEFT_UP_TAG);
        ActionConfig right_swipe = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.FLING_RIGHT_UP_TAG);
        fireAction(!right_swipe.hasNoAction() ? right_swipe : left_swipe);
    }

    @Override
    public void onUpLeftSwipe() {
        ActionConfig left_swipe = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.FLING_LEFT_UP_TAG);
        ActionConfig right_swipe = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.FLING_RIGHT_UP_TAG);
        fireAction(!left_swipe.hasNoAction() ? left_swipe : right_swipe);
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

    protected void setImeActions(boolean enable) {
        mUseKbCursors = enable;
        setDoubleTapEnabled();
    }

    private void setDoubleTapEnabled() {
        isDoubleTapEnabled = mUseKbCursors || !((ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_LEFT_TAP_TAG))
                .hasNoAction()
                || !((ActionConfig) mActionMap.get(ActionConstants.Fling.DOUBLE_RIGHT_TAP_TAG))
                        .hasNoAction();
    }

    @Override
    public void onDoubleLeftTap() {
        if (mUseKbCursors) {
            ActionHandler.performTask(mContext, ActionHandler.SYSTEMUI_TASK_IME_NAVIGATION_LEFT);
            return;
        }

        ActionConfig left_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_LEFT_TAP_TAG);
        ActionConfig right_tap = (ActionConfig) mActionMap
                .get(ActionConstants.Fling.DOUBLE_RIGHT_TAP_TAG);
        fireAction(!left_tap.hasNoAction() ? left_tap : right_tap);
    }

    @Override
    public void onDoubleRightTap() {
        if (mUseKbCursors) {
            ActionHandler.performTask(mContext, ActionHandler.SYSTEMUI_TASK_IME_NAVIGATION_RIGHT);
            return;
        }

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
    public void onDownPreloadRecents(boolean isRight) {
        mOnTapPreloadedRecents = false;
        for (String flingAction : (isRight? mRightTapActions : mLeftTapActions)) {
            ActionConfig action = (ActionConfig) mActionMap.get(flingAction);
            if (action != null && !action.hasNoAction() && action.isActionRecents()) {
                ActionHandler.preloadRecentApps();
                mOnTapPreloadedRecents = true;
                return;
            }
        }
    }

    @Override
    public void onScrollPreloadRecents() {
        mOnSwipePreloadedRecents = false;
        for (String swipeAction : mSwipeActions) {
            ActionConfig action = (ActionConfig) mActionMap.get(swipeAction);
            if (action != null && !action.hasNoAction() && action.isActionRecents() && !mOnTapPreloadedRecents) {
                ActionHandler.preloadRecentApps();
                mOnSwipePreloadedRecents = true;
                return;
            }
        }
    }

    @Override
    public void onCancelPreloadRecents() {
        if (mOnTapPreloadedRecents || mOnSwipePreloadedRecents) {
            ActionHandler.cancelPreloadRecentApps();
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
}
