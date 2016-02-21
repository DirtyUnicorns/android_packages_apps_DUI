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
import com.android.internal.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.DUActionUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
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

public class FlingView extends BaseNavigationBar {
    final static String TAG = FlingView.class.getSimpleName();

    private static Set<Uri> sUris = new HashSet<Uri>();    
    static {
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGPRESS_TIMEOUT));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_RIPPLE_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_RIPPLE_COLOR));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_COLOR));
    }

    private FlingActionHandler mActionHandler;
    private FlingGestureHandler mGestureHandler;
    private FlingGestureDetectorPriv mGestureDetector;
    private final FlingBarTransitions mBarTransitions;
    private FlingLogoController mLogoController;
    private boolean mRippleEnabled;
    private PowerManager mPm;
    private FlingRipple mRipple;
    private FlingTrails mTrails;

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

    public FlingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Bundle configs = getConfigs();
        mBarTransitions = new FlingBarTransitions(this);
        mActionHandler = new FlingActionHandler(context, this);
        mGestureHandler = new FlingGestureHandler(context, mActionHandler, this, configs);
        mGestureDetector = new FlingGestureDetectorPriv(context, mGestureHandler, configs);
        setOnTouchListener(mFlingTouchListener);
        mPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mRipple = new FlingRipple(this);
        mTrails = new FlingTrails(this);
        mLogoController = new FlingLogoController(this);

        mSmartObserver.addListener(mActionHandler);
        mSmartObserver.addListener(mGestureHandler);
        mSmartObserver.addListener(mLogoController);
        mSmartObserver.addListener(mObservable);
    }

    private Bundle getConfigs() {
        try {
            Bundle b = ActionConstants.getDefaults(ActionConstants.FLING).getConfigs(mContext);
            Log.i(TAG, "Got config bundle! dump: " + b.toString());
            return b;
        } catch (Exception e) {
            return null; // it's all over anyways
        }
    }

    private final AnimationListener mPulseOnListener = new AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            mPulse.turnOnPulse();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    private final Runnable mAnimateShowLogo = new Runnable() {
        @Override
        public void run() {
            mLogoController.unlockAndShow(null);            
        }        
    };

    @Override
    public boolean onStartPulse(Animation animatePulseIn) {
        final boolean hasLogo = mLogoController.isEnabled();
        if (hasLogo) {
            mLogoController.hideAndLock(mPulseOnListener);
            return true;
        }
        return false;
    }

    @Override
    public void onStopPulse(Animation animatePulseOut) {
        getHandler().removeCallbacks(mAnimateShowLogo);
        getHandler().postDelayed(mAnimateShowLogo, 250);        
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
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mLogoController.setLogoIcon();
        updateFlingSettings();
    }

    private void updateRippleColor() {
        int color = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_RIPPLE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mRipple.updateColor(color);
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
    public void updateNavbarThemedResources(Resources res) {
//        mRipple.updateResources(res);
        mLogoController.setLogoIcon();
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
                        iv.setImageDrawable(res.getDrawable(
                                DUActionUtils.getIdentifier(getContext(),
                                        "ic_sysbar_lights_out_dot_large", "drawable",
                                        DUActionUtils.PACKAGE_SYSTEMUI)));                   
                    }
                }
            }
        }
    }

    private void updateFlingSettings() {
        updateRippleColor();
        updateTrailsEnabled();
        updateTrailsColor();
        int lpTimeout = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LONGPRESS_TIMEOUT, FlingGestureDetectorPriv.LP_TIMEOUT_MAX, UserHandle.USER_CURRENT);
        mGestureDetector.setLongPressTimeout(lpTimeout);
        mRippleEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_RIPPLE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;  
    }

    @Override
    public void reorient() {
        super.reorient();
        mBarTransitions.init();
        mLogoController.setLogoView(getFlingLogo());
        mLogoController.setLogoIcon();
        mGestureHandler.setIsVertical(mVertical);
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        mGestureHandler.onScreenStateChanged(screenOn);
        super.notifyScreenOn(screenOn);
    }

    @Override
    protected void onInflateFromUser() {
        mGestureHandler.onScreenStateChanged(mScreenOn);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRipple.onSizeChanged(w, h, oldw, oldh);
        mTrails.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void setNavigationIconHints(int hints) {
        // maybe do something with the IME switcher
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mRippleEnabled) {
            mRipple.onDraw(canvas);
        }
        if (mTrails.isEnabled()) {
            mTrails.onDraw(canvas);
        }
    }

    @Override
    protected void onDispose() {
    }

	@Override
	public void setStatusBarCallbacks(StatusbarImpl statusbar) {
		// TODO Auto-generated method stub
		
	}
}
