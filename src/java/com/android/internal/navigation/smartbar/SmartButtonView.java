/**
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 SlimRoms
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
 */

package com.android.internal.navigation.smartbar;

import android.animation.Animator;

import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;

import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import java.lang.Math;

public class SmartButtonView extends ImageView {
    private static final String TAG = "StatusBar.KeyButtonView";
    private static final boolean DEBUG = false;

    // TODO: make this dynamic again
    private static final int DT_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();
    private static final int LP_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static int sSingleTapTimeout = ViewConfiguration.getTapTimeout();
    private static int sSingleTapTimeoutWithDT = sSingleTapTimeout + 175;
    private static int sLongPressTimeout = LP_TIMEOUT - 100;
    private static int sDoubleTapTimeout = DT_TIMEOUT;

    // TODO: Get rid of this
    public static final float DEFAULT_QUIESCENT_ALPHA = 1f;

    private long mDownTime;
    private long mUpTime;
    private int mTouchSlop;
    private float mDrawingAlpha = 1f;
    private float mQuiescentAlpha = DEFAULT_QUIESCENT_ALPHA;
    private Animator mAnimateToQuiescent = new ObjectAnimator();
    private boolean mInEditMode;

    private ButtonConfig mConfig;
    private SmartActionHandler mActionHandler;

    public SmartButtonView(Context context, SmartActionHandler actionHandler) {
        this(context);
        mActionHandler = actionHandler;
    }

    public SmartButtonView(Context context) {
        super(context);
        setDrawingAlpha(mQuiescentAlpha);
        setClickable(true);
        setLongClickable(false);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void loadRipple() {
        setBackground(new SmartButtonRipple(mContext, this));
    }

    public void setEditMode(boolean editMode) {
        mInEditMode = editMode;
    }

    public static void setLongPressTimeout(int lpTimeout) {
        sLongPressTimeout = lpTimeout;
    }

    public static void setDoubleTapTimeout(int dtTimeout) {
        sDoubleTapTimeout = dtTimeout;
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

    private int getSingleTapTimeout() {
        return hasDoubleAction() ? sSingleTapTimeoutWithDT : sSingleTapTimeout;
    }

    @Override
    public Resources getResources() {
        ThemeConfig themeConfig = mContext.getResources().getConfiguration().themeConfig;
        Resources res = null;
        if (themeConfig != null) {
            try {
                final String navbarThemePkgName = themeConfig.getOverlayForNavBar();
                final String sysuiThemePkgName = themeConfig.getOverlayForStatusBar();
                // Check if the same theme is applied for systemui, if so we can skip this
                if (navbarThemePkgName != null && !navbarThemePkgName.equals(sysuiThemePkgName)) {
                    res = mContext.getPackageManager().getThemedResourcesForApplication(
                            mContext.getPackageName(), navbarThemePkgName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // don't care since we'll handle res being null below
            }
        }

        return res != null ? res : super.getResources();
    }

    public void setQuiescentAlpha(float alpha, boolean animate) {
        mAnimateToQuiescent.cancel();
        alpha = Math.min(Math.max(alpha, 0), 1);
        if (alpha == mQuiescentAlpha && alpha == mDrawingAlpha)
            return;
        mQuiescentAlpha = alpha;
        if (DEBUG)
            Log.d(TAG, "New quiescent alpha = " + mQuiescentAlpha);
        if (animate) {
            mAnimateToQuiescent = animateToQuiescent();
            mAnimateToQuiescent.start();
        } else {
            setDrawingAlpha(mQuiescentAlpha);
        }
    }

    private ObjectAnimator animateToQuiescent() {
        return ObjectAnimator.ofFloat(this, "drawingAlpha", mQuiescentAlpha);
    }

    public float getQuiescentAlpha() {
        return mQuiescentAlpha;
    }

    public float getDrawingAlpha() {
        return mDrawingAlpha;
    }

    public void setDrawingAlpha(float x) {
        setImageAlpha((int) (x * 255));
        mDrawingAlpha = x;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mInEditMode) {
            return false;
        }

        final int action = ev.getAction();
        int x, y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (hasRecentAction()) {
                    ActionHandler.preloadRecentApps();
                }
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (hasSingleAction()) {
                    removeCallbacks(mSingleTap);
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                long diff = mDownTime - mUpTime; // difference between last up
                                                 // and now
                if (hasDoubleAction() && diff <= sDoubleTapTimeout) {
                    doDoubleTap();
                } else {
                    if (hasLongAction()) {
                        removeCallbacks(mCheckLongPress);
                        postDelayed(mCheckLongPress, sLongPressTimeout);
                    }
                    if (hasSingleAction()) {
                        postDelayed(mSingleTap, getSingleTapTimeout());
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (hasSingleAction()) {
                    removeCallbacks(mSingleTap);
                }
                if (hasLongAction()) {
                    removeCallbacks(mCheckLongPress);
                }
                ActionHandler.cancelPreloadRecentApps();
                break;
            case MotionEvent.ACTION_UP:
                mUpTime = SystemClock.uptimeMillis();
                boolean playSound;

                if (hasLongAction()) {
                    removeCallbacks(mCheckLongPress);
                }
                playSound = isPressed();
                setPressed(false);

                if (playSound) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }

                if (!hasDoubleAction() && !hasLongAction()) {
                    removeCallbacks(mSingleTap);
                    doSinglePress();
                }
                break;
        }

        return true;
    }

    private void doSinglePress() {
        if (mConfig != null) {
            if (mActionHandler != null
                    && mActionHandler.isSecureToFire(mConfig.getActionConfig(ActionConfig.PRIMARY)
                            .getAction())) {
                ActionHandler.performTask(mContext, mConfig.getActionConfig(ActionConfig.PRIMARY)
                        .getAction());
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
        }
    }

    private void doLongPress() {
        if (mConfig != null) {
            if (mActionHandler != null
                    && mActionHandler.isSecureToFire(mConfig.getActionConfig(ActionConfig.SECOND)
                            .getAction())) {
                removeCallbacks(mSingleTap);
                ActionHandler.performTask(mContext, mConfig.getActionConfig(ActionConfig.SECOND)
                        .getAction());
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            }
        }
    }

    private void doDoubleTap() {
        if (mConfig != null) {
            if (mActionHandler != null
                    && mActionHandler.isSecureToFire(mConfig.getActionConfig(ActionConfig.THIRD)
                            .getAction())) {
                ActionHandler.performTask(mContext, mConfig.getActionConfig(ActionConfig.THIRD)
                        .getAction());
            }
        }
    }

    private Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                removeCallbacks(mSingleTap);
                doLongPress();
            }
        }
    };

    private Runnable mSingleTap = new Runnable() {
        @Override
        public void run() {
            if (!isPressed()) {
                doSinglePress();
            }
        }
    };
}
