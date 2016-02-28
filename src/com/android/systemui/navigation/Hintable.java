/*
 * Copyright (C) 2013 SlimRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This interface was renamed from NavigationBar.Callbacks to Hintable
 * to better reflect this navigation implementation
 */

package com.android.systemui.navigation;

import com.android.systemui.navigation.BaseNavigationBar;

/**
 * Base interface for navigation components to receive core hints from statusbar.<br>
 * See {@link BaseNavigationBar} for an example.
 */
public interface Hintable {
    /**
     * @param hints flags from StatusBarManager (NAVIGATION_HINT...) to indicate which key is
     * available for navigation
     * @see StatusBarManager
     */
    public abstract void setNavigationIconHints(int hints);
    /**
     * @param showMenu {@code true} when an menu key should be displayed by the navigation bar.
     */
    public abstract void setMenuVisibility(boolean showMenu);
    /**
     * @param disabledFlags flags from View (STATUS_BAR_DISABLE_...) to indicate which key
     * is currently disabled on the navigation bar.
     * {@see View}
     */
    public void setDisabledFlags(int disabledFlags);
};
