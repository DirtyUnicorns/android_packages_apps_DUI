/**
 * Copyright (C) 2014 The TeamEos Project
 * Copyright (C) 2017 The DirtyUnicorns Project
 * 
 * @author: Randall Rushing <randall.rushing@gmail.com>
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
 *
 */

package com.android.systemui.navigation;

import com.android.internal.utils.du.ActionHandler.ActionIconResources;
import com.android.systemui.R;
import com.android.systemui.navigation.NavbarOverlayResources;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

    public class NavbarOverlayResources extends ActionIconResources {

//      public int mOpaque;
//      public int mSemiTransparent;
//      public int mTransparent;
//      public int mWarning;
        public Drawable mGradient;
        public Drawable mFlingLogo;
        public Drawable mFlingLogoDark;
        public Drawable mLightsOutLarge;

    public NavbarOverlayResources(Context ctx, Resources res) {
        super(res);
//      mOpaque = res.getColor(R.color.navigation_bar_background_opaque);
//      mSemiTransparent = res.getColor(R.color.navigation_bar_background_semi_transparent);
//      mTransparent = res.getColor(R.color.navigation_bar_background_transparent);
//      mWarning = res.getColor(com.android.internal.R.color.battery_saver_mode_color);
        mGradient = res.getDrawable(R.drawable.nav_background);
        mFlingLogo = res.getDrawable(R.drawable.ic_eos_fling);
        mFlingLogoDark = res.getDrawable(R.drawable.ic_eos_fling_dark);
        mLightsOutLarge = res.getDrawable(R.drawable.ic_sysbar_lights_out_dot_large);
        }

    public void updateResources(Resources res) {
        super.updateResources(res);
//      mOpaque = res.getColor(R.color.navigation_bar_background_opaque);
//      mSemiTransparent = res.getColor(R.color.navigation_bar_background_semi_transparent);
//      mTransparent = res.getColor(R.color.navigation_bar_background_transparent);
//      mWarning = res.getColor(com.android.internal.R.color.battery_saver_mode_color);
        Rect bounds = mGradient.getBounds();
        mGradient = res.getDrawable(R.drawable.nav_background);
        mGradient.setBounds(bounds);
        mFlingLogo = res.getDrawable(R.drawable.ic_eos_fling);
        mFlingLogoDark = res.getDrawable(R.drawable.ic_eos_fling_dark);
        mLightsOutLarge = res.getDrawable(R.drawable.ic_sysbar_lights_out_dot_large);
    }
}
