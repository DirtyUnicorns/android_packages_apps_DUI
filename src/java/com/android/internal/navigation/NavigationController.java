
package com.android.internal.navigation;

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

import com.android.internal.utils.eos.ActionConstants;
import com.android.internal.utils.eos.EosActionUtils;
import com.android.internal.utils.eos.EosPackageMonitor.PackageChangedListener;
import com.android.internal.utils.eos.EosPackageMonitor.PackageState;
import com.android.internal.utils.eos.UserContentObserver;

public class NavigationController implements PackageChangedListener {
    private static final String TAG = NavigationController.class.getSimpleName();
    private static final boolean DEBUG = "1".equals(SystemProperties.get("ro.fling.debug", "0"));

    public static final int NAVIGATION_MODE_AOSP = 0;
    public static final int NAVIGATION_MODE_FLING = 1;
    private static final String NAVBAR_LAYOUT = "navigation_bar";
    private static final String FLING_LAYOUT = "fling_bar";

    private StatusbarImpl mBar;
    private NavbarObserver mNavbarObserver;
    private Handler mHandler = new Handler();
    private Runnable mAddNavbar;
    private Runnable mRemoveNavbar;

    // private final boolean mHasHardkeys;
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
        return inflateBar(mContext, layout);
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
                    EosActionUtils.getIdentifier(ctx, layout,
                            "layout", EosActionUtils.PACKAGE_SYSTEMUI), null);
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
                    Settings.Secure.DEV_FORCE_SHOW_NAVBAR), false, this, UserHandle.USER_ALL);
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
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.DEV_FORCE_SHOW_NAVBAR))
                    && EosActionUtils.isCapKeyDevice(mContext)) {
                updateDevForceNavbar(false);
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
            if (EosActionUtils.isCapKeyDevice(mContext)) {
                updateDevForceNavbar(true);
            }
        }

        private void updateDevForceNavbar(boolean userChange) {
            final ContentResolver resolver = mContext.getContentResolver();
            final boolean isBarShowingNow = mBar.getNavigationBarView() != null;
            // force navbar adds or removes the bar view
            boolean visible = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.DEV_FORCE_SHOW_NAVBAR, 0, UserHandle.USER_CURRENT) == 1;
            if (isBarShowingNow) {
                mBar.getNavigationBarView().dispose();
                mHandler.post(mRemoveNavbar);
            }
            if (visible) {
                mHandler.postDelayed(mAddNavbar, 500);
            }
            // only broadcast if updating from a user change
            // i.e. don't broadcast if user simply changed settings as
            // key disabler has already been invoked
            if (userChange) {
                // Send a broadcast to Settings to update Key disabling when user changes
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
                    if (EosActionUtils.isCapKeyDevice(ctx)) {
                        EosActionUtils.resolveAndUpdateButtonActions(ctx,
                                ActionConstants
                                        .getDefaults(ActionConstants.HWKEYS));
                    }
                    EosActionUtils
                            .resolveAndUpdateButtonActions(ctx, ActionConstants
                                    .getDefaults(ActionConstants.NAVBAR));
                    EosActionUtils.resolveAndUpdateButtonActions(ctx,
                            ActionConstants.getDefaults(ActionConstants.FLING));
                }
            });
            thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
        }
    }
}
