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

package com.android.systemui.navigation.fling;

import java.util.HashSet;
import java.util.Set;

import com.android.systemui.R;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.NavbarOverlayResources;
import com.android.systemui.navigation.fling.FlingActionHandler;
import com.android.systemui.navigation.fling.FlingBarTransitions;
import com.android.systemui.navigation.fling.FlingGestureDetector;
import com.android.systemui.navigation.fling.FlingGestureHandler;
import com.android.systemui.navigation.fling.FlingLogoController;
import com.android.systemui.navigation.fling.FlingLogoView;
import com.android.systemui.navigation.fling.FlingRipple;
import com.android.systemui.navigation.fling.FlingTrails;
import com.android.systemui.navigation.fling.FlingView;
import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.navigation.utils.SmartObserver.SmartObservable;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.internal.utils.du.ActionConstants;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.res.Resources;
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
    final static int PULSE_FADE_OUT_DURATION = 250;
    final static int PULSE_FADE_IN_DURATION = 200;
    final static float PULSE_LOGO_OPACITY = 0.6f;

    private static Set<Uri> sUris = new HashSet<Uri>();
    static {
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGPRESS_TIMEOUT));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_RIPPLE_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_RIPPLE_COLOR));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_ENABLED));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_COLOR));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_TRAILS_WIDTH));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_KEYBOARD_CURSORS));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LOGO_OPACITY));
    }

    private FlingActionHandler mActionHandler;
    private FlingGestureHandler mGestureHandler;
    private FlingGestureDetectorPriv mGestureDetector;
    private final FlingBarTransitions mBarTransitions;
    private FlingLogoController mLogoController;
    private boolean mRippleEnabled;
    private FlingRipple mRipple;
    private FlingTrails mTrails;
    private boolean mKeyboardCursors;
    private float mLogoOpacity;
    private boolean mIsNotificationPanelExpanded;

    private int mNavigationIconHints = 0;

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

    public static final class FlingGestureDetectorPriv extends FlingGestureDetector {
        static final int LP_TIMEOUT = 250;
        // no more than default timeout
        static final int LP_TIMEOUT_MAX = LP_TIMEOUT;
        // no less than 25ms longer than single tap timeout
        static final int LP_TIMEOUT_MIN = 25;
        private int mLongPressTimeout = LP_TIMEOUT;

        public FlingGestureDetectorPriv(Context context, OnGestureListener listener) {
            super(context, listener, null);
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

    @Override
    public void setNotificationPanelExpanded(boolean expanded) {
        mIsNotificationPanelExpanded = expanded;
    }

    private final OnTouchListener mFlingTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
//                mPm.cpuBoost(1000 * 1000);
                mLogoController.onTouchHide(null);
                setSlippery(mIsNotificationPanelExpanded ? true : false);
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                mLogoController.onTouchShow(null);
                setSlippery(true);
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
        mActionHandler = new FlingActionHandler(context, this);
        mGestureHandler = new FlingGestureHandler(context, mActionHandler, this, BaseNavigationBar.sIsTablet);
        mGestureDetector = new FlingGestureDetectorPriv(context, mGestureHandler);

        // CM bases: turn this on for an extra bump ;D
        //mPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        mRipple = new FlingRipple(this);
        mTrails = new FlingTrails(this);
        mLogoController = new FlingLogoController(this);

        mSmartObserver.addListener(mActionHandler);
        mSmartObserver.addListener(mGestureHandler);
        mSmartObserver.addListener(mLogoController);
        mSmartObserver.addListener(mObservable);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() != null) {
            final View v = (View)getParent();
            v.setOnTouchListener(mFlingTouchListener);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (getParent() != null) {
            final View v = (View)getParent();
            v.setOnTouchListener(null);
        }
    }

    @Override
    public boolean onStartPulse(Animation animatePulseIn) {
        if (mLogoController.isEnabled()) {
            getLogoView(getHiddenView()).setAlpha(PULSE_LOGO_OPACITY);
            getLogoView(getCurrentView()).animate()
                    .alpha(PULSE_LOGO_OPACITY)
                    .setDuration(PULSE_FADE_OUT_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator _a) {
                            // shouldn't be null, mPulse just called into us
                            if (mPulse != null) {
                                mPulse.turnOnPulse();
                            }
                        }
                    })
                    .start();
            return true;
        }
        return false;
    }

    @Override
    public void onStopPulse(Animation animatePulseOut) {
        if (mLogoController.isEnabled()) {
            getLogoView(getHiddenView()).setAlpha(mLogoOpacity);
            getLogoView(getCurrentView()).animate()
                    .alpha(mLogoOpacity)
                    .setDuration(PULSE_FADE_IN_DURATION)
                    .start();
        }
    }

    @Override
    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    @Override
    public LightBarTransitionsController getLightTransitionsController() {
        return mBarTransitions.getLightTransitionsController();
    }

    private FlingLogoView getFlingLogo() {
        return (FlingLogoView) mCurrentView.findViewById(R.id.fling_console);
    }

    @Override
    public void setLeftInLandscape(boolean leftInLandscape) {
        super.setLeftInLandscape(leftInLandscape);
        mGestureHandler.setLeftInLandscape(leftInLandscape);
    }

    @Override
    protected void onKeyguardShowing(boolean showing) {
        mActionHandler.setKeyguardShowing(showing);
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    @Override
    public void setResourceMap(NavbarOverlayResources resourceMap) {
        super.setResourceMap(resourceMap);
        mLogoController.updateLogo(FlingView.this, getFlingLogo());
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

    private void updateTrailsWidth() {
        int width = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_TRAILS_WIDTH, FlingTrails.TRAIL_WIDTH_DEFAULT,
                UserHandle.USER_CURRENT);
        mTrails.setTrailWidth(width);
    }

    @Override
    public void updateNavbarThemedResources(Resources res) {
//        mRipple.updateResources(res);
        super.updateNavbarThemedResources(res);
        final FlingLogoView logo = getFlingLogo();
        mLogoController.setLogoView(logo);
        mLogoController.setLogoIcon();
        setLogoOpacity();
    }

    private void updateFlingSettings() {
        updateRippleColor();
        updateTrailsEnabled();
        updateTrailsColor();
        updateTrailsWidth();
        int lpTimeout = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LONGPRESS_TIMEOUT, FlingGestureDetectorPriv.LP_TIMEOUT_MAX, UserHandle.USER_CURRENT);
        mGestureDetector.setLongPressTimeout(lpTimeout);
        mRippleEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_RIPPLE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mKeyboardCursors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_KEYBOARD_CURSORS, 1, UserHandle.USER_CURRENT) == 1;
        setLogoOpacity();
    }

    private void setLogoOpacity() {
        mLogoOpacity = alphaIntToFloat(Settings.Secure.getIntForUser(getContext().getContentResolver(),
                Settings.Secure.FLING_LOGO_OPACITY, 255, UserHandle.USER_CURRENT));
        if (mLogoController.isEnabled()) {
            getLogoView(getCurrentView()).setAlpha(isBarPulseFaded() ? PULSE_LOGO_OPACITY : mLogoOpacity);
            getLogoView(getHiddenView()).setAlpha(isBarPulseFaded() ? PULSE_LOGO_OPACITY : mLogoOpacity);
        }
    }

    @Override
    public void reorient() {
        super.reorient();
        mBarTransitions.init();
        final FlingLogoView logo = getFlingLogo();
        mLogoController.setLogoView(logo);
        mLogoController.setLogoIcon();
        setLogoOpacity();
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    boolean isBarPulseFaded() {
        if (mPulse == null) {
            return false;
        } else {
            return mPulse.shouldDrawPulse();
        }
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        mGestureHandler.onScreenStateChanged(screenOn);
        super.notifyScreenOn(screenOn);

        if (mLogoController.isEnabled()) {
            ImageView currentLogo = getLogoView(getCurrentView());
            ImageView hiddenLogo = getLogoView(getHiddenView());
            if (screenOn && (currentLogo.getAlpha() != mLogoOpacity || hiddenLogo.getAlpha() != mLogoOpacity)) {
                currentLogo.setAlpha(mLogoOpacity);
                hiddenLogo.setAlpha(mLogoOpacity);
            }
        }
    }

    private ImageView getLogoView(View v) {
        final ViewGroup viewGroup = (ViewGroup) v;
        ImageView logoView = (ImageView)viewGroup.findViewById(R.id.fling_console);
        return logoView;
    }

    public Drawable getLogoDrawable(boolean hiddenView) {
        return getLogoView(hiddenView ? getHiddenView() : getCurrentView()).getDrawable();
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
        if (hints == mNavigationIconHints) {
            return;
        }

        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        mNavigationIconHints = hints;
        mActionHandler.setImeActions(backAlt && mKeyboardCursors);
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
        //unsetListeners();
        removeAllViews();
    }

    /*private void unsetListeners() {
    }*/

    @Override
    public void setMediaPlaying(boolean playing) {
        PulseController mPulse = getPulseController();
        if (mPulse != null) {
            mPulse.setMediaPlaying(playing);
        }
    }
}
