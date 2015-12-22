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
 * TeamEos logo doubles as the Fling feature indicator! Most state is managed
 * by FlingLogoController
 *
 */

package com.android.internal.navigation.fling;

import com.android.internal.utils.du.DUActionUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

public class FlingLogoView extends ImageView {

    public static final String TAG = FlingLogoView.class.getSimpleName();

    private int mLogoColor = Color.WHITE;

    public FlingLogoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlingLogoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setBackground(null);
        updateResources(context.getResources());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // TEMP: pass all events to NX, for now
        return false;
    }

    public void updateResources(Resources res) {
        // int color = res.getColor(R.color.status_bar_clock_color);
        // setDrawableColorFilter(color);
    }

    public int getLogoColor() {
        return mLogoColor;
    }

    public void setLogoColor(int color) {
        if (mLogoColor == color) {
            return;
        } else if (color == -1) {
            mLogoColor = -1;
            getDrawable().setColorFilter(null);
            return;
        } else if (color == -2) {
            mLogoColor = DUActionUtils.getColor(getContext(), "status_bar_clock_color",
                    DUActionUtils.PACKAGE_SYSTEMUI);
        } else {
            mLogoColor = color;
        }
        setDrawableColorFilter(mLogoColor);
    }

    private void setDrawableColorFilter(int color) {
        Drawable logo = getDrawable();
        logo.setColorFilter(color, Mode.SRC_ATOP);
    }
}
