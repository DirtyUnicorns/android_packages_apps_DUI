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
 * Definition of a renderer. Currently unused, may need to be expanded
 *
 */

package com.android.systemui.navigation.pulse;

import com.android.systemui.navigation.utils.ColorAnimatable.ColorAnimationListener;

import android.graphics.Canvas;
import android.graphics.Rect;

// TODO: This should probably just be an abstract class
public interface Renderer extends ColorAnimationListener {
    public void render(Canvas canvas, byte[] bytes, Rect rect);
    public void setColor(int color, boolean fromAnimator);
}
