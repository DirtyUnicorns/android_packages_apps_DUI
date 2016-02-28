/**
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
 * Definitions of a data structure that colors things with value animators
 * 
 */

package com.android.systemui.navigation.utils;

import com.android.systemui.navigation.utils.ColorAnimator;

public class ColorAnimatable {
    public interface ColorAnimationListener {
        public void onColorChanged(ColorAnimator colorAnimator, int color);
        public void onStartAnimation(ColorAnimator colorAnimator, int firstColor);
        public void onStopAnimation(ColorAnimator colorAnimator, int lastColor);
    }

    public interface ColorAnimatorMachine {
        public void setColorAnimatorListener(ColorAnimationListener listener);
        public void removeColorAnimatorListener(ColorAnimationListener listener);
    }

    public interface AnimatorControls {
        public void start();
        public void stop();
        public void setAnimationTime(long millis);
        public int getCurrentColor();
    }
}
