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
 * Bar style FFT renderer. Get callbacks from LavaLamp if enabled. Uses
 * PulseFftValidator to analyze byte stream
 * Render logic inspired by Roman Birg aka romanbb in his Equalizer
 * tile produced for Cyanogenmod as well as Felix Palmer
 * in his android-visualizer library
 *
 */

package com.android.systemui.navigation.pulse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.systemui.navigation.pulse.Renderer;
import com.android.systemui.navigation.pulse.StreamValidator;
import com.android.systemui.navigation.utils.ColorAnimator;
import com.android.systemui.R;

public class PulseRenderer implements Renderer {
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

    public PulseRenderer(Context ctx, StreamValidator validator) {
        super();
        mValidator = validator;

        mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions);
        mDefColor = ctx.getResources().getColor(R.color.config_pulseFillColor);
        mDbFuzzFactor = ctx.getResources().getInteger(R.integer.config_pulseDbFuzzFactor);
        mDbFuzz = ctx.getResources().getInteger(R.integer.config_pulseDbFuzz);
        mPathEffect1 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect_1);
        mPathEffect2 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect_2);
        mUserColor = mDefColor;

        mPaint = new Paint();
        mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth));
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
