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
 * Callbacks into statusbar (likely deprecated soon)
 * 
 */

package com.android.internal.navigation;

public interface StatusbarImpl {
    public boolean shouldDisableNavbarGestures();
    public void setInteracting(int barWindow, boolean interacting);
    public void showNextAffiliatedTask();
    public void showPreviousAffiliatedTask();
    public Navigator getNavigationBarView();
}
