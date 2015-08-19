
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

import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.internal.navigation.Res;

public class SmartBarHelper {
    static final boolean sIsTablet = !DUActionUtils.isNormalScreen();

    static boolean buttonHasCustomIcon(SmartButtonView v) {
        return v.getButtonConfig().hasCustomIcon();
    }

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

    static SmartButtonView generatePrimaryKey(Context ctx, boolean landscape,
            SmartActionHandler handler, ButtonConfig config) {
        SmartButtonView v;
        if (handler == null) {
            v = new SmartButtonView(ctx);
        } else {
            v = new SmartButtonView(ctx, handler);
        }
        v.setButtonConfig(config);
        int width = DUActionUtils.getDimenPixelSize(ctx, Res.Softkey.KEY_WIDTH,
                DUActionUtils.PACKAGE_SYSTEMUI);
        int height = DUActionUtils.getDimenPixelSize(ctx, Res.Softkey.KEY_HEIGHT,
                DUActionUtils.PACKAGE_SYSTEMUI);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                landscape && !sIsTablet ? LayoutParams.MATCH_PARENT
                        : width, landscape && sIsTablet ? height : LayoutParams.MATCH_PARENT));
        v.loadRipple();

        // if iconUri equals a task name, and it is a key event task
        // adjust button scaling for wonky stock icons
        if (buttonHasCustomIcon(v)) {
            final int[] appIconPadding = getAppIconPadding(ctx);
            if (landscape & !sIsTablet) {
                v.setPaddingRelative(appIconPadding[1], appIconPadding[0],
                        appIconPadding[3], appIconPadding[2]);
            } else {
                v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
                        appIconPadding[2], appIconPadding[3]);
            }
        } else {
            v.setScaleType(ScaleType.CENTER);
        }
        v.setImageDrawable(config.getCurrentIcon(ctx));
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
        int origSize = landscape && !sIsTablet ? DUActionUtils.getDimenPixelSize(ctx,
                Res.Softkey.KEY_WIDTH,
                DUActionUtils.PACKAGE_SYSTEMUI) : DUActionUtils.getDimenPixelSize(ctx,
                Res.Softkey.KEY_HEIGHT,
                DUActionUtils.PACKAGE_SYSTEMUI);
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
