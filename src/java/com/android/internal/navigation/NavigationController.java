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

package com.android.internal.navigation;

import com.android.internal.navigation.pulse.PulseController;
import com.android.internal.navigation.smartbar.SmartBarView;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler.ActionIconMap;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.UserContentObserver;
import com.android.internal.utils.du.DUPackageMonitor.PackageChangedListener;
import com.android.internal.utils.du.DUPackageMonitor.PackageState;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

public class NavigationController implements PackageChangedListener {
    private static final String TAG = NavigationController.class.getSimpleName();

    public static final int NAVIGATION_MODE_SMARTBAR = 0;
    public static final int NAVIGATION_MODE_FLING = 1;
    private static final String SMARTBAR_LAYOUT = "smart_bar";
    private static final String FLING_LAYOUT = "fling_bar";

    private StatusbarImpl mBar;
    private NavbarObserver mNavbarObserver;
    private Handler mHandler = new Handler();
    private Runnable mAddNavbar;
    private Runnable mRemoveNavbar;
    private Context mContext;
    private PulseController mPulseController;
    private final ActionIconMap mIconMap;

    public NavigationController(Context context, Resources themedRes, StatusbarImpl statusBar,
            Runnable forceAddNavbar, Runnable removeNavbar) {
        mContext = context;
        mBar = statusBar;
        mIconMap = new ActionIconMap(themedRes);
        mAddNavbar = forceAddNavbar;
        mRemoveNavbar = removeNavbar;
        unlockVisualizer();
        mNavbarObserver = new NavbarObserver(mHandler);
        mNavbarObserver.observe();
        mPulseController = new PulseController(mContext, mHandler, ActionConstants.getDefaults(ActionConstants.FLING).getConfigs(mContext));
    }

    public Navigator getNavigationBarView(Context context) {
        int navMode = Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_MODE, NAVIGATION_MODE_SMARTBAR, UserHandle.USER_CURRENT);
        if (navMode == 2) {
            navMode = NAVIGATION_MODE_SMARTBAR;
        }
        String layout;
        switch (navMode) {
            case NAVIGATION_MODE_SMARTBAR:
                layout = SMARTBAR_LAYOUT;
                break;
            case NAVIGATION_MODE_FLING:
                layout = FLING_LAYOUT;
                break;
            default:
                layout = SMARTBAR_LAYOUT;
        }

        Navigator nav = null;

        if (TextUtils.equals(SMARTBAR_LAYOUT, layout)) {
            try {
                nav = new SmartBarView(context);
            } catch (Exception e) {
                e.printStackTrace();
                layout = FLING_LAYOUT;
            }
        }

        if (nav == null) {
            nav = (BaseNavigationBar) View.inflate(context,
                    DUActionUtils.getIdentifier(context, layout,
                            "layout", DUActionUtils.PACKAGE_SYSTEMUI), null);
        }

        nav.setIconMap(mIconMap);
        nav.setControllers(mPulseController);
        return nav;
    }

    public void updateNavbarOverlay(Resources res) {
        if (res == null) return;
        mIconMap.updateIcons(res);
        if (mBar.getNavigationBarView() != null) {
            mBar.getNavigationBarView().updateNavbarThemedResources(res);
        }
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
            } else if (uri.equals(Settings.Secure
                    .getUriFor(Settings.Secure.NAVIGATION_BAR_VISIBLE))) {
                boolean showing = Settings.Secure.getInt(resolver,
                        Settings.Secure.NAVIGATION_BAR_VISIBLE,
                        DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0) != 0;
                if (isBarShowingNow && !showing) {
                    mBar.getNavigationBarView().dispose();
                    mHandler.post(mRemoveNavbar);
                } else if (!isBarShowingNow && showing) {
                    mHandler.post(mAddNavbar);
                }
                updateKeyDisabler();
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
