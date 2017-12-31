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
 * Manage logo settings and state. Public helper methods to simplify
 * animations and visibility. 
 *
 */

package com.android.systemui.navigation.fling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.android.systemui.R;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.fling.FlingLogoView;
import com.android.systemui.navigation.fling.FlingView;
import com.android.systemui.navigation.utils.SmartObserver.SmartObservable;
import com.android.systemui.statusbar.policy.KeyButtonDrawable;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.internal.utils.du.DUActionUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class FlingLogoController implements SmartObservable {
    private static final int LOGO_ANIMATE_HIDE = 1;
    private static final int LOGO_ANIMATE_SHOW = 2;

    private static final int LOCK_DISABLED = 0;
    private static final int LOCK_SHOW = 1;
    private static final int LOCK_HIDDEN = 2;

    public static final String FLING_LOGO_URI = "fling_custom_icon_config";

    private static Set<Uri> sUris = new HashSet<Uri>();
    static {
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LOGO_VISIBLE));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LOGO_ANIMATES));
        //sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LOGO_COLOR));
        sUris.add(Settings.Secure.getUriFor(FLING_LOGO_URI));
    }

    private Context mContext;
    private FlingView mHost;
    private FlingLogoView mLogoView;
    private ButtonConfig mLogoConfig;

    private boolean mLogoEnabled;
    private boolean mAnimateTouchEnabled;
    private int mLogoColor = -1;
    private int mVisibilityLock;
    private AnimationSet mShow = getSpinAnimation(LOGO_ANIMATE_SHOW);
    private AnimationSet mHide = getSpinAnimation(LOGO_ANIMATE_HIDE);

    public FlingLogoController(FlingView host) {
        mHost = host;
        mContext = host.getContext();
        initialize();
    }

    public void setLogoView(FlingLogoView view) {
        mLogoView = view;
        mLogoView.setLogoColor(mLogoColor);
        animateToCurrentState();
    }

    private void animateToCurrentState() {
        if (mLogoEnabled) {
            if (mVisibilityLock == LOCK_HIDDEN) {
                hide(null);
            } else {
                show(null);
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
        mVisibilityLock = LOCK_SHOW;
        if (mLogoEnabled) {
            show(listener);
        }
    }

    public void hideAndLock(AnimationListener listener) {
        mVisibilityLock = LOCK_HIDDEN;
        if (mLogoEnabled) {
            hide(listener);
        }
    }

    public void unlockAndShow(AnimationListener listener) {
        mVisibilityLock = LOCK_DISABLED;
        if (mLogoEnabled) {
            show(listener);
        }
    }

    public void unlockAndHide(AnimationListener listener) {
        mVisibilityLock = LOCK_DISABLED;
        if (mLogoEnabled) {
            hide(listener);
        }
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
        boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LOGO_VISIBLE, 1, UserHandle.USER_CURRENT) == 1;
        boolean spinOnTouch = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LOGO_ANIMATES, 1, UserHandle.USER_CURRENT) == 1;
        /*mLogoColor = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LOGO_COLOR, -1, UserHandle.USER_CURRENT);*/
        mLogoConfig = ButtonConfig.getButton(mContext, FLING_LOGO_URI, true);
        setLogoIcon();
        mLogoView.setLogoColor(mLogoColor);
        if (mLogoEnabled != enabled) {
            setEnabled(enabled);
        }
        if (mAnimateTouchEnabled != spinOnTouch) {
            mAnimateTouchEnabled = spinOnTouch;
        }
    }

    private void initialize() {
        mLogoEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LOGO_VISIBLE, 1, UserHandle.USER_CURRENT) == 1;
        mAnimateTouchEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LOGO_ANIMATES, 1, UserHandle.USER_CURRENT) == 1;
        /*mLogoColor = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.FLING_LOGO_COLOR, -1, UserHandle.USER_CURRENT);*/
        mLogoConfig = ButtonConfig.getButton(mContext, FLING_LOGO_URI, true);
    }

    public void updateLogo(FlingView fv, FlingLogoView lv) {
        mHost = fv;
        mContext = fv.getContext();
        mLogoConfig = ButtonConfig.getButton(mContext, FLING_LOGO_URI, true);
        setLogoView(lv);
        setLogoIcon();
    }

    void setLogoIcon() {
        final ViewGroup current = (ViewGroup) mHost.getCurrentView();
        final ViewGroup hidden = (ViewGroup) mHost.getHiddenView();
        ImageView currentLogo = (ImageView)current.findViewById(R.id.fling_console);
        ImageView hiddenLogo = (ImageView)hidden.findViewById(R.id.fling_console);
        currentLogo.setImageDrawable(null);
        currentLogo.setImageDrawable(getCurrentDrawable());
        hiddenLogo.setImageDrawable(null);
        hiddenLogo.setImageDrawable(getCurrentDrawable());
        updateButtonScalingAndPadding(currentLogo, mLogoConfig, mHost.isLandscape());
        updateButtonScalingAndPadding(hiddenLogo, mLogoConfig, !mHost.isLandscape());
    }

    Drawable getCurrentDrawable() {
        KeyButtonDrawable d = null;
        Drawable light = null;
        Drawable dark = null;
        if (mLogoConfig.hasCustomIcon() &&
                getConfigCustomIcon() != null) {
            light = getBitmapDrawable(mContext, getConfigCustomIcon()).mutate();
            dark = getBitmapDrawable(mContext, getConfigCustomIcon()).mutate();
            dark.setColorFilter(new PorterDuffColorFilter(0x4D353535, PorterDuff.Mode.SRC_ATOP));
            d = KeyButtonDrawable.create(light, dark);
            return d;
        }
        light = mHost.mResourceMap.mFlingLogo;
        dark = mHost.mResourceMap.mFlingLogoDark;
        d = KeyButtonDrawable.create(light, dark);
        return d;
    }

    // Helper to flatten AdaptiveIconDrawable layers to a single drawable
    private static BitmapDrawable getBitmapDrawable(Context ctx, Drawable d) {
        if (d instanceof BitmapDrawable) {
            return (BitmapDrawable) d;
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        Bitmap bmResult = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmResult);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return new BitmapDrawable(ctx.getResources(), bmResult);
    }

    private Drawable getConfigCustomIcon() {
        return mLogoConfig.getActionConfig(ActionConfig.PRIMARY).getCurrentCustomIcon(mContext);
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

    static int[] getAppIconPadding(Context ctx) {
        int[] padding = new int[4];
        // left
        padding[0] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, ctx
                .getResources()
                .getDisplayMetrics());
        // top
        padding[1] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, ctx
                .getResources()
                .getDisplayMetrics());
        // right
        padding[2] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, ctx
                .getResources()
                .getDisplayMetrics());
        // bottom
        padding[3] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                ctx.getResources()
                        .getDisplayMetrics());
        return padding;
    }

    public static void applyPaddingToView(ImageView v, int[] padding) {
        v.setPaddingRelative(padding[0], padding[1],
                padding[2], padding[3]);
    }

    static boolean buttonNeedsCustomPadding(ButtonConfig config) {
        boolean hasCustomIcon = config.hasCustomIcon();
        boolean hasNonSystemIcon = !config.isSystemAction();
        return hasCustomIcon || hasNonSystemIcon;
    }

    public static void updateButtonScalingAndPadding(ImageView v, ButtonConfig config, boolean landscape) {
        // all non-system action icons need some extra padding/scaling work
        final int[] appIconPadding = getAppIconPadding(v.getContext());
        if (buttonNeedsCustomPadding(config)) {
            if (landscape && !BaseNavigationBar.sIsTablet) {
                v.setPaddingRelative(appIconPadding[1], appIconPadding[0],
                        appIconPadding[3], appIconPadding[2]);
            } else {
                v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
                        appIconPadding[2], appIconPadding[3]);
            }
            v.setScaleType(ScaleType.CENTER_INSIDE);
        } else {
            if (landscape && BaseNavigationBar.sIsTablet) {
                v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
                        appIconPadding[2], appIconPadding[3]);
                v.setScaleType(ScaleType.CENTER_INSIDE);
            }
            v.setScaleType(BaseNavigationBar.sIsTablet ? ScaleType.CENTER_INSIDE : ScaleType.CENTER);
        }
    }
}
