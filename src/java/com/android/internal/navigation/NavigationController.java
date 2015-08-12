package com.android.internal.navigation;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.IAudioService;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.android.internal.actions.ActionConstants;
import com.android.internal.actions.ActionUtils;

public class NavigationController {
    private static final String TAG = NavigationController.class.getSimpleName();
    private static final boolean DEBUG = true;

    public static final int NAVIGATION_MODE_AOSP = 0;
    public static final int NAVIGATION_MODE_FLING = 1;
    private static final String NAVBAR_LAYOUT = "navigation_bar";
    private static final String FLING_LAYOUT = "fling_bar";

    private StatusbarImpl mBar;
    private NavbarObserver mNavbarObserver;
    private Handler mHandler = new Handler();
    private Runnable mAddNavbar;
    private Runnable mRemoveNavbar;

    // monitor package changes and clear actions on features
    // that launched the package, if one was assigned
    // we monitor softkeys, hardkeys, and NX here
    private PackageReceiver mPackageReceiver;

    //private final boolean mHasHardkeys;
    private Context mContext;

    public NavigationController(Context context, StatusbarImpl statusBar,
            Runnable forceAddNavbar, Runnable removeNavbar) {
        mContext = context;
        mBar = statusBar;
        mAddNavbar = forceAddNavbar;
        mRemoveNavbar = removeNavbar;
        validateBarState();
        unlockVisualizer();
        mNavbarObserver = new NavbarObserver(mHandler);
        mNavbarObserver.observe();
        mPackageReceiver = new PackageReceiver();
        mPackageReceiver.registerBootReceiver(context);
    }

    public Navigator getNavigationBarView() {
        int navMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_MODE, NAVIGATION_MODE_AOSP, UserHandle.USER_CURRENT);
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
                    ActionUtils.getIdentifier(ctx, layout,
                            "layout", ActionUtils.PACKAGE_SYSTEMUI), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bar;
    }

    // resolve possible bizarre state in which
    // "force show navbar" became enabled on a
    // device without hardware navigation buttons
    private void validateBarState() {
        final Context ctx = mContext;
        final ContentResolver resolver = mContext.getContentResolver();
        boolean needsNavbar = !ActionUtils.isCapKeyDevice(ctx);
        if (needsNavbar) {
            boolean forceShown = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.DEV_FORCE_SHOW_NAVBAR, 0, UserHandle.USER_CURRENT) == 1;
            if (forceShown) {
                Settings.Secure.putInt(resolver,
                        Settings.Secure.DEV_FORCE_SHOW_NAVBAR, 0);
            }
        }
    }

    // for now, it makes sense to let PhoneStatusBar add/remove navbar view
    // from window manager. Define the add/remove runnables in PSB then pass
    // to us for handling
    class NavbarObserver extends ContentObserver {

        NavbarObserver(Handler handler) {
            super(handler);
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_MODE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.DEV_FORCE_SHOW_NAVBAR), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVBAR_LEFT_IN_LANDSCAPE), false, this, UserHandle.USER_ALL);
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
                    && ActionUtils.isCapKeyDevice(mContext)) {
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
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_MODE))) {
                if (isBarShowingNow) {
                    mBar.getNavigationBarView().dispose();
                    mHandler.post(mRemoveNavbar);
                }
                mHandler.postDelayed(mAddNavbar, 500);
            }
        }
    }

    /*
     * Initially register for boot completed, as PackageManager is likely not
     * online yet. Once boot is completed, reregister for package changes and
     * handle as needed
     */
    private class PackageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                handlePackageChanged();
                registerPackageReceiver(context);
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                    Intent.ACTION_PACKAGE_CHANGED.equals(action) ||
                    Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action) ||
                    Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                handlePackageChanged();
            }
        }

        void registerPackageReceiver(Context ctx) {
            Log.i(TAG, "Boot completed received, registering package receiver");
            ctx.unregisterReceiver(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            ctx.registerReceiver(this, filter);
        }

        void registerBootReceiver(Context ctx) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BOOT_COMPLETED);
            ctx.registerReceiver(this, filter);
        }
    }

    private void handlePackageChanged() {
        final Context ctx = mContext;
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (ActionUtils.isCapKeyDevice(ctx)) {
                    ActionUtils.resolveAndUpdateButtonActions(ctx, ActionConstants.getDefaults(ActionConstants.HWKEYS));
                }
                ActionUtils.resolveAndUpdateButtonActions(ctx, ActionConstants.getDefaults(ActionConstants.NAVBAR));
                ActionUtils.resolveAndUpdateButtonActions(ctx, ActionConstants.getDefaults(ActionConstants.FLING));
            }
        });
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
    }
}
