/*
 * Copyright (C) 2015 The TeamEos Project
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
 * Launches actions assigned to widgets. Creates bundles of state based
 * on the type of action passed.
 *
 */

package com.android.internal.actions;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.app.SearchManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.ITorchService;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerPolicyControl;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.actions.Config.ActionConfig;
import com.android.internal.util.cm.QSUtils;

public class ActionHandler {
    public static String TAG = ActionHandler.class.getSimpleName();

    public static final String SYSTEM_PREFIX = "task";
    public static final String SYSTEMUI = "com.android.systemui";

    public static final String SYSTEMUI_TASK_NO_ACTION = "task_no_action";
    public static final String SYSTEMUI_TASK_SETTINGS_PANEL = "task_settings_panel";
    public static final String SYSTEMUI_TASK_NOTIFICATION_PANEL = "task_notification_panel";
    public static final String SYSTEMUI_TASK_SCREENSHOT = "task_screenshot";
    public static final String SYSTEMUI_TASK_SCREENRECORD = "task_screenrecord";
    // public static final String SYSTEMUI_TASK_AUDIORECORD =
    // "task_audiorecord";
    public static final String SYSTEMUI_TASK_EXPANDED_DESKTOP = "task_expanded_desktop";
    public static final String SYSTEMUI_TASK_SCREENOFF = "task_screenoff";
    public static final String SYSTEMUI_TASK_KILL_PROCESS = "task_killcurrent";
    public static final String SYSTEMUI_TASK_ASSIST = "task_assist";
    public static final String SYSTEMUI_TASK_POWER_MENU = "task_powermenu";
    public static final String SYSTEMUI_TASK_TORCH = "task_torch";
    public static final String SYSTEMUI_TASK_CAMERA = "task_camera";
    public static final String SYSTEMUI_TASK_BT = "task_bt";
    public static final String SYSTEMUI_TASK_WIFI = "task_wifi";
    public static final String SYSTEMUI_TASK_WIFIAP = "task_wifiap";
    public static final String SYSTEMUI_TASK_RECENTS = "task_recents";
    public static final String SYSTEMUI_TASK_LAST_APP = "task_last_app";
    public static final String SYSTEMUI_TASK_VOICE_SEARCH = "task_voice_search";
    public static final String SYSTEMUI_TASK_APP_SEARCH = "task_app_search";
    public static final String SYSTEMUI_TASK_SILENT = "task_silent";
    public static final String SYSTEMUI_TASK_VIBRATOR = "task_vibrator";
    public static final String SYSTEMUI_TASK_VIB_SILENT = "task_vib_silent";
    public static final String SYSTEMUI_TASK_MENU = "task_menu";
    public static final String SYSTEMUI_TASK_BACK = "task_back";
    public static final String SYSTEMUI_TASK_HOME = "task_home";

    public static final String INTENT_SHOW_POWER_MENU = "action_handler_show_power_menu";
    public static final String INTENT_TOGGLE_SCREENRECORD = "action_handler_toggle_screenrecord";
    public static final String INTENT_SCREENSHOT = "action_handler_screenshot";

    static enum SystemAction {
        NoAction(SYSTEMUI_TASK_NO_ACTION, "No action", SYSTEMUI, "ic_sysbar_null"),
        SettingsPanel(SYSTEMUI_TASK_SETTINGS_PANEL, "Settings panel", SYSTEMUI, "ic_sysbar_qs"),
        NotificationPanel(SYSTEMUI_TASK_NOTIFICATION_PANEL, "Notification panel", SYSTEMUI, "ic_sysbar_notifications"),
        Screenshot(SYSTEMUI_TASK_SCREENSHOT, "Screenshot", SYSTEMUI, "ic_sysbar_screenshot"),
        Screenrecord(SYSTEMUI_TASK_SCREENRECORD, "Record screen", SYSTEMUI, "ic_sysbar_record_screen"),
        ExpandedDesktop(SYSTEMUI_TASK_EXPANDED_DESKTOP, "Expanded desktop", SYSTEMUI, "ic_sysbar_expanded_desktop"),
        ScreenOff(SYSTEMUI_TASK_SCREENOFF, "Turn off screen", SYSTEMUI, "ic_sysbar_power"),
        KillApp(SYSTEMUI_TASK_KILL_PROCESS, "Force close app", SYSTEMUI, "ic_sysbar_killtask"),
        Assistant(SYSTEMUI_TASK_ASSIST, "Search assistant", SYSTEMUI, "ic_sysbar_assist"),
        VoiceSearch(SYSTEMUI_TASK_VOICE_SEARCH, "Voice search", SYSTEMUI, "ic_sysbar_search"),
        Flashlight(SYSTEMUI_TASK_TORCH, "Flashlight", SYSTEMUI, "ic_sysbar_torch"),
        Bluetooth(SYSTEMUI_TASK_BT, "Bluetooth", SYSTEMUI, "ic_sysbar_bt"),
        WiFi(SYSTEMUI_TASK_WIFI, "WiFi", SYSTEMUI, "ic_sysbar_wifi"),
        Hotspot(SYSTEMUI_TASK_WIFIAP, "Hotspot", SYSTEMUI, "ic_qs_wifi_ap_on"),
        LastApp(SYSTEMUI_TASK_LAST_APP, "Last app", SYSTEMUI, "ic_sysbar_lastapp"),
        Overview(SYSTEMUI_TASK_RECENTS, "Overview", SYSTEMUI, "ic_sysbar_recent"),
        PowerMenu(SYSTEMUI_TASK_POWER_MENU, "Power menu", SYSTEMUI, "ic_sysbar_power_menu"),
        Silent(SYSTEMUI_TASK_SILENT, "Normal/Silent", SYSTEMUI, "ic_sysbar_silent"),
        Vibrator(SYSTEMUI_TASK_VIBRATOR, "Normal/Vibrate", "SYSTEMUI", "ic_sysbar_vib"),
        SilentVib(SYSTEMUI_TASK_VIB_SILENT, "Normal/Vibrate/Silent", SYSTEMUI, "ic_sysbar_ring_vib_silent"),
        Menu(SYSTEMUI_TASK_MENU, "Menu", SYSTEMUI, "ic_sysbar_menu"),
        Back(SYSTEMUI_TASK_BACK, "Back", SYSTEMUI, "ic_sysbar_back"),
        Home(SYSTEMUI_TASK_HOME, "Home", SYSTEMUI, "ic_sysbar_home");

        String mAction;
        String mLabel;
        String mIconPackage;
        String mIconName;

        private SystemAction(String action, String label, String iconPackage, String iconName) {
            mAction = action;
            mLabel = label;
            mIconPackage = iconPackage;
            mIconName = iconName;
        }

        private ActionConfig create(Context ctx) {
            ActionConfig a = new ActionConfig(ctx, mAction, mAction);
            return a;
        }
    }

    static SystemAction[] systemActions = new SystemAction[] {
            SystemAction.NoAction, SystemAction.SettingsPanel,
            SystemAction.NotificationPanel, SystemAction.Screenshot,
            SystemAction.ScreenOff, SystemAction.KillApp,
            SystemAction.Assistant, SystemAction.Flashlight,
            SystemAction.Bluetooth, SystemAction.WiFi,
            SystemAction.Hotspot, SystemAction.LastApp,
            SystemAction.PowerMenu, SystemAction.Overview,
            SystemAction.Menu, SystemAction.Back,
            SystemAction.VoiceSearch, SystemAction.Home,
            SystemAction.Silent, SystemAction.Vibrator,
            SystemAction.SilentVib, SystemAction.ExpandedDesktop,
            SystemAction.Screenrecord
    };

    public static ArrayList<ActionConfig> getSystemActions(Context context) {
        ArrayList<ActionConfig> bundle = new ArrayList<ActionConfig>();
        for (int i = 0; i < systemActions.length; i++) {
            ActionConfig c = systemActions[i].create(context);
            String action = c.getAction();
            if (TextUtils.equals(action, SYSTEMUI_TASK_WIFIAP)
                    && !QSUtils.deviceSupportsMobileData(context)) {
                continue;
            } else if (TextUtils.equals(action, SYSTEMUI_TASK_BT)
                    && !QSUtils.deviceSupportsBluetooth()) {
                continue;
            } else if (TextUtils.equals(action, SYSTEMUI_TASK_TORCH)
                    && !QSUtils.deviceSupportsFlashLight(context)) {
                continue;
            } else if ((TextUtils.equals(action, SYSTEMUI_TASK_VIBRATOR))
                    || (TextUtils.equals(action, SYSTEMUI_TASK_VIB_SILENT))) {
                Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib == null || !vib.hasVibrator()) {
                    continue;
                }
            } else if (TextUtils.equals(action, SYSTEMUI_TASK_CAMERA)
                    && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                continue;
            } else if (TextUtils.equals(action, SYSTEMUI_TASK_SCREENRECORD)) {
                if (!ActionUtils.getBoolFromResources(context, "config_enableScreenrecordChord", ActionUtils.PACKAGE_ANDROID)) {
                    continue;
                }
            }
            bundle.add(c);
        }
        Collections.sort(bundle);
        return bundle;
    }

    private static final class StatusBarHelper {
        private static boolean isPreloaded = false;
        private static final Object mLock = new Object();
        private static IStatusBarService mService = null;

        private static IStatusBarService getStatusBarService() {
            synchronized (mLock) {
                if (mService == null) {
                    try {
                        mService = IStatusBarService.Stub.asInterface(
                                ServiceManager.getService("statusbar"));
                    } catch (Exception e) {
                    }
                }
                return mService;
            }
        }

        private static void toggleRecentsApps() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    sendCloseSystemWindows("recentapps");
                    service.toggleRecentApps();
                } catch (RemoteException e) {
                    return;
                }
                isPreloaded = false;
            }
        }

        private static void cancelPreloadRecentApps() {
            if (isPreloaded == false)
                return;
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.cancelPreloadRecentApps();
                } catch (Exception e) {
                    return;
                }
            }
            isPreloaded = false;
        }

        private static void preloadRecentApps() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.preloadRecentApps();
                } catch (RemoteException e) {
                    isPreloaded = false;
                    return;
                }
                isPreloaded = true;
            }
        }

        private static void expandNotificationPanel() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.expandNotificationsPanel();
                } catch (RemoteException e) {
                }
            }
        }

        private static void expandSettingsPanel() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.expandSettingsPanel();
                } catch (RemoteException e) {
                }
            }
        }
    }

    public static void toggleRecentApps() {
        StatusBarHelper.toggleRecentsApps();
    }

    public static void cancelPreloadRecentApps() {
        StatusBarHelper.cancelPreloadRecentApps();
    }

    public static void preloadRecentApps() {
        StatusBarHelper.preloadRecentApps();
    }

    public static void performTask(Context context, String action) {
        // null: throw it out
        if (action == null) {
            return;
        }
        ActionUtils.checkSoftKeyDevice();
        // not a system action, should be intent
        if (!action.startsWith(SYSTEM_PREFIX)) {
            Intent intent = ActionUtils.getIntent(action);
            if (intent == null) {
                return;
            }
            launchActivity(context, intent);
            return;
        } else if (action.equals(SYSTEMUI_TASK_NO_ACTION)) {
            return;
        } else if (action.equals(SYSTEMUI_TASK_KILL_PROCESS)) {
            killProcess(context);
        } else if (action.equals(SYSTEMUI_TASK_SCREENSHOT)) {
            takeScreenshot(context);
        } else if (action.equals(SYSTEMUI_TASK_SCREENRECORD)) {
            takeScreenrecord(context);
            // } else if (action.equals(SYSTEMUI_TASK_AUDIORECORD)) {
            // takeAudiorecord();
        } else if (action.equals(SYSTEMUI_TASK_EXPANDED_DESKTOP)) {
            toggleExpandedDesktop(context);
        } else if (action.equals(SYSTEMUI_TASK_SCREENOFF)) {
            screenOff(context);
        } else if (action.equals(SYSTEMUI_TASK_ASSIST)) {
            launchAssistAction(context);
        } else if (action.equals(SYSTEMUI_TASK_POWER_MENU)) {
            showPowerMenu(context);
        } else if (action.equals(SYSTEMUI_TASK_TORCH)) {
            toggleTorch();
        } else if (action.equals(SYSTEMUI_TASK_CAMERA)) {
            launchCamera(context);
        } else if (action.equals(SYSTEMUI_TASK_WIFI)) {
            toggleWifi(context);
        } else if (action.equals(SYSTEMUI_TASK_WIFIAP)) {
            toggleWifiAP(context);
        } else if (action.equals(SYSTEMUI_TASK_BT)) {
            toggleBluetooth();
        } else if (action.equals(SYSTEMUI_TASK_RECENTS)) {
            toggleRecentApps();
        } else if (action.equals(SYSTEMUI_TASK_LAST_APP)) {
            switchToLastApp(context);
        } else if (action.equals(SYSTEMUI_TASK_SETTINGS_PANEL)) {
            StatusBarHelper.expandSettingsPanel();
        } else if (action.equals(SYSTEMUI_TASK_NOTIFICATION_PANEL)) {
            StatusBarHelper.expandNotificationPanel();
        } else if (action.equals(SYSTEMUI_TASK_VOICE_SEARCH)) {
            launchVoiceSearch(context);
        } else if (action.equals(SYSTEMUI_TASK_APP_SEARCH)) {
            triggerVirtualKeypress(context, KeyEvent.KEYCODE_SEARCH);
        } else if (action.equals(SYSTEMUI_TASK_SILENT)) {
            toggleSilent(context);
        } else if (action.equals(SYSTEMUI_TASK_VIBRATOR)) {
            toggleVib(context);
        } else if (action.equals(SYSTEMUI_TASK_VIB_SILENT)) {
            toggleVibSilent(context);
        } else if (action.equals(SYSTEMUI_TASK_MENU)) {
            triggerVirtualKeypress(context, KeyEvent.KEYCODE_MENU);
        } else if (action.equals(SYSTEMUI_TASK_BACK)) {
            triggerVirtualKeypress(context, KeyEvent.KEYCODE_BACK);
        } else if (action.equals(SYSTEMUI_TASK_HOME)) {
            triggerVirtualKeypress(context, KeyEvent.KEYCODE_HOME);
        }
    }

    public static boolean isActionKeyEvent(String action) {
        if (action.equals(SYSTEMUI_TASK_HOME)
                || action.equals(SYSTEMUI_TASK_BACK)
//                || action.equals(SYSTEMUI_TASK_SEARCH)
                || action.equals(SYSTEMUI_TASK_MENU)
//                || action.equals(ActionConstants.ACTION_MENU_BIG)
                || action.equals(SYSTEMUI_TASK_NO_ACTION)) {
            return true;
        }
        return false;
    }

    private static void launchActivity(Context context, Intent intent) {
        try {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        } catch (Exception e) {
            Log.i(TAG, "Unable to launch activity " + e);
        }
    }

    private static void switchToLastApp(Context context) {
        final ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo lastTask = getLastTask(context, am);

        if (lastTask != null) {
            final ActivityOptions opts = ActivityOptions.makeCustomAnimation(context,
                    ActionUtils.getIdentifier(context, "last_app_in", "anim", ActionUtils.PACKAGE_ANDROID),
                    ActionUtils.getIdentifier(context, "last_app_out", "anim", ActionUtils.PACKAGE_ANDROID));
            am.moveTaskToFront(lastTask.id, ActivityManager.MOVE_TASK_NO_USER_ACTION,
                    opts.toBundle());
        }
    }

    private static ActivityManager.RunningTaskInfo getLastTask(Context context,
            final ActivityManager am) {
        final String defaultHomePackage = resolveCurrentLauncherPackage(context);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);

        for (int i = 1; i < tasks.size(); i++) {
            String packageName = tasks.get(i).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage)
                    && !packageName.equals(context.getPackageName())
                    && !packageName.equals(SYSTEMUI)) {
                return tasks.get(i);
            }
        }
        return null;
    }

    private static String resolveCurrentLauncherPackage(Context context) {
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final PackageManager pm = context.getPackageManager();
        final ResolveInfo launcherInfo = pm.resolveActivity(launcherIntent, 0);
        return launcherInfo.activityInfo.packageName;
    }

    private static void sendCloseSystemWindows(String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    private static void toggleSilent(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && ActivityManagerNative.isSystemReady()) {
            if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                ToneGenerator tg = new ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION,
                        (int) (ToneGenerator.MAX_VOLUME * 0.85));
                if (tg != null) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                }
            }
        }
    }

    private static void toggleVib(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && ActivityManagerNative.isSystemReady()) {
            if (am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null) {
                    vib.vibrate(50);
                }
            } else {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                ToneGenerator tg = new ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION,
                        (int) (ToneGenerator.MAX_VOLUME * 0.85));
                if (tg != null) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                }
            }
        }
    }

    private static void toggleVibSilent(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && ActivityManagerNative.isSystemReady()) {
            if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null) {
                    vib.vibrate(50);
                }
            } else if (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                ToneGenerator tg = new ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION,
                        (int) (ToneGenerator.MAX_VOLUME * 0.85));
                if (tg != null) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                }
            }
        }
    }

    private static void toggleExpandedDesktop(Context context) {
        ContentResolver cr = context.getContentResolver();
        String newVal = "";
        String currentVal = Settings.Global.getString(cr, Settings.Global.POLICY_CONTROL);
        if (currentVal == null) {
            currentVal = newVal;
        }
        if ("".equals(currentVal)) {
            newVal = "immersive.full=*";
        }
        Settings.Global.putString(cr, Settings.Global.POLICY_CONTROL, newVal);
        if (newVal.equals("")) {
            WindowManagerPolicyControl.reloadFromSetting(context);
        }
    }

    private static void launchVoiceSearch(Context context) {
        sendCloseSystemWindows("assist");
        // launch the search activity
        Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
        try {
            // TODO: This only stops the factory-installed search manager.
            // Need to formalize an API to handle others
            SearchManager searchManager = (SearchManager) context
                    .getSystemService(Context.SEARCH_SERVICE);
            if (searchManager != null) {
                searchManager.stopSearch();
            }
            launchActivity(context, intent);
        } catch (ActivityNotFoundException e) {
            Slog.w(TAG, "No assist activity installed", e);
        }
    }

    private static void triggerVirtualKeypress(Context context, final int keyCode) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        final KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);

        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private static void launchCamera(Context context) {
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        PackageManager pm = context.getPackageManager();
        final ResolveInfo mInfo = pm.resolveActivity(i, 0);
        Intent intent = new Intent().setComponent(new ComponentName(mInfo.activityInfo.packageName,
                mInfo.activityInfo.name));
        launchActivity(context, intent);
    }

    private static void toggleWifi(Context context) {
        WifiManager wfm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wfm.setWifiEnabled(!wfm.isWifiEnabled());
    }

    private static void toggleBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean enabled = bluetoothAdapter.isEnabled();
        if (enabled) {
            bluetoothAdapter.disable();
        } else {
            bluetoothAdapter.enable();
        }
    }

    private static void toggleWifiAP(Context context) {
        final ContentResolver cr = context.getContentResolver();
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int state = wm.getWifiApState();
        boolean enabled = false;
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
            case WifiManager.WIFI_AP_STATE_ENABLED:
                enabled = false;
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
            case WifiManager.WIFI_AP_STATE_DISABLED:
                enabled = true;
                break;
        }
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = wm.getWifiState();
        if (enabled && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            wm.setWifiEnabled(false);
            Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }

        // Turn on the Wifi AP
        wm.setWifiApEnabled(null, enabled);

        /**
         * If needed, restore Wifi on tether disable
         */
        if (!enabled) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                // Do nothing here
            }
            if (wifiSavedState == 1) {
                wm.setWifiEnabled(true);
                Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }
    }

    private static void toggleTorch() {
        try {
            ITorchService torchService = ITorchService.Stub.asInterface(ServiceManager
                    .getService(Context.TORCH_SERVICE));
            torchService.toggleTorch();
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown acquiring torch service" + e.toString());
        }
    }

    private static void takeScreenshot(Context context) {
        context.sendBroadcastAsUser(new Intent(INTENT_SCREENSHOT), new UserHandle(
                UserHandle.USER_ALL));
    }

    private static void takeScreenrecord(Context context) {
        context.sendBroadcastAsUser(new Intent(INTENT_TOGGLE_SCREENRECORD), new UserHandle(
                UserHandle.USER_ALL));
    }

    private static void killProcess(Context context) {
        if (context.checkCallingOrSelfPermission(android.Manifest.permission.FORCE_STOP_PACKAGES) == PackageManager.PERMISSION_GRANTED
                && !isLockTaskOn()) {
            try {
                final Intent intent = new Intent(Intent.ACTION_MAIN);
                String defaultHomePackage = "com.android.launcher";
                intent.addCategory(Intent.CATEGORY_HOME);
                final ResolveInfo res = context.getPackageManager()
                        .resolveActivity(intent, 0);
                if (res.activityInfo != null
                        && !res.activityInfo.packageName.equals("android")) {
                    defaultHomePackage = res.activityInfo.packageName;
                }
                IActivityManager am = ActivityManagerNative.getDefault();
                List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
                for (RunningAppProcessInfo appInfo : apps) {
                    int uid = appInfo.uid;
                    // Make sure it's a foreground user application (not system,
                    // root, phone, etc.)
                    if (uid >= Process.FIRST_APPLICATION_UID
                            && uid <= Process.LAST_APPLICATION_UID
                            && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        if (appInfo.pkgList != null
                                && (appInfo.pkgList.length > 0)) {
                            for (String pkg : appInfo.pkgList) {
                                if (!pkg.equals("com.android.systemui")
                                        && !pkg.equals(defaultHomePackage)) {
                                    am.forceStopPackage(pkg,
                                            UserHandle.USER_CURRENT);
                                    break;
                                }
                            }
                        } else {
                            Process.killProcess(appInfo.pid);
                            break;
                        }
                    }
                }
            } catch (RemoteException remoteException) {
                Log.d("ActionHandler", "Caller cannot kill processes, aborting");
            }
        } else {
            Log.d("ActionHandler", "Caller cannot kill processes, aborting");
        }
    }

    private static void screenOff(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }

    private static void launchAssistAction(Context context) {
        sendCloseSystemWindows("assist");
        Intent intent = ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(context, true, UserHandle.USER_CURRENT);
        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                context.startActivityAsUser(intent, UserHandle.CURRENT);
            } catch (ActivityNotFoundException e) {
                Slog.w(TAG, "No activity to handle assist action.", e);
            }
        }
    }

    public static void turnOffLockTask() {
        try {
            ActivityManagerNative.getDefault().stopLockTaskModeOnCurrent();
        } catch (Exception e) {
        }
    }

    public static boolean isLockTaskOn() {
        try {
            return ActivityManagerNative.getDefault().isInLockTaskMode();
        } catch (Exception e) {
        }
        return false;
    }

    private static void showPowerMenu(Context context) {
        context.sendBroadcastAsUser(new Intent(INTENT_SHOW_POWER_MENU), new UserHandle(
                UserHandle.USER_ALL));
    }
}
