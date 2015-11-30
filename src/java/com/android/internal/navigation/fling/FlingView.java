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

import java.util.HashSet;
import java.util.Set;

import com.android.internal.navigation.BarTransitions;
import com.android.internal.navigation.BaseNavigationBar;
import com.android.internal.navigation.StatusbarImpl;
import com.android.internal.navigation.fling.FlingGestureDetector;
import com.android.internal.navigation.fling.pulse.PulseController;
import com.android.internal.navigation.utils.LavaLamp;
import com.android.internal.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.DUActionUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;

public class FlingView extends BaseNavigationBar implements FlingModule.Callbacks {
    final static String TAG = FlingView.class.getSimpleName();

    private static Set<Uri> sUris = new HashSet<Uri>();    
    static {
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGPRESS_TIMEOUT));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_RIPPLE_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_RIPPLE_COLOR));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_COLOR));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_COLOR));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_SPEED));
    }

    public static final int MSG_SET_DISABLED_FLAGS = 101;
    public static final int MSG_INVALIDATE = 102;

    private FlingActionHandler mActionHandler;
    private FlingGestureHandler mGestureHandler;
    private FlingGestureDetectorPriv mGestureDetector;
    private final FlingBarTransitions mBarTransitions;
    private FlingLogoController mLogoController;
    private boolean mRippleEnabled;
    private PulseController mPulse;
    private PowerManager mPm;
    private FlingRipple mRipple;
    private FlingTrails mTrails;
    private Drawable mLogo, mLogoLand;

    private SmartObservable mObservable = new SmartObservable() {
        @Override
        public Set<Uri> onGetUris() {
            return sUris;
        }

        @Override
        public void onChange(Uri uri) {
            updateFlingSettings();
        }
    };

    private static final class FlingGestureDetectorPriv extends FlingGestureDetector {
        static final int LP_TIMEOUT = ViewConfiguration.getLongPressTimeout();
        // no more than default timeout
        static final int LP_TIMEOUT_MAX = LP_TIMEOUT;
        // no less than 25ms longer than single tap timeout
        static final int LP_TIMEOUT_MIN = 25;
        private int mLongPressTimeout = LP_TIMEOUT;

        public FlingGestureDetectorPriv(Context context, OnGestureListener listener, Bundle config) {
            super(context, listener, config);
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

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
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
    };

    private final OnTouchListener mFlingTouchListener = new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();
            if (mUserAutoHideListener != null) {
                mUserAutoHideListener.onTouch(FlingView.this, event);
            }
            if (action == MotionEvent.ACTION_DOWN) {
//                mPm.cpuBoost(1000 * 1000);
                mLogoController.onTouchHide(null);
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                mLogoController.onTouchShow(null);
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

    private final AnimationListener mPulseOnListener = new AnimationListener() {

        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            mPulse.turnOnPulse();
            animation.setAnimationListener(null);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    public FlingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Bundle configs = getConfigs(context);
        mBarTransitions = new FlingBarTransitions(this);
        mActionHandler = new FlingActionHandler(context, this);
        mGestureHandler = new FlingGestureHandler(context, mActionHandler, this, configs);
        mGestureDetector = new FlingGestureDetectorPriv(context, mGestureHandler, configs);
        setOnTouchListener(mFlingTouchListener);
        mPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mRipple = new FlingRipple(this);
        mTrails = new FlingTrails(this, this);
        mLogoController = new FlingLogoController(context);

        mPulse = new PulseController(context, this, configs) {
            @Override
            public boolean onPrepareToPulse() {
                mLogoController.hideAndLock(mPulseOnListener);
                if (mLogoController.isEnabled()) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPulseStateChanged(int state) {
                if (state == PulseController.STATE_STOPPED) {
                    mLogoController.unlockAndShow(null);
                }
            }
        };

        mSmartObserver.addListener(mActionHandler);
        mSmartObserver.addListener(mGestureHandler);
        mSmartObserver.addListener(mLogoController);
        mSmartObserver.addListener(mObservable);
    }

    private Bundle getConfigs(Context ctx) {
        try {
            Bundle b = ActionConstants.getDefaults(ActionConstants.FLING).getConfigs(ctx);
            Log.i(TAG, "Got config bundle! dump: " + b.toString());
            return b;
        } catch (Exception e) {
            return null; // it's all over anyways
        }
    }

    private int findViewByIdName(String name) {
        return DUActionUtils.getId(getContext(), name,
                DUActionUtils.PACKAGE_SYSTEMUI);
    }

    @Override
    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    private FlingLogoView getFlingLogo() {
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
        updateFlingSettings();
    }

    @Override
    public void setLeftInLandscape(boolean leftInLandscape) {
        super.setLeftInLandscape(leftInLandscape);
        mPulse.setLeftInLandscape(leftInLandscape);
    }

    private void updateRippleColor() {
        int color = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_RIPPLE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mRipple.updateColor(color);
    }

    private void updatePulseEnabled() {
        boolean doPulse = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mPulse.setPulseEnabled(doPulse);
    }

    private void updatePulseColor() {
        int color = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mPulse.updateRenderColor(color);
    }

    private void updateLavaLampEnabled() {
        boolean doLava = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mPulse.setLavaLampEnabled(doLava);
    }

    private void updateLavaLampSpeed() {
        int time = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_PULSE_LAVALAMP_SPEED, LavaLamp.ANIM_DEF_DURATION,
                UserHandle.USER_CURRENT);
        mPulse.setLavaAnimationTime(time);
    }

    private void updateTrailsEnabled() {
        boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_TRAILS_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mTrails.setTrailsEnabled(enabled);
    }

    private void updateTrailsColor() {
        int color = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_TRAILS_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mTrails.setTrailColor(color);
    }

    @Override
    protected void onUpdateResources(Resources res) {
//        mRipple.updateResources(res);
        View v = mRot0.findViewById(findViewByIdName("fling_console"));
        if (v != null && v instanceof ImageView) {
            ImageView iv = (ImageView) v;
            mLogo = getAvailableResources().getDrawable(
                    DUActionUtils.getIdentifier(getContext(),
                            "ic_eos_fling", "drawable",
                            DUActionUtils.PACKAGE_SYSTEMUI));
            iv.setImageDrawable(null);
            iv.setImageDrawable(mLogo);
        }
        v = mRot90.findViewById(findViewByIdName("fling_console"));
        if (v != null && v instanceof ImageView) {
            ImageView iv = (ImageView) v;
            mLogoLand = getAvailableResources().getDrawable(
                    DUActionUtils.getIdentifier(getContext(),
                            "ic_eos_fling_land", "drawable",
                            DUActionUtils.PACKAGE_SYSTEMUI));
            iv.setImageDrawable(null);
            iv.setImageDrawable(mLogoLand);
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
                                DUActionUtils.getIdentifier(getContext(),
                                        "ic_sysbar_lights_out_dot_large", "drawable",
                                        DUActionUtils.PACKAGE_SYSTEMUI)));                   
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

    private void updateFlingSettings() {
        updateRippleColor();
        updateTrailsEnabled();
        updateTrailsColor();
        updatePulseEnabled();
        updatePulseColor();
        updateLavaLampEnabled();
        updateLavaLampSpeed();
        int lpTimeout = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LONGPRESS_TIMEOUT, FlingGestureDetectorPriv.LP_TIMEOUT_MAX, UserHandle.USER_CURRENT);
        mGestureDetector.setLongPressTimeout(lpTimeout);
        mRippleEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_RIPPLE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;  
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        super.setDisabledFlags(disabledFlags, force);        
    }

    @Override
    public void reorient() {
        super.reorient();
        mBarTransitions.init(mVertical);
        mLogoController.setLogoView(getFlingLogo());
        mGestureHandler.setIsVertical(mVertical);
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        mGestureHandler.onScreenStateChanged(screenOn);
        mPulse.notifyScreenOn(screenOn);
        super.notifyScreenOn(screenOn);
    }

    @Override
    protected void onInflateFromUser() {
        mGestureHandler.onScreenStateChanged(mScreenOn);
        mPulse.notifyScreenOn(mScreenOn);
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
        if (mPulse.isPulseEnabled()) {
            mPulse.setPulseEnabled(false);
        }
    }

    @Override
    public Handler getHandler() {
        return mHandler;
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
        mHandler.obtainMessage(MSG_INVALIDATE).sendToTarget();
    }

    @Override
    public void onUpdateState() {
        mHandler.obtainMessage(MSG_SET_DISABLED_FLAGS).sendToTarget();
    }

	@Override
	public void setStatusBarCallbacks(StatusbarImpl statusbar) {
		// TODO Auto-generated method stub
		
	}
}
