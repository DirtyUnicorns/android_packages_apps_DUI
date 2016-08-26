/**
 * Copyright (C) 2014 SlimRoms
 * Copyright (C) 2016 The DirtyUnicorns Project
 * 
 * @author: Randall Rushing <randall.rushing@gmail.com>
 *
 * Much love and respect to SlimRoms for some of these layout/padding
 * related methods and static factory methods
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
 */

package com.android.systemui.navigation.smartbar;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;

import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.smartbar.SmartBarView;
import com.android.systemui.navigation.smartbar.SmartButtonView;
import com.android.systemui.R;

public class SmartBarHelper {

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

    public static int[] getViewPadding(ImageView v) {
        int[] padding = new int[4];
        padding[0] = v.getPaddingStart();
        padding[1] = v.getPaddingTop();
        padding[2] = v.getPaddingEnd();
        padding[3] = v.getPaddingBottom();
        return padding;
    }

    public static void applyPaddingToView(ImageView v, int[] padding) {
        v.setPaddingRelative(padding[0], padding[1],
                padding[2], padding[3]);
    }

    static boolean buttonNeedsCustomPadding(SmartButtonView v) {
        boolean hasCustomIcon = v.getButtonConfig().hasCustomIcon();
        boolean hasNonSystemIcon = !v.getButtonConfig().isSystemAction();
        return hasCustomIcon || hasNonSystemIcon;
    }

    public static void updateButtonScalingAndPadding(SmartButtonView v, boolean landscape) {
        // all non-system action icons need some extra padding/scaling work
        final int[] appIconPadding = getAppIconPadding(v.getContext());
        if (buttonNeedsCustomPadding(v)) {
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

    static SmartButtonView generatePrimaryKey(Context ctx, SmartBarView host, boolean landscape,
            ButtonConfig config) {
        SmartButtonView v = new SmartButtonView(ctx, host);
        v.setButtonConfig(config);
        int width = ctx.getResources().getDimensionPixelSize(R.dimen.navigation_key_width);
        int height = ctx.getResources().getDimensionPixelSize(R.dimen.navigation_key_height);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                landscape && !BaseNavigationBar.sIsTablet ? LayoutParams.MATCH_PARENT
                        : width, landscape && !BaseNavigationBar.sIsTablet ? height : LayoutParams.MATCH_PARENT));
        v.loadRipple();
        updateButtonScalingAndPadding(v, landscape);
        host.setButtonDrawable(v);
        return v;
    }

    static View makeSeparator(Context ctx) {
        View v;
        if (BaseNavigationBar.sIsTablet) {
            v = new Space(ctx);
        } else {
            v = new View(ctx);
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f));
        v.setVisibility(View.INVISIBLE);
        return v;
    }

    static void addViewToRoot(ViewGroup root, View toAdd, boolean landscape) {
        if (landscape && !BaseNavigationBar.sIsTablet) {
            root.addView(toAdd, 0);
        } else {
            root.addView(toAdd);
        }
    }

    static int getButtonSize(Context ctx, int numButtons, boolean landscape) {
        if (BaseNavigationBar.sIsTablet) {
            return getTabletButtonSize(ctx, numButtons, landscape);
        } else {
            return getPhoneButtonSize(ctx, numButtons, landscape);
        }
    }

    static int getTabletButtonSize(Context ctx, int numButtons, boolean landscape) {
        int origSize = ctx.getResources().getDimensionPixelSize(
                landscape ? R.dimen.navigation_key_tablet_width_land
                        : R.dimen.navigation_key_tablet_width_port);
        if (numButtons < 4) {
            return origSize;
        } else {
            // the more buttons we have the less we shave per button
            float factor = 1f - ((numButtons - 3) * 0.06f);
            int size = Math.round(origSize * factor);
            return size;
        }
    }

    static int getPhoneButtonSize(Context ctx, int numButtons, boolean landscape) {
        // in this case, landscape refers to the vertical bar layout
        int origSize = ctx.getResources().getDimensionPixelSize(
                landscape ? R.dimen.navigation_key_height : R.dimen.navigation_key_width);
        if (numButtons < 4) {
            return origSize;
        } else {
            // create an even distribution
            int size = Math.round((origSize * 3) / numButtons);
            return size;
        }
    }

    static void updateButtonSize(View v, int size, boolean landscape) {
        if (BaseNavigationBar.sIsTablet) {
            updateTabletButtonSize(v, size, landscape);
        } else {
            updatePhoneButtonSize(v, size, landscape);
        }
    }

    static void updatePhoneButtonSize(View v, int size, boolean landscape) {
        if (landscape) {
            v.getLayoutParams().height = size;
        } else {
            v.getLayoutParams().width = size;
        }
    }

    static void updateTabletButtonSize(View v, int size, boolean landscape) {
        v.getLayoutParams().width = size;
    }
}