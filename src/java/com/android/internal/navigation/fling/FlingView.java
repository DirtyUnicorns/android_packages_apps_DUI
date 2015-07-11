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
 * Gesture based navigation implementation and action executor
 *
 */

package com.android.internal.navigation.fling;


import com.android.internal.navigation.BarTransitions;
import com.android.internal.navigation.BaseNavigationBar;
import com.android.internal.navigation.fling.FlingGestureDetector;
import com.android.internal.navigation.fling.pulse.PulseController;
import com.android.internal.navigation.utils.LavaLamp;
import com.android.internal.actions.ActionUtils;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;

public class FlingView extends BaseNavigationBar implements FlingModule.Callbacks {
    final static String TAG = FlingView.class.getSimpleName();

    private static final int MSG_SET_DISABLED_FLAGS = 101;
    private static final int MSG_INVALIDATE = 102;

    private FlingActionHandler mActionHandler;
    private FlingGestureHandler mGestureHandler;
    private FlingGestureDetectorPriv mGestureDetector;
    private final FlingBarTransitions mBarTransitions;
    private FlingObserver mObserver;
    private boolean mRippleEnabled;
    private PulseController mPulse;
    private PowerManager mPm;
    private FlingRipple mRipple;
    private FlingTrails mTrails;
    private UiHandler mUiHandler = new UiHandler();

    private static final class FlingGestureDetectorPriv extends FlingGestureDetector {
        static final int LP_TIMEOUT = ViewConfiguration.getLongPressTimeout();
        // no more than default timeout
        static final int LP_TIMEOUT_MAX = LP_TIMEOUT;
        // no less than 25ms longer than single tap timeout
        static final int LP_TIMEOUT_MIN = 25;
        private int mLongPressTimeout = LP_TIMEOUT;

        public FlingGestureDetectorPriv(Context context, OnGestureListener listener) {
            super(context, listener);
            // TODO Auto-generated constructor stub
        }

        @Override
        protected int getLongPressTimeout() {
            return mLongPressTimeout;
        }

        void setLongPressTimeout(int timeoutFactor) {
            if (timeoutFactor > LP_TIMEOUT_MAX) {
                timeoutFactor = LP_TIMEOUT_MAX;
            } else if (timeoutFactor < LP_TIMEOUT_MIN) {
                timeoutFactor = LP_TIMEOUT_MIN;
            }
            mLongPressTimeout = timeoutFactor;
        }
    }

    private class FlingObserver extends ContentObserver {

        public FlingObserver(Handler handler) {
            super(handler);
        }

        void register() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NX_LOGO_VISIBLE), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NX_LOGO_ANIMATES), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NX_PULSE_ENABLED), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NX_LONGPRESS_TIMEOUT), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NX_RIPPLE_ENABLED), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.FLING_RIPPLE_COLOR), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NX_LOGO_COLOR), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.FLING_TRAILS_ENABLED), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.FLING_TRAILS_COLOR), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.FLING_PULSE_COLOR), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.FLING_PULSE_LAVALAMP_ENABLED), false,
                    FlingObserver.this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.FLING_PULSE_LAVALAMP_SPEED), false,
                    FlingObserver.this, UserHandle.USER_ALL);
        }

        void unregister() {
            mContext.getContentResolver().unregisterContentObserver(
                    FlingObserver.this);
        }

        public void onChange(boolean selfChange, Uri uri) {
            updateRippleColor();
            updateLogoEnabled();
            updateLogoAnimates();
            updateLogoColor();
            updateTrailsEnabled();
            updateTrailsColor();
            updatePulseEnabled();
            updatePulseColor();
            updateLavaLampEnabled();
            updateLavaLampSpeed();
            int lpTimeout = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NX_LONGPRESS_TIMEOUT, FlingGestureDetectorPriv.LP_TIMEOUT_MAX, UserHandle.USER_CURRENT);
            mGestureDetector.setLongPressTimeout(lpTimeout);
            mRippleEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NX_RIPPLE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        }
    }

    private class UiHandler extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_SET_DISABLED_FLAGS:
                    setDisabledFlags(mDisabledFlags, true);
                    break;
                case MSG_INVALIDATE:
                    FlingView.this.invalidate();
                    break;
            }
        }
    }

    private final OnTouchListener mNxTouchListener = new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();
            if (mUserAutoHideListener != null) {
                mUserAutoHideListener.onTouch(FlingView.this, event);
            }
            if (action == MotionEvent.ACTION_DOWN) {
                mPm.cpuBoost(1000 * 1000);
                if (!getNxLogo().isAnimating()) {
                    getNxLogo().animateSpinner(true);
                }
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                getNxLogo().animateSpinner(false);
            }
            if (mRippleEnabled) {
                mRipple.onTouch(FlingView.this, event);
            }
            if (mTrails.isEnabled()) {
                mTrails.onTouch(FlingView.this, event);
            }
            return mGestureDetector.onTouchEvent(event);
        }
    };

    public FlingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBarTransitions = new FlingBarTransitions(this);
        mDelegateHelper.setForceDisabled(true);
        mActionHandler = new FlingActionHandler(context, this);
        mGestureHandler = new FlingGestureHandler(context, mActionHandler, this);
        mGestureDetector = new FlingGestureDetectorPriv(context, mGestureHandler);
        setOnTouchListener(mNxTouchListener);
        mObserver = new FlingObserver(mUiHandler);
        mObserver.register();
        int lpTimeout = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LONGPRESS_TIMEOUT, FlingGestureDetectorPriv.LP_TIMEOUT_MAX, UserHandle.USER_CURRENT);
        mRippleEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_RIPPLE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mGestureDetector.setLongPressTimeout(lpTimeout);
        mPulse = new PulseController(context, this);
        mPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mRipple = new FlingRipple(this);
        mTrails = new FlingTrails(this, this);
    }

    private int findViewByIdName(String name) {
        return ActionUtils.getIdentifierByName(getContext(), name,
                ActionUtils.PACKAGE_SYSTEMUI);
    }

    @Override
    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public View getNxContainer() {
        return mCurrentView.findViewById(findViewByIdName("nav_buttons"));
    }

    public FlingLogoView getNxLogo() {
        return (FlingLogoView) mCurrentView.findViewById(findViewByIdName("fling_console"));
    }

    @Override
    protected void onKeyguardShowing(boolean showing) {
        mActionHandler.setKeyguardShowing(showing);
        mPulse.setKeyguardShowing(showing);
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        updateRippleColor();
        updateLogoEnabled();
        updateLogoAnimates();
        updateLogoColor();
        updateTrailsEnabled();
        updateTrailsColor();
        updatePulseEnabled();
        updatePulseColor();
        updateLavaLampEnabled();
        updateLavaLampSpeed();
    }

    @Override
    public void setLeftInLandscape(boolean leftInLandscape) {
        super.setLeftInLandscape(leftInLandscape);
        mPulse.setLeftInLandscape(leftInLandscape);
    }

    private void updateLogoAnimates() {
        boolean logoAnimates = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_ANIMATES, 1, UserHandle.USER_CURRENT) == 1;
        for (FlingLogoView v : ActionUtils.getAllChildren(FlingView.this, FlingLogoView.class)) {
            v.setSpinEnabled(logoAnimates);
        }
    }

    private void updateLogoColor() {
        int color = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        for (FlingLogoView v : ActionUtils.getAllChildren(FlingView.this, FlingLogoView.class)) {
            v.setLogoColor(color);
        }
    }

    private void updateLogoEnabled() {
        boolean logoEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_VISIBLE, 1, UserHandle.USER_CURRENT) == 1;
        for (FlingLogoView v : ActionUtils.getAllChildren(FlingView.this, FlingLogoView.class)) {
            v.setLogoEnabled(logoEnabled);
        }
        setDisabledFlags(mDisabledFlags, true);
    }

    private void updateRippleColor() {
        int color = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FLING_RIPPLE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mRipple.updateColor(color);
    }

    private void updatePulseEnabled() {
        boolean doPulse = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_PULSE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mPulse.setPulseEnabled(doPulse);
    }

    private void updatePulseColor() {
        int color = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FLING_PULSE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mPulse.updateRenderColor(color);
    }

    private void updateLavaLampEnabled() {
        boolean doLava = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FLING_PULSE_LAVALAMP_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mPulse.setLavaLampEnabled(doLava);
    }

    private void updateLavaLampSpeed() {
        int time = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FLING_PULSE_LAVALAMP_SPEED, LavaLamp.ANIM_DEF_DURATION,
                UserHandle.USER_CURRENT);
        mPulse.setLavaAnimationTime(time);
    }

    private void updateTrailsEnabled() {
        boolean enabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FLING_TRAILS_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mTrails.setTrailsEnabled(enabled);
    }

    private void updateTrailsColor() {
        int color = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.FLING_TRAILS_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mTrails.setTrailColor(color);
    }

    @Override
    protected void onUpdateResources(Resources res) {
//        mRipple.updateResources(res);
        for (FlingLogoView v : ActionUtils.getAllChildren(FlingView.this, FlingLogoView.class)) {
            v.updateResources(res);
        }
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            ViewGroup lightsOut = (ViewGroup) container.findViewById(findViewByIdName("lights_out"));
            if (lightsOut != null) {
                final int nChildren = lightsOut.getChildCount();
                for (int j = 0; j < nChildren; j++) {
                    final View child = lightsOut.getChildAt(j);
                    if (child instanceof ImageView) {
                        final ImageView iv = (ImageView) child;
                        // clear out the existing drawable, this is required since the
                        // ImageView keeps track of the resource ID and if it is the same
                        // it will not update the drawable.
                        iv.setImageDrawable(null);
                        iv.setImageDrawable(getAvailableResources().getDrawable(
                                ActionUtils.getIdentifier(getContext(),
                                        "ic_sysbar_lights_out_dot_large", "drawable",
                                        ActionUtils.PACKAGE_SYSTEMUI)));                   
                    }
                }
            }
//            ImageView ime = (ImageView) container.findViewById(R.id.ime_switcher);
//            if (ime != null) {
//                ime.setImageDrawable(null);
//                ime.setImageDrawable(res.getDrawable(R.drawable.ic_ime_switcher_default));
//            }
        }
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        super.setDisabledFlags(disabledFlags, force);
        mGestureHandler.onScreenStateChanged(mScreenOn);
        getNxLogo().updateVisibility(mPulse.shouldDrawPulse());
    }

    @Override
    public void reorient() {
        super.reorient();
        mBarTransitions.init(mVertical);
        mGestureHandler.setIsVertical(mVertical);
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        mPulse.notifyScreenOn(screenOn);
        super.notifyScreenOn(screenOn);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mPulse.onSizeChanged(w, h, oldw, oldh);
        mRipple.onSizeChanged(w, h, oldw, oldh);
        mTrails.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void setNavigationIconHints(int hints) {
        // maybe do something with the IME switcher
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mPulse.isPulseEnabled()) {
            mPulse.onDraw(canvas);
        }
        if (mRippleEnabled) {
            mRipple.onDraw(canvas);
        }
        if (mTrails.isEnabled()) {
            mTrails.onDraw(canvas);
        }
    }

    @Override
    protected void onDispose() {
        mObserver.unregister();
        mActionHandler.unregister();
        mGestureHandler.unregister();
        if (mPulse.isPulseEnabled()) {
            mPulse.setPulseEnabled(false);
        }
    }

    @Override
    public int onGetWidth() {
        return getWidth();
    }

    @Override
    public int onGetHeight() {
        return getHeight();
    }

    @Override
    public void onInvalidate() {
        mUiHandler.obtainMessage(MSG_INVALIDATE).sendToTarget();
    }

    @Override
    public void onUpdateState() {
        mUiHandler.obtainMessage(MSG_SET_DISABLED_FLAGS).sendToTarget();
    }
}
