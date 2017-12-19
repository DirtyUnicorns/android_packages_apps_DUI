/*
 * Copyright (C) 2013 The Android Open Source Project
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
 */

package com.android.systemui.navigation.fling;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.systemui.navigation.*;
import com.android.systemui.navigation.fling.FlingView;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.R;

public final class FlingBarTransitions extends BarTransitions {

    private final FlingView mView;
    private boolean mLightsOut;
    private final LightBarTransitionsController mLightTransitionsController;

    public FlingBarTransitions(FlingView view) {
        super(view, R.drawable.nav_background);
//                R.color.navigation_bar_background_opaque,
//                R.color.navigation_bar_background_semi_transparent,
//                R.color.navigation_bar_background_transparent,
//                com.android.internal.R.color.battery_saver_mode_color);
        mView = view;
        mLightTransitionsController = new LightBarTransitionsController(view.getContext(),
                this::applyDarkIntensity);
    }

    public void init() {
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyMode(getMode(), false /*animate*/, true /*force*/);
    }

    public LightBarTransitionsController getLightTransitionsController() {
        return mLightTransitionsController;
    }

    public void reapplyDarkIntensity() {
        applyDarkIntensity(mLightTransitionsController.getCurrentDarkIntensity());
    }

    public void applyDarkIntensity(float darkIntensity) {
        Drawable current = mView.getLogoDrawable(false);
        if (current != null && current instanceof DarkIntensity) {
            ((DarkIntensity) current).setDarkIntensity(darkIntensity);
        }
        Drawable hidden = mView.getLogoDrawable(true);
        if (hidden != null && hidden instanceof DarkIntensity) {
            ((DarkIntensity) hidden).setDarkIntensity(darkIntensity);
        }
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyMode(newMode, animate, false /*force*/);
    }

    private void applyMode(int mode, boolean animate, boolean force) {
        // apply to lights out
        applyLightsOut(isLightsOut(mode), animate, force);
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut)
            return;

        mLightsOut = lightsOut;
        final View navButtons = mView.getCurrentView().findViewById(R.id.nav_buttons);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();

        final float navButtonsAlpha = lightsOut ? 0.5f : 1f;

        if (!animate) {
            navButtons.setAlpha(navButtonsAlpha);
        } else {
            final int duration = lightsOut ? LIGHTS_OUT_DURATION : LIGHTS_IN_DURATION;
            navButtons.animate()
                    .alpha(navButtonsAlpha)
                    .setDuration(duration)
                    .start();
        }
    }
}
