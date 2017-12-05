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

import com.android.systemui.RecentsComponent;
import com.android.systemui.navigation.NavbarOverlayResources;
import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.statusbar.phone.NavGesture;
import com.android.systemui.stackdivider.Divider;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.ButtonDispatcher;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;

public interface Navigator extends PluginListener<NavGesture> {
    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

    /*
     * Public methods in NavigationBarView. Notice there are numerous other public methods,
     * however, they are never called from anywhere or they are called by other classes that
     * expect the NavigationBarView implementation (NavigationBarTransitions, etc)
     */
    public BarTransitions getBarTransitions();
    public default LightBarTransitionsController getLightTransitionsController() { return null; }
    public default void setComponents(RecentsComponent recentsComponent, Divider divider) {}
    public default void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {}
    public default void abortCurrentGesture() {}
    public default void notifyScreenOn() {}
    public default void setNavigationIconHints(int hints) {}
    public default void setDisabledFlags(int disabledFlags) {}
    public default void setDisabledFlags(int disabledFlags, boolean force) {}
    public default void setLayoutTransitionsEnabled(boolean enabled) {}
    public default void setWakeAndUnlocking(boolean wakeAndUnlocking) {}
    public default void setMenuVisibility(boolean showMenu) {}
    public default void setAccessibilityButtonState(final boolean visible, final boolean longClickable) {}
    public default boolean needsReorient(int rotation) { return false; }
    public default boolean isVertical() { return false; }
    public default void reorient() {}
    public default void onKeyguardOccludedChanged(boolean keyguardOccluded) {}
    public default ButtonDispatcher getRecentsButton() { return null; }
    public default ButtonDispatcher getBackButton() { return null; }
    public default ButtonDispatcher getHomeButton() { return null; }
    public default ButtonDispatcher getAccessibilityButton() { return null; }
    public default void dump(FileDescriptor fd, PrintWriter pw, String[] args) {}
    public default void setMediaPlaying(boolean playing) {}
    public default void setNotificationPanelExpanded(boolean expanded) {}

    /*
     * DUI additional methods to support additional winning ;D
     */
    // Return a top level view of implementation. A time may come where not everything that implements
    // this interface is actually a view
    public View getBaseView();

    // refresh buttons/drawables
    public default void onHandlePackageChanged() {}

    /*
     * PIE support. It may come back someday.
     */
    public default void setForgroundColor(Drawable drawable) {}

    /*
     * get this event from PhoneWindowManager
     */
    public default void setLeftInLandscape(boolean isLeftInLandscape) {}

    // enable/disable features as needed for security/sanity
    public default void setKeyguardShowing(boolean showing) {}

    // assist with settings states/flags that were set on boot
    // but are not set with a user bar change
    // let's find a better way to do this!
    public default void notifyInflateFromUser() {}

    // Theme change! Update all the things! Designed for CMTE
    // but may be useful again someday
    public default void updateNavbarThemedResources(Resources res) {}

    // Designed for CMTE statusbar recreation events, mostly
    // to catch a icon pack change. May be useful again someday
    public default void onRecreateStatusbar() {}

    // also designed to assist with CMTE. CMTE had very peculiar behavior
    // in how navbar resources were "separated" from other SystemUI resources
    public default void setResourceMap(NavbarOverlayResources resourceMap) {}

    // shut down listeners/receivers/observers
    public default void dispose() {}

    // Pulse all the things!... that use it
    public default void setControllers(PulseController pulseController) {}

    // if bar uses custom editor, is it on?
    public default boolean isInEditMode() { return false; }

    // get our editor and pipe commands directly to it
    public default Editor getEditor() { return null; }
}
