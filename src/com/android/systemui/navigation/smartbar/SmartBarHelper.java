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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;

import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.internal.utils.du.ImageHelper;
import com.android.systemui.navigation.OpaLayout;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.smartbar.SmartBarView;
import com.android.systemui.navigation.smartbar.SmartButtonView;
import com.android.systemui.R;

public class SmartBarHelper {

    public static int[] getViewPadding(View v) {
        int[] padding = new int[4];
        padding[0] = v.getPaddingStart();
        padding[1] = v.getPaddingTop();
        padding[2] = v.getPaddingEnd();
        padding[3] = v.getPaddingBottom();
        return padding;
    }

    public static void applyPaddingToView(View v, int[] padding) {
        v.setPaddingRelative(padding[0], padding[1],
                padding[2], padding[3]);
    }

    static boolean buttonNeedsCustomPadding(OpaLayout v) {
        boolean hasCustomIcon = v.getButton().getButtonConfig().hasCustomIcon();
        boolean hasNonSystemIcon = !v.getButton().getButtonConfig().isSystemAction();
        return hasCustomIcon || hasNonSystemIcon;
    }

    public static void updateButtonScalingAndPadding(OpaLayout v, boolean landscape) {
        // all non-system action icons need some extra scaling work
        v.getButton().setScaleType(ScaleType.CENTER_INSIDE);
    }

    public static BitmapDrawable resizeCustomButtonIcon(Drawable d, Context ctx, float iconSizeScale) {
        if (d == null) {
            d = DUActionUtils.getDrawableForAction(ctx, ActionHandler.SYSTEMUI_TASK_NO_ACTION);
        }
        // get custom button icon size
        final Bitmap bitmap = ImageHelper.drawableToBitmap(d);
        int originalHeight = bitmap.getHeight();
        int originalWeight = bitmap.getWidth();
        // apply the needed scaling to the icon size values to match default system buttons icons size
        final float scaledWidth = iconSizeScale * originalWeight;
        final float scaledHeight = iconSizeScale * originalHeight;
        // later we'll put the icon bitmap (resized to a smaller scaled rectf) into the bigger canvas,
        // so we need to center the rectf coordinates into the canvas (moving it a bit to right and to bottom)
        final float leftPadding = (originalWeight - scaledWidth) / 2;
        final float topPadding = (originalHeight - scaledHeight) / 2;
        final RectF targetRect = new RectF(leftPadding, topPadding, leftPadding + scaledWidth, topPadding + scaledHeight);
        // create a new empty canvas with the size of the original icon, then draw into it the icon bitmap scaled to our
        // smaller rectf size
        final Bitmap dest = Bitmap.createBitmap(originalWeight, originalHeight, android.graphics.Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(dest);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);
        canvas.drawBitmap(bitmap, null, targetRect, paint);

        return new BitmapDrawable(ctx.getResources(), dest);
    }

    static OpaLayout generatePrimaryKey(Context ctx, SmartBarView host, boolean landscape,
            ButtonConfig config) {
        OpaLayout opa = (OpaLayout)View.inflate(ctx, R.layout.opa_smartbutton, null);
        SmartButtonView v = opa.getButton();
        v.setHost(host);
        v.setButtonConfig(config);
        int width = ctx.getResources().getDimensionPixelSize(R.dimen.navigation_key_width);
        int height = ctx.getResources().getDimensionPixelSize(R.dimen.navigation_key_height);
        v.loadRipple();
        updateButtonScalingAndPadding(opa, landscape);
        host.setButtonDrawable(v);
        if (BaseNavigationBar.sIsTablet) {
            v.setLayoutParams(new FrameLayout.LayoutParams(width, LayoutParams.MATCH_PARENT));
            opa.setLayoutParams(new LinearLayout.LayoutParams(width, LayoutParams.MATCH_PARENT));
        } else {
            if (landscape) {
                v.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, height));
                opa.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height));
            } else {
                v.setLayoutParams(new FrameLayout.LayoutParams(width, LayoutParams.MATCH_PARENT));
                opa.setLayoutParams(new LinearLayout.LayoutParams(width, LayoutParams.MATCH_PARENT));
            }
        }
        return opa;
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