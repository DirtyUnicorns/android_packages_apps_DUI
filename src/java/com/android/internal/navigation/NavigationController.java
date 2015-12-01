/*
 * Copyright (C) 2014 The TeamEos Project
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
 * Helper functions mostly for device configuration and some utilities
 * including a fun ViewGroup crawler and dpi conversion
 * 
 */

package com.android.internal.navigation;

import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.UserContentObserver;
import com.android.internal.utils.du.DUPackageMonitor.PackageChangedListener;
import com.android.internal.utils.du.DUPackageMonitor.PackageState;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.IAudioService;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

public class NavigationController implements PackageChangedListener {
    private static final String TAG = NavigationController.class.getSimpleName();

    public static final int NAVIGATION_MODE_AOSP = 0;
    public static final int NAVIGATION_MODE_FLING = 1;
    private static final String NAVBAR_LAYOUT = "navigation_bar";
    private static final String FLING_LAYOUT = "fling_bar";

    private StatusbarImpl mBar;
    private NavbarObserver mNavbarObserver;
    private Handler mHandler = new Handler();
    private Runnable mAddNavbar;
    private Runnable mRemoveNavbar;
    private Context mContext;

    public NavigationController(Context context, StatusbarImpl statusBar,
            Runnable forceAddNavbar, Runnable removeNavbar) {
        mContext = context;
        mBar = statusBar;
        mAddNavbar = forceAddNavbar;
        mRemoveNavbar = removeNavbar;
        unlockVisualizer();
        mNavbarObserver = new NavbarObserver(mHandler);
        mNavbarObserver.observe();
    }

    public Navigator getNavigationBarView() {
        int navMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_MODE, NAVIGATION_MODE_AOSP, UserHandle.USER_CURRENT);
        String layout;
        switch (navMode) {
            case NAVIGATION_MODE_AOSP:
                layout = NAVBAR_LAYOUT;
                break;
            case NAVIGATION_MODE_FLING:
                layout = FLING_LAYOUT;
                break;
            default:
                layout = NAVBAR_LAYOUT;
        }
        // use for testing or lack of settings
        String override = SystemProperties.get("ro.fling.debug");
        if (override != null) {
            if ("0".equals(override)) {
                layout = NAVBAR_LAYOUT;
            } else if ("1".equals(override)) {
                layout = FLING_LAYOUT;
            }
        }
        return inflateBar(mContext, layout);
    }

    public void destroy() {
        mNavbarObserver.unobserve();
        unlockVisualizer(); // just to be sure
    }

    private void unlockVisualizer() {
        try {
            IBinder b = ServiceManager.getService(Context.AUDIO_SERVICE);
            IAudioService audioService = IAudioService.Stub.asInterface(b);
            audioService.setVisualizerLocked(false);
        } catch (RemoteException e) {
            Log.e(TAG, "Error unlocking visualizer when starting SystemUI");
        }
    }

    private BaseNavigationBar inflateBar(Context ctx, String layout) {
        BaseNavigationBar bar = null;
        try {
            bar = (BaseNavigationBar) View.inflate(ctx,
                    DUActionUtils.getIdentifier(ctx, layout,
                            "layout", DUActionUtils.PACKAGE_SYSTEMUI), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bar;
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
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVBAR_LEFT_IN_LANDSCAPE), false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
        }

        public void onChange(boolean selfChange, Uri uri) {
            final ContentResolver resolver = mContext.getContentResolver();
            final boolean isBarShowingNow = mBar.getNavigationBarView() != null; // sanity checks

            if (uri.equals(Settings.System.getUriFor(Settings.System.NAVBAR_LEFT_IN_LANDSCAPE))
                    && isBarShowingNow) {
                boolean navLeftInLandscape = Settings.System.getIntForUser(resolver,
                        Settings.System.NAVBAR_LEFT_IN_LANDSCAPE, 0, UserHandle.USER_CURRENT) == 1;
                mBar.getNavigationBarView().setLeftInLandscape(navLeftInLandscape);
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.NAVIGATION_BAR_VISIBLE))) {
                if (isBarShowingNow) {
                    mBar.getNavigationBarView().dispose();
                    mHandler.post(mRemoveNavbar);
                    updateKeyDisabler();
                }                
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.NAVIGATION_BAR_MODE))) {
                if (isBarShowingNow) {
                    mBar.getNavigationBarView().dispose();
                    mHandler.post(mRemoveNavbar);
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
                    if (!DUActionUtils.hasNavbarByDefault(mContext)) {
                        DUActionUtils.resolveAndUpdateButtonActions(ctx,
                                ActionConstants
                                        .getDefaults(ActionConstants.HWKEYS));
                    }
                    DUActionUtils
                            .resolveAndUpdateButtonActions(ctx, ActionConstants
                                    .getDefaults(ActionConstants.NAVBAR));
                    DUActionUtils.resolveAndUpdateButtonActions(ctx,
                            ActionConstants.getDefaults(ActionConstants.FLING));
                }
            });
            thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
        }
    }
}
