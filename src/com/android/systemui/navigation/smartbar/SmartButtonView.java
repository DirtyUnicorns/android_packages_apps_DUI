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
 */

package com.android.systemui.navigation.smartbar;

import com.android.systemui.navigation.smartbar.SmartBarView;
import com.android.systemui.navigation.smartbar.SmartButtonRipple;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringListener;

import android.content.Context;
//import android.content.res.ThemeConfig;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

public class SmartButtonView extends ImageView {
    private static final String TAG = "StatusBar.KeyButtonView";
    private static final boolean DEBUG = false;

    // TODO: make this dynamic again
    private static final int DT_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();
    private static final int LP_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    // AOSP values feel rather slow, shave off some slack
    private static int sLongPressTimeout = LP_TIMEOUT - 100;
    private static int sDoubleTapTimeout = DT_TIMEOUT - 100;

    // Rebound spring config
    private static double TENSION = 120;
    private static double FRICTION = 3;
    public static final int ANIM_STYLE_RIPPLE = 0;
    public static final int ANIM_STYLE_SPRING = 1;

    private boolean isDoubleTapPending;
    private boolean wasConsumed;
    private boolean mInEditMode;
    private boolean mScreenPinningEnabled;
    private ButtonConfig mConfig;
    private SmartBarView mHost;
    View.OnLongClickListener mLongPressBackListener;

    private Spring mSpring;
    private SpringListener mSpringListener = new SpringListener() {

        @Override
        public void onSpringActivate(Spring arg0) {}

        @Override
        public void onSpringAtRest(Spring arg0) {}

        @Override
        public void onSpringEndStateChange(Spring arg0) {}

        @Override
        public void onSpringUpdate(Spring spring) {
            float value = (float) spring.getCurrentValue();
            float scale = 1f - (value * 0.5f);
            setScaleX(scale);
            setScaleY(scale);
        }    
    };

    public SmartButtonView(Context context, SmartBarView host) {
        super(context);
        mHost = host;
        setClickable(true);
        setLongClickable(false);
    }

    public void setAnimationStyle(int style) {
        switch (style) {
            case ANIM_STYLE_RIPPLE:
                if (mSpring != null) {
                    if (getScaleX() != 1f || getScaleY() != 1f) {
                        mSpring.setCurrentValue(0f);
                    }
                    mSpring.removeListener(mSpringListener);
                    mSpring.destroy();
                    mSpring = null;
                }
                // this is causing NPE when user changes animation type
                //mHost.flushSpringSystem();
                if (getBackground() != null && getBackground() instanceof SmartButtonRipple) {
                    SmartButtonRipple background = (SmartButtonRipple) getBackground();
                    background.setEnabled(true);
                }
                break;
            case ANIM_STYLE_SPRING:
                mSpring = mHost.getSpringSystem().createSpring();
                mSpring.addListener(mSpringListener);
                SpringConfig config = new SpringConfig(TENSION, FRICTION);
                mSpring.setSpringConfig(config);
                if (getBackground() != null && getBackground() instanceof SmartButtonRipple) {
                    SmartButtonRipple background = (SmartButtonRipple) getBackground();
                    background.setEnabled(false);
                }
                break;
        }
    }

    private void fireActionIfSecure(String action) {
        final boolean keyguardShowing = mHost.isKeyguardShowing();
        if (!keyguardShowing
                || (keyguardShowing && ActionHandler.SYSTEMUI_TASK_BACK.equals(action))) {
            ActionHandler.performTask(mContext, action);
        }
    }

    public void loadRipple() {
        setBackground(new SmartButtonRipple(mContext, this));
    }

    public void setEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode && mSpring != null) {
            if (getScaleX() != 1f || getScaleY() != 1f) {
                mSpring.setCurrentValue(0f);
            }
        }
    }

    public void setScreenPinningMode(boolean enabled) {
        mScreenPinningEnabled = enabled;
    }

    public void setLongPressBackListener(View.OnLongClickListener longPressBackListener) {
        mLongPressBackListener = longPressBackListener;
    }

    public void setButtonConfig(ButtonConfig config) {
        mConfig = config;
        setTag(config.getTag());
        setLongClickable(hasLongAction());
    }

    private boolean hasSingleAction() {
        return mConfig != null && !mConfig.getActionConfig(ActionConfig.PRIMARY).hasNoAction();
    }

    private boolean hasLongAction() {
        return mConfig != null && !mConfig.getActionConfig(ActionConfig.SECOND).hasNoAction();
    }

    private boolean hasDoubleAction() {
        return mConfig != null && !mConfig.getActionConfig(ActionConfig.THIRD).hasNoAction();
    }

    private boolean hasRecentAction() {
        return hasRecentsSingle() || hasRecentsLong() || hasRecentsDouble();
    }

    private boolean hasRecentsSingle() {
        return mConfig != null && mConfig.getActionConfig(ActionConfig.PRIMARY).isActionRecents();
    }

    private boolean hasRecentsLong() {
        return mConfig != null && mConfig.getActionConfig(ActionConfig.SECOND).isActionRecents();
    }

    private boolean hasRecentsDouble() {
        return mConfig != null && mConfig.getActionConfig(ActionConfig.THIRD).isActionRecents();
    }

    public ButtonConfig getButtonConfig() {
        return mConfig;
    }

    // special case: double tap for screen off we never capture up motion event
    // reset spring value and add/remove listeners if screen on/off
    public void onScreenStateChanged(boolean screenOn) {
        wasConsumed = false;
        setPressed(false);
        if (mSpring != null) {
            if (screenOn) {
                mSpring.addListener(mSpringListener);
                if (getScaleX() != 1f || getScaleY() != 1f) {
                    mSpring.setCurrentValue(0f);
                }
            } else {
                mSpring.removeListener(mSpringListener);
            }
        }
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mInEditMode) {
            return false;
        }
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                if (mSpring != null) {
                    mSpring.setEndValue(1f);
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (isDoubleTapPending) {
                    isDoubleTapPending = false;
                    wasConsumed = true;
                    removeCallbacks(mDoubleTapTimeout);
                    doDoubleTap();
                } else {
                    wasConsumed = false;
                    if (hasRecentAction()) {
                        ActionHandler.preloadRecentApps();
                    }
                    if (hasLongAction() || mScreenPinningEnabled) {
                        removeCallbacks(mCheckLongPress);
                        postDelayed(mCheckLongPress, sLongPressTimeout);
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (hasLongAction() || mScreenPinningEnabled) {
                    removeCallbacks(mCheckLongPress);
                }
                removeCallbacks(mDoubleTapTimeout);
                wasConsumed = true;
                isDoubleTapPending = false;
                setPressed(false);
                if (mSpring != null) {
                    mSpring.setEndValue(0f);
                }
                break;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                if (mSpring != null) {
                    mSpring.setEndValue(0f);
                }
                if (hasLongAction() || mScreenPinningEnabled) {
                    removeCallbacks(mCheckLongPress);
                }
                if (hasDoubleAction()) {
                    if (wasConsumed) {
                        wasConsumed = false;
                        return true;
                    }
                    isDoubleTapPending = true;
                    postDelayed(mDoubleTapTimeout, sDoubleTapTimeout);
                } else {
                    if (!wasConsumed && hasSingleAction()) {
                        doSinglePress();
                    }
                }
                break;
        }
        return true;
    }

    private void doSinglePress() {
        isDoubleTapPending = false;
        if (mConfig != null) {
            String action = mConfig.getActionConfig(ActionConfig.PRIMARY).getAction();
            fireActionIfSecure(action);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        }
    }

    private void doLongPress() {
        isDoubleTapPending = false;
        wasConsumed = true;
        if (mScreenPinningEnabled && mLongPressBackListener != null) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            mLongPressBackListener.onLongClick(this);
        } else {
            if (mConfig != null) {
                String action = mConfig.getActionConfig(ActionConfig.SECOND).getAction();
                fireActionIfSecure(action);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            }
        }
    }

    private void doDoubleTap() {
        isDoubleTapPending = false;
        wasConsumed = true;
        if (mConfig != null) {
            String action = mConfig.getActionConfig(ActionConfig.THIRD).getAction();
            fireActionIfSecure(action);
        }
    }

    private Runnable mDoubleTapTimeout = new Runnable() {
        @Override
        public void run() {
            wasConsumed = false;
            isDoubleTapPending = false;
            doSinglePress();
        }
    };

    private Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                wasConsumed = true;
                isDoubleTapPending = false;
                removeCallbacks(mDoubleTapTimeout);
                doLongPress();
            }
        }
    };
}
