/*
 * Copyright (C) 2015 The TeamEos Project
 * Author: Randall Rushing aka Bigrushdog
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
 * Bar style FFT renderer. Get callbacks from LavaLamp if enabled. Uses
 * PulseFftValidator to analyze byte stream
 *
 */

package com.android.internal.navigation.pulse;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.Rect;
import android.os.Bundle;

import com.android.internal.navigation.utils.ColorAnimatable.ColorAnimationListener;
import com.android.internal.navigation.utils.ColorAnimator;
import com.android.internal.utils.du.ActionConstants;

public class PulseRenderer implements Renderer, ColorAnimationListener {
    private static final int DEF_PAINT_ALPHA = (byte) 188;

    private float[] mFFTPoints;
    private int mDivisions;
    private int mDefColor;
    private int mUserColor;
    private int mDbFuzzFactor;
    private int mDbFuzz;
    private int mPathEffect1;
    private int mPathEffect2;

    private Paint mPaint;
    private StreamValidator mValidator;

    public PulseRenderer(Bundle config, StreamValidator validator) {
        super();
        mValidator = validator;

        mDivisions = config.getInt(ActionConstants.Fling.CONFIG_pulseDivisions);
        mDefColor = config.getInt(ActionConstants.Fling.CONFIG_pulseFillColor);
        mDbFuzzFactor = config.getInt(ActionConstants.Fling.CONFIG_pulseDbFuzzFactor);
        mDbFuzz = config.getInt(ActionConstants.Fling.CONFIG_pulseDbFuzz);
        mPathEffect1 = config.getInt(ActionConstants.Fling.CONFIG_pulsePathEffect_1);
        mPathEffect2 = config.getInt(ActionConstants.Fling.CONFIG_pulsePathEffect_2);
        mUserColor = mDefColor;

        mPaint = new Paint();
        mPaint.setStrokeWidth(config.getInt(ActionConstants.Fling.CONFIG_pulsePathStrokeWidth));
        mPaint.setAntiAlias(true);
        mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[] {
                mPathEffect1,
                mPathEffect2
        }, 0));
        setColor(mDefColor, false);
    }

    public void render(Canvas canvas, byte[] bytes, Rect rect) {
        if (mFFTPoints == null || mFFTPoints.length < bytes.length * 4) {
            mFFTPoints = new float[bytes.length * 4];
        }
        mValidator.analyze(bytes);

        if (mValidator.isValidStream()) {
            for (int i = 0; i < bytes.length / mDivisions; i++) {
                mFFTPoints[i * 4] = i * 4 * mDivisions;
                mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                byte rfk = bytes[mDivisions * i];
                byte ifk = bytes[mDivisions * i + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                int dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mFFTPoints[i * 4 + 1] = rect.height();
                mFFTPoints[i * 4 + 3] = rect.height() - (dbValue * mDbFuzzFactor + mDbFuzz);
            }
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    private int applyPaintAlphaToColor(int color) {
        int opaqueColor = Color.rgb(Color.red(color),
                Color.green(color), Color.blue(color));
        return (DEF_PAINT_ALPHA << 24) | (opaqueColor & 0x00ffffff);
    }

    public void setColor(int color, boolean fromAnimator) {
        if (!fromAnimator) {
            if (mUserColor != color) {
                mUserColor = color;
            }
        }
        mPaint.setColor(applyPaintAlphaToColor(color));
    }

    public void reset() {
        mValidator.reset();
    }

    public boolean shouldDraw() {
        return mValidator.isValidStream();
    }

    @Override
    public void onColorChanged(ColorAnimator colorAnimator, int color) {
        setColor(color, true);
    }

    @Override
    public void onStartAnimation(ColorAnimator colorAnimator, int firstColor) {

    }

    @Override
    public void onStopAnimation(ColorAnimator colorAnimator, int lastColor) {
        setColor(mUserColor, true);
    }
}
