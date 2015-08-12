/*
 * Copyright (C) 2015 The TeamEos Project
 * 
 * Contributor: Randall Rushing aka Bigrushdog
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
 * amazing things. This is all the rest of the work needs to see ;D
 * 
 */

package com.android.internal.navigation;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnTouchListener;

public interface Navigator extends Hintable {
    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }
    public View getCurrentView();
    public View getBaseView();
    public View.OnTouchListener getHomeActionListener();
    public boolean isVertical();
    public abstract BarTransitions getBarTransitions();
    public void onHandlePackageChanged();
    public void setForgroundColor(Drawable drawable);
    public void setLeftInLandscape(boolean isLeftInLandscape);
    public void setKeyguardShowing(boolean showing);
    public void notifyInflateFromUser();
    public void updateResources(Resources res);
    public void setTransparencyAllowedWhenVertical(boolean allowed);
    public void setDelegateView(View view);
    public void setStatusBarCallbacks(StatusbarImpl statusbar);
    public void setDisabledFlags(int disabledFlags);
    public void setDisabledFlags(int disabledFlags, boolean force);
    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener);
    public void dispose();
    public void notifyScreenOn(boolean screenOn);
    public void setSlippery(boolean newSlippery);
    public void reorient();
    public void setKeyButtonListeners(OnTouchListener homeActionListener, OnTouchListener userAutoHideListener);
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args);
}
