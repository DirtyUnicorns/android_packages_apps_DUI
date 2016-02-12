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

package com.android.internal.navigation.smartbar;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;

import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.internal.navigation.Res;

public class SmartBarHelper {
    static final boolean sIsTablet = !DUActionUtils.isNormalScreen();

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
        boolean hasNonSystemIcon = !v.getButtonConfig().getActionConfig(ActionConfig.PRIMARY)
                .getAction().startsWith(ActionHandler.SYSTEM_PREFIX);
        return hasCustomIcon || hasNonSystemIcon;
    }

    public static void updateButtonScalingAndPadding(SmartButtonView v, boolean landscape) {
        // all non-system action icons need some extra padding/scaling work
        final int[] appIconPadding = getAppIconPadding(v.getContext());
        if (buttonNeedsCustomPadding(v)) {
            if (landscape && !sIsTablet) {
                v.setPaddingRelative(appIconPadding[1], appIconPadding[0],
                        appIconPadding[3], appIconPadding[2]);
            } else {
                v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
                        appIconPadding[2], appIconPadding[3]);
            }
            v.setScaleType(ScaleType.CENTER_INSIDE);
        } else {
            if (landscape && sIsTablet) {
                v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
                        appIconPadding[2], appIconPadding[3]);
                v.setScaleType(ScaleType.CENTER_INSIDE);
            }
            v.setScaleType(sIsTablet ? ScaleType.CENTER_INSIDE : ScaleType.CENTER);
        }
    }

    static SmartButtonView generatePrimaryKey(Context ctx, boolean landscape, ButtonConfig config) {
        SmartButtonView v = new SmartButtonView(ctx);
        v.setButtonConfig(config);
        int width = DUActionUtils.getDimenPixelSize(ctx, Res.Softkey.KEY_WIDTH,
                DUActionUtils.PACKAGE_SYSTEMUI);
        int height = DUActionUtils.getDimenPixelSize(ctx, Res.Softkey.KEY_HEIGHT,
                DUActionUtils.PACKAGE_SYSTEMUI);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                landscape && !sIsTablet ? LayoutParams.MATCH_PARENT
                        : width, landscape && !sIsTablet ? height : LayoutParams.MATCH_PARENT));
        v.loadRipple();

        updateButtonScalingAndPadding(v, landscape);

        if (config.getTag().equals(ActionConstants.Smartbar.BUTTON1_TAG)) {  // back
            v.setImageDrawable(new SmartBackButtonDrawable(config.getCurrentIcon(ctx)));
        } else {
            v.setImageDrawable(config.getCurrentIcon(ctx));
        }
        return v;
    }

    static void addLightsOutButton(Context ctx, LinearLayout root, View v, boolean landscape,
            boolean empty) {
        ImageView addMe = new ImageView(ctx);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? DUActionUtils.getIdentifier(ctx,
                Res.Common.LIGHTS_OUT_LARGE, DUActionUtils.DRAWABLE,
                DUActionUtils.PACKAGE_SYSTEMUI)
                : DUActionUtils.getIdentifier(ctx, Res.Common.LIGHTS_OUT_SMALL,
                        DUActionUtils.DRAWABLE,
                        DUActionUtils.PACKAGE_SYSTEMUI));
        addMe.setScaleType(ImageView.ScaleType.CENTER);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        addViewToRoot(root, addMe, landscape);
    }

    static View makeSeparator(Context ctx) {
        View v;
        if (sIsTablet) {
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
        if (landscape && !sIsTablet) {
            root.addView(toAdd, 0);
        } else {
            root.addView(toAdd);
        }
    }

    static int getButtonSize(Context ctx, int numButtons, boolean landscape) {
        int origSize;        
        if (landscape && !sIsTablet) { // vertical needs height
            origSize = DUActionUtils.getDimenPixelSize(ctx,
                    Res.Softkey.KEY_HEIGHT,
                    DUActionUtils.PACKAGE_SYSTEMUI);
        } else { // horizontal needs width
            origSize = DUActionUtils.getDimenPixelSize(ctx,
                    Res.Softkey.KEY_WIDTH,
                    DUActionUtils.PACKAGE_SYSTEMUI);
        }        
        // don't squish tablet buttons
        if (numButtons == 3 || sIsTablet) {
            return origSize;
        } else {
            int size = Math.round((origSize * 3) / numButtons);
            return size;
        }
    }

    static void updateButtonSize(View v, int size, boolean landscape) {
        if (landscape && !sIsTablet) {
            v.getLayoutParams().height = size;
        } else {
            v.getLayoutParams().width = size;
        }
    }
}
