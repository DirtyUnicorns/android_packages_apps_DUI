/**
 * Copyright (C) 2014 The TeamEos Project
 * Copyright (C) 2016 The DirtyUnicorns Project
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
 * Interface for all the things that go in that navigation window and do
 * amazing things. This is all the rest of the world needs to see ;D
 * 
 */

package com.android.systemui.navigation;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.android.systemui.navigation.NavigationController.NavbarOverlayResources;
import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnTouchListener;

public interface Navigator {
    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

    public View getBaseView();
    public boolean isVertical();
    public abstract BarTransitions getBarTransitions();
    public void onHandlePackageChanged();
    public void setForgroundColor(Drawable drawable);
    public void setLeftInLandscape(boolean isLeftInLandscape);
    public void setKeyguardShowing(boolean showing);
    public void notifyInflateFromUser();
    public void updateNavbarThemedResources(Resources res);
    public void onRecreateStatusbar();
    public void setResourceMap(NavbarOverlayResources resourceMap);
    public void setTransparencyAllowedWhenVertical(boolean allowed);
    public void abortCurrentGesture();
    public void setStatusBar(PhoneStatusBar statusbar);
    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener);
    public void dispose();
    public void notifyScreenOn(boolean screenOn);
    public void setDisabledFlags(int disabledFlags, boolean force);
    public void setNavigationIconHints(int hints);
    public void setMenuVisibility(boolean showMenu);
    public void setDisabledFlags(int disabledFlags);
    public void reorient();
    public void setListeners(OnTouchListener userAutoHideListener, View.OnLongClickListener longPressBackListener);
    public void setControllers(PulseController pulseController);
    public boolean isInEditMode();
    public void setLayoutTransitionsEnabled(boolean enabled);
    public void setWakeAndUnlocking(boolean wakeAndUnlocking);
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args);
}
