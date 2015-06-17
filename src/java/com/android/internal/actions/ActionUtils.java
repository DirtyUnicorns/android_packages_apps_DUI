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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import java.net.URISyntaxException;
import java.util.ArrayList;

import com.android.internal.actions.ActionConstants.Defaults;
import com.android.internal.actions.Config.ActionConfig;
import com.android.internal.actions.Config.ButtonConfig;

public final class ActionUtils {
    public static final String ANDROIDNS = "http://schemas.android.com/apk/res/android";
    public static final String PACKAGE_SYSTEMUI = "com.android.systemui";

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
        return !context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
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
                    d = getDrawableFromResources(context, ActionHandler.systemActions[i].mIconName,
                            ActionHandler.systemActions[i].mIconPackage);
                }
            }
        } else {
            d = getDrawableFromComponent(context.getPackageManager(), action);
        }
        return d;
    }

    public static Drawable getDrawableFromResources(Context context, String drawableName, String pkg) {
        try {
            Resources res = context.getPackageManager()
                    .getResourcesForApplication(pkg);
            Drawable icon = res.getDrawable(res.getIdentifier(drawableName, "drawable",
                    pkg));
            return icon;
        } catch (Exception e) {
            return context.getResources().getDrawable(
                    com.android.internal.R.drawable.sym_def_app_icon);
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

    public static int getIntFromResources(Context context, String intName, String pkg) {
        try {
            Resources res = context.getPackageManager()
                    .getResourcesForApplication(pkg);
            int val = res.getInteger(res.getIdentifier(intName, "integer",
                    pkg));
            return val;
        } catch (Exception e) {
            return -1; // good luck
        }
    }

    public static int getcolorFromResources(Context context, String colorName, String pkg) {
        try {
            Resources res = context.getPackageManager()
                    .getResourcesForApplication(pkg);
            int color = res.getColor(res.getIdentifier(colorName, "color",
                    pkg));
            return color;
        } catch (Exception e) {
            return Color.WHITE; // sorry. you fail. you are white now
        }
    }

    public static int getIdentifierByName(Context ctx, String name, String pkg) {
        try {
            Resources res = ctx.getPackageManager()
                    .getResourcesForApplication(pkg);
            int id = res.getIdentifier(name, "id", pkg);
            return id;
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getDimensByName(Context ctx, String name, String pkg) {
        try {
            Resources res = ctx.getPackageManager()
                    .getResourcesForApplication(pkg);
            int id = res.getIdentifier(name, "dimen", pkg);
            return id;
        } catch (Exception e) {
            return 0;
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
}
