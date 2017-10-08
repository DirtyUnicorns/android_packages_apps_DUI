/**
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2017 The DirtyUnicorns Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.navigation.smartbar;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.FloatProperty;
import android.util.Property;
import android.view.Gravity;

import com.android.systemui.navigation.DarkIntensity;
import com.android.systemui.navigation.smartbar.SmartBackButtonDrawable;

public class SmartBackButtonDrawable extends LayerDrawable implements DarkIntensity{
    private float mRotation;
    private Animator mCurrentAnimator;

    private final boolean mHasDarkDrawable;

    private static final int ANIMATION_DURATION = 200;
    public static final Property<SmartBackButtonDrawable, Float> ROTATION
            = new FloatProperty<SmartBackButtonDrawable>("rotation") {
        @Override
        public void setValue(SmartBackButtonDrawable object, float value) {
            object.setRotation(value);
        }

        @Override
        public Float get(SmartBackButtonDrawable object) {
            return object.getRotation();
        }
    };

    public static SmartBackButtonDrawable create(Drawable lightDrawable,
            @Nullable Drawable darkDrawable) {
        if (darkDrawable != null) {
            return new SmartBackButtonDrawable(
                    new Drawable[] { lightDrawable.mutate(), darkDrawable.mutate() });
        } else {
            return new SmartBackButtonDrawable(new Drawable[] { lightDrawable.mutate() });
        }
    }

    private SmartBackButtonDrawable(Drawable[] drawables) {
        super(drawables);
        for (int i = 0; i < drawables.length; i++) {
            setLayerGravity(i, Gravity.CENTER);
        }
        mutate();
        mHasDarkDrawable = drawables.length > 1;
        setDarkIntensity(0f);
    }

    @Override
    public void setDarkIntensity(float intensity) {
        if (!mHasDarkDrawable) {
            return;
        }
        getDrawable(0).setAlpha((int) ((1 - intensity) * 255f));
        getDrawable(1).setAlpha((int) (intensity * 255f));
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        rotateCanvas(canvas);
        getDrawable(0).draw(canvas);
        if (mHasDarkDrawable)  {
            getDrawable(1).draw(canvas);
        }
    }

    private void rotateCanvas(Canvas c) {
        final Rect bounds = getDrawable(0).getBounds();
        final int boundsCenterX = bounds.width() / 2;
        final int boundsCenterY = bounds.height() / 2;

        c.translate(boundsCenterX, boundsCenterY);
        c.rotate(mRotation);
        c.translate(- boundsCenterX, - boundsCenterY);
    }

    public void setRotation(float rotation) {
        mRotation = rotation;
        invalidateSelf();
    }

    public float getRotation() {
        return mRotation;
    }

    public void setImeVisible(boolean ime) {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        final float nextRotation = ime ? - 90 : 0;
        if (mRotation == nextRotation) {
            return;
        }

        if (isVisible() && ActivityManager.isHighEndGfx()) {
            mCurrentAnimator = ObjectAnimator.ofFloat(this, ROTATION, nextRotation)
                    .setDuration(ANIMATION_DURATION);
            mCurrentAnimator.start();
        } else {
            setRotation(nextRotation);
        }
    }
}
