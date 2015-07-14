/*
 * Copyright (C) 2015 The TeamEos Project
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
 * Manage logo settings and state. Public helper methods to simplify
 * animations and visibility. 
 *
 */

package com.android.internal.navigation.fling;

import java.util.HashSet;
import java.util.Set;

import com.android.internal.navigation.utils.SmartObserver.SmartObservable;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation.AnimationListener;

public class FlingLogoController implements SmartObservable {
    private static final int LOGO_ANIMATE_HIDE = 1;
    private static final int LOGO_ANIMATE_SHOW = 2;

    private static final int LOCK_DISABLED = 0;
    private static final int LOCK_SHOW = 1;
    private static final int LOCK_HIDDEN = 2;

    private static Set<Uri> sUris = new HashSet<Uri>();
    static {
        sUris.add(Settings.System.getUriFor(Settings.System.NX_LOGO_VISIBLE));
        sUris.add(Settings.System.getUriFor(Settings.System.NX_LOGO_ANIMATES));
        sUris.add(Settings.System.getUriFor(Settings.System.NX_LOGO_COLOR));
    }

    private Context mContext;
    private FlingLogoView mLogoView;

    private boolean mLogoEnabled;
    private boolean mAnimateTouchEnabled;
    private int mVisibilityLock;
    private int mLogoColor;
    private AnimationSet mShow = getSpinAnimation(LOGO_ANIMATE_SHOW);
    private AnimationSet mHide = getSpinAnimation(LOGO_ANIMATE_HIDE);

    public FlingLogoController(Context ctx) {
        mContext = ctx;
        initialize();
    }

    public void setLogoView(FlingLogoView view) {
        mLogoView = view;
        if (mLogoColor != view.getLogoColor()) {
            view.setLogoColor(mLogoColor);
        }
        view.animate().cancel();
        animateToCurrentState();
    }

    private void animateToCurrentState() {
        if (mLogoEnabled) {
            if (mVisibilityLock == LOCK_DISABLED || mVisibilityLock == LOCK_SHOW) {
                show(null);
            } else {
                hide(null);
            }
        } else {
            hide(null);
        }
    }

    private void show(AnimationListener listener) {
        mLogoView.animate().cancel();
        if (listener != null) {
            mShow.setAnimationListener(listener);
        }
        mLogoView.startAnimation(mShow);
    }

    private void hide(AnimationListener listener) {
        mLogoView.animate().cancel();
        if (listener != null) {
            mHide.setAnimationListener(listener);
        }
        mLogoView.startAnimation(mHide);
    }

    private boolean isLockEnabled() {
        return mVisibilityLock != LOCK_DISABLED;
    }

    private void setEnabled(boolean enabled) {
        mLogoEnabled = enabled;
        animateToCurrentState();
    }

    public boolean isEnabled() {
        return mLogoEnabled;
    }

    public void onTouchHide(AnimationListener listener) {
        if (!mLogoEnabled || !mAnimateTouchEnabled || isLockEnabled()) {
            return;
        }
        hide(listener);
    }

    public void onTouchShow(AnimationListener listener) {
        if (!mLogoEnabled || !mAnimateTouchEnabled || isLockEnabled()) {
            return;
        }
        show(listener);
    }

    public void showAndLock(AnimationListener listener) {
        if (!mLogoEnabled) {
            return;
        }
        unlockAndShow(listener);
        mVisibilityLock = LOCK_SHOW;
    }

    public void hideAndLock(AnimationListener listener) {
        if (!mLogoEnabled) {
            return;
        }
        unlockAndHide(listener);
        mVisibilityLock = LOCK_HIDDEN;
    }

    public void unlockAndShow(AnimationListener listener) {
        if (!mLogoEnabled) {
            return;
        }
        mVisibilityLock = LOCK_DISABLED;
        show(listener);
    }

    public void unlockAndHide(AnimationListener listener) {
        if (!mLogoEnabled) {
            return;
        }
        mVisibilityLock = LOCK_DISABLED;
        hide(listener);
    }

    @Override
    public Set<Uri> onGetUris() {
        return sUris;
    }

    @Override
    public void onChange(Uri uri) {
        updateSettings();
    }

    private void updateSettings() {
        boolean enabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_VISIBLE, 1, UserHandle.USER_CURRENT) == 1;
        boolean spinOnTouch = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_ANIMATES, 1, UserHandle.USER_CURRENT) == 1;
        int logoColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        if (mLogoColor != logoColor) {
            mLogoColor = logoColor;
            if (mLogoColor != mLogoView.getLogoColor()) {
                mLogoView.setLogoColor(mLogoColor);
            }
        }
        if (mLogoEnabled != enabled) {
            setEnabled(enabled);
        }
        if (mAnimateTouchEnabled != spinOnTouch) {
            mAnimateTouchEnabled = spinOnTouch;
        }
    }

    private void initialize() {
        mLogoEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_VISIBLE, 1, UserHandle.USER_CURRENT) == 1;
        mAnimateTouchEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_ANIMATES, 1, UserHandle.USER_CURRENT) == 1;
        mLogoColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NX_LOGO_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
    }

    public static AnimationSet getSpinAnimation(int mode) {
        final boolean makeHidden = mode == LOGO_ANIMATE_HIDE;
        final float from = makeHidden ? 1.0f : 0.0f;
        final float to = makeHidden ? 0.0f : 1.0f;
        final float fromDeg = makeHidden ? 0.0f : 360.0f;
        final float toDeg = makeHidden ? 360.0f : 0.0f;

        Animation scale = new ScaleAnimation(from, to, from, to, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        RotateAnimation rotate = new RotateAnimation(fromDeg, toDeg, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        AnimationSet animSet = new AnimationSet(true);
        animSet.setInterpolator(new LinearInterpolator());
        animSet.setDuration(150);
        animSet.setFillAfter(true);
        animSet.addAnimation(scale);
        animSet.addAnimation(rotate);
        return animSet;
    }
}
