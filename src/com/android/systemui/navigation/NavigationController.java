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
 * Middleware for providing bar implementation to PhoneStatusBar. Manage
 * some global state changes affecting bars
 *
 */

package com.android.systemui.navigation;

import com.android.systemui.recents.ScreenPinningRequest;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler.ActionIconResources;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.UserContentObserver;
import com.android.internal.utils.du.DUPackageMonitor.PackageChangedListener;
import com.android.internal.utils.du.DUPackageMonitor.PackageState;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.NavigationController;
import com.android.systemui.navigation.Navigator;
import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.navigation.smartbar.SmartBarView;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.R;

import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

public class NavigationController implements PackageChangedListener {
    private static final String TAG = NavigationController.class.getSimpleName();

    public static final int NAVIGATION_MODE_SMARTBAR = 0;
    public static final int NAVIGATION_MODE_FLING = 1;

    private PhoneStatusBar mBar;
    private Navigator mNavigationBarView;
    private NavbarObserver mNavbarObserver;
    private Handler mHandler = new Handler();
    private Context mContext;
    private WindowManager mWindowManager;
    private PulseController mPulseController;
    private final NavbarOverlayResources mResourceMap;
    private boolean mScreenPinningEnabled;
    private Configuration mConfiguration;

    private final Runnable mAddNavbar = new Runnable() {
        @Override
        public void run() {
            mBar.forceAddNavigationBar(false);
        }
    };

    private final Runnable mAddAndHideNavbar = new Runnable() {
        @Override
        public void run() {
            mBar.forceAddNavigationBar(true);
            // we have a race condition between recreating the bar
            // and keyguard coming up when exiting screenpinning
            // bar may still be null when we get the keyguard state
            // change through the pipes
            if (mNavigationBarView != null) {
                mNavigationBarView.setKeyguardShowing(true);
            }
        }
    };

    public static class NavbarOverlayResources extends ActionIconResources {
        public int mOpaque;
        public int mSemiTransparent;
        public int mTransparent;
        public int mWarning;
        public Drawable mGradient;
        public Drawable mFlingLogo;
        public Drawable mLightsOutLarge;

        public NavbarOverlayResources(Context ctx, Resources res) {
            super(res);
//            mOpaque = res.getColor(R.color.navigation_bar_background_opaque);
//            mSemiTransparent = res.getColor(R.color.navigation_bar_background_semi_transparent);
//            mTransparent = res.getColor(R.color.navigation_bar_background_transparent);
            mWarning = res.getColor(com.android.internal.R.color.battery_saver_mode_color);
            mGradient = res.getDrawable(R.drawable.nav_background);
            mFlingLogo = res.getDrawable(R.drawable.ic_eos_fling);
            mLightsOutLarge = res.getDrawable(R.drawable.ic_sysbar_lights_out_dot_large);
        }

        public void updateResources(Resources res) {
            super.updateResources(res);
//            mOpaque = res.getColor(R.color.navigation_bar_background_opaque);
//            mSemiTransparent = res.getColor(R.color.navigation_bar_background_semi_transparent);
//            mTransparent = res.getColor(R.color.navigation_bar_background_transparent);
            mWarning = res.getColor(com.android.internal.R.color.battery_saver_mode_color);
            Rect bounds = mGradient.getBounds();
            mGradient = res.getDrawable(R.drawable.nav_background);
            mGradient.setBounds(bounds);
            mFlingLogo = res.getDrawable(R.drawable.ic_eos_fling);
            mLightsOutLarge = res.getDrawable(R.drawable.ic_sysbar_lights_out_dot_large);
        }
    }

    public NavigationController(Context context, Resources themedRes, PhoneStatusBar statusBar) {
        mContext = context;
        mBar = statusBar;
        mResourceMap = new NavbarOverlayResources(context, themedRes);
        mConfiguration = new Configuration();
        mConfiguration.updateFrom(context.getResources().getConfiguration());
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mNavbarObserver = new NavbarObserver(mHandler);
        mNavbarObserver.observe();
        mPulseController = new PulseController(mContext, mHandler);
        PulseController.setVisualizerLocked(false); // in case SystemUI crashed and coming back up, don't keep it locked
    }

    public boolean onConfigurationChanged(Configuration newConfig) {
        boolean handled = false;
        if (mConfiguration.densityDpi != newConfig.densityDpi && mNavigationBarView != null) {
            removeNavigationBar();
            mHandler.postDelayed(mAddNavbar, 500);
            handled = true;
        }
        mConfiguration.updateFrom(newConfig);
        return handled;
    }

    public void recreateNavigationBar(Context context) {
        int navMode = Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_MODE, NAVIGATION_MODE_SMARTBAR,
                UserHandle.USER_CURRENT);
        final boolean screenPinningEnabled = mScreenPinningEnabled;

        if (mNavigationBarView != null) {
            mNavigationBarView.dispose();
            mNavigationBarView = null;
        }

        if (navMode == NAVIGATION_MODE_SMARTBAR || screenPinningEnabled) {
            mNavigationBarView = new SmartBarView(context, screenPinningEnabled);
        } else {
            mNavigationBarView = (BaseNavigationBar) View.inflate(context, R.layout.fling_bar, null);
        }

        mNavigationBarView.setStatusBar(mBar);
        mNavigationBarView.setResourceMap(mResourceMap);
        mNavigationBarView.setControllers(mPulseController);
    }

    public Navigator getBar() {
        return mNavigationBarView;
    }

    public void updateNavbarOverlay(Resources res) {
        if (res == null) return;
        mResourceMap.updateResources(res);
        if (mNavigationBarView != null) {
            mNavigationBarView.updateNavbarThemedResources(res);
        }
    }

    public void destroy() {
        mNavbarObserver.unobserve();
        if (mNavigationBarView != null) {
            mNavigationBarView.dispose();
            mNavigationBarView = null;
        }
    }

    public void screenPinningStateChanged(boolean enabled) {
        if (mScreenPinningEnabled == enabled) {
            return;
        }
        mScreenPinningEnabled = enabled;
        mPulseController.setScreenPinningState(enabled);
        boolean showing = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_VISIBLE,
                DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0) != 0;
        if (mNavigationBarView != null) {
            removeNavigationBar();
        }
        // always show if we are navbar device by default
        // or hwkey device but want to show navbar
        if (DUActionUtils.hasNavbarByDefault(mContext) || showing) {
            if (isScreenLockUsed() && !enabled) {
                mHandler.postDelayed(mAddAndHideNavbar, 500);
            } else {
                mHandler.postDelayed(mAddNavbar, 500);
            }
        }
    }

    private void removeNavigationBar() {
        if (mNavigationBarView != null) {
            mNavigationBarView.dispose();
            if (mNavigationBarView.getBaseView().isAttachedToWindow()) {
                mWindowManager.removeView(mNavigationBarView.getBaseView());
            }
            mNavigationBarView = null;
        }
    }

    private boolean isScreenLockUsed() {
        int def = getCurrentSecurityTitle() != DUActionUtils.getIdentifier(mContext,
                "screen_pinning_unlock_none",
                DUActionUtils.STRING, "com.android.settings") ? 1 : 0;
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCK_TO_APP_EXIT_LOCKED, def) != 0;
    }

    private int getCurrentSecurityTitle() {
        LockPatternUtils lockPatternUtils = new LockPatternUtils(mContext);
        int quality = lockPatternUtils.getKeyguardStoredPasswordQuality(
                UserHandle.myUserId());
        switch (quality) {
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                return DUActionUtils.getIdentifier(mContext, "screen_pinning_unlock_pin",
                        DUActionUtils.STRING, "com.android.settings");
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_MANAGED:
                return DUActionUtils.getIdentifier(mContext, "screen_pinning_unlock_password",
                        DUActionUtils.STRING, "com.android.settings");
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                if (lockPatternUtils.isLockPatternEnabled(UserHandle.myUserId())) {
                    return DUActionUtils.getIdentifier(mContext, "screen_pinning_unlock_pattern",
                            DUActionUtils.STRING, "com.android.settings");
                }
        }
        return DUActionUtils.getIdentifier(mContext, "screen_pinning_unlock_none",
                DUActionUtils.STRING, "com.android.settings");
    }

    // for now, it makes sense to let PhoneStatusBar add/remove navbar view
    // from window manager. Define the add/remove runnables in PSB then pass
    // to us for handling
    class NavbarObserver extends UserContentObserver {

        NavbarObserver(Handler handler) {
            super(handler);
        }

        protected void unobserve() {
            super.unobserve();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        protected void observe() {
            super.observe();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.NAVIGATION_BAR_MODE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.NAVIGATION_BAR_VISIBLE), false, this, UserHandle.USER_ALL);
//            resolver.registerContentObserver(Settings.System.getUriFor(
//                    Settings.System.NAVBAR_LEFT_IN_LANDSCAPE), false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
        }

        public void onChange(boolean selfChange, Uri uri) {
            final ContentResolver resolver = mContext.getContentResolver();

//            if (uri.equals(Settings.System.getUriFor(Settings.System.NAVBAR_LEFT_IN_LANDSCAPE))
//                    && isBarShowingNow) {
//                boolean navLeftInLandscape = Settings.System.getIntForUser(resolver,
//                        Settings.System.NAVBAR_LEFT_IN_LANDSCAPE, 0, UserHandle.USER_CURRENT) == 1;
//                mBar.getNavigationBarView().setLeftInLandscape(navLeftInLandscape);
            if (uri.equals(Settings.Secure
                    .getUriFor(Settings.Secure.NAVIGATION_BAR_VISIBLE))) {
                boolean showing = Settings.Secure.getInt(resolver,
                        Settings.Secure.NAVIGATION_BAR_VISIBLE,
                        DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0) != 0;
                if (mNavigationBarView != null && !showing) {
                    removeNavigationBar();
                } else if (mNavigationBarView == null && showing) {
                    mHandler.post(mAddNavbar);
                }
                updateKeyDisabler();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.NAVIGATION_BAR_MODE))) {
                if (mNavigationBarView != null) {
                    removeNavigationBar();
                }
                mHandler.postDelayed(mAddNavbar, 500);
            }
        }

        @Override
        protected void update() {
            updateKeyDisabler();
        }

        private void updateKeyDisabler() {
            // only broadcast if updating from a user change
            // i.e. don't broadcast if user simply changed settings as
            // key disabler has already been invoked
            // Send a broadcast to Settings to update Key disabling when user changes
            if (!DUActionUtils.hasNavbarByDefault(mContext)) {
                Intent intent = new Intent("com.cyanogenmod.action.UserChanged");
                intent.setPackage("com.android.settings");
                mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
            }
        }
    }

    @Override
    public void onPackageChanged(String pkg, PackageState state) {
        if (state == PackageState.PACKAGE_REMOVED
                || state == PackageState.PACKAGE_CHANGED) {
            final Context ctx = mContext;
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!DUActionUtils.hasNavbarByDefault(ctx)) {
                        DUActionUtils.resolveAndUpdateButtonActions(ctx,
                                ActionConstants
                                        .getDefaults(ActionConstants.HWKEYS));
                    }
                    DUActionUtils
                            .resolveAndUpdateButtonActions(ctx, ActionConstants
                                    .getDefaults(ActionConstants.SMARTBAR));
                    DUActionUtils.resolveAndUpdateButtonActions(ctx,
                            ActionConstants.getDefaults(ActionConstants.FLING));
                }
            });
            thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
        }
    }
}
