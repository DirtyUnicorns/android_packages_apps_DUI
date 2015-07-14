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

package com.android.internal.navigation.fling.pulse;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.internal.navigation.utils.LavaLamp;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.renderer.Renderer;

public abstract class PulseRenderer extends Renderer implements LavaLamp.LavaListener {
    public static final int STATE_SUCCESS = 1;
    public static final int STATE_FAILED = 2;

    private static final int DEF_PAINT_ALPHA = (byte) 188;
    private static final int DEF_PAINT_COLOR = Color.WHITE;
    private int mDivisions;
    private Paint mPaint;

    private PulseFftValidator mValidator;
    private LavaLamp mLavaLamp;

    private int mUserColor = Color.WHITE;
    private boolean mLavaEnabled;


    public PulseRenderer(int divisions) {
        super();
        mDivisions = divisions;
        mPaint = new Paint();
        mPaint.setStrokeWidth(50f);
        mPaint.setAntiAlias(true);
        updateColor(DEF_PAINT_COLOR);
        mLavaLamp = new LavaLamp(this);

        mValidator = new PulseFftValidator() {
            @Override
            public void onStreamValidated(boolean isValid) {
                int state = isValid ? STATE_SUCCESS : STATE_FAILED;
                onRenderStateChanged(state);
            }
        };
    }

    public abstract void onRenderStateChanged(int state);

    public void setLavaAnimationTime(int time) {
        mLavaLamp.setAnimationTime(time);
    }

    public boolean isLavaLampEnabled() {
        return mLavaEnabled;
    }

    public void setLavaLampEnabled(boolean enabled) {
        if (mLavaEnabled != enabled) {
            mLavaEnabled = enabled;
            if (mValidator.isValidStream()) {
                if (enabled) {
                    mLavaLamp.startAnimation();
                } else {
                    mLavaLamp.stopAnimation();
                }
            }
        }
    }

    public void startLavaLamp() {
        if (mLavaEnabled) {
            mLavaLamp.startAnimation();
        }
    }

    public void stopLavaLamp() {
        if (mLavaEnabled) {
            mLavaLamp.stopAnimation();
        }
    }

    public void updateColor(int color) {
        mPaint.setColor(applyPaintAlphaToColor(color));
    }

    private int applyPaintAlphaToColor(int color) {
        int opaqueColor = Color.rgb(Color.red(color),
                Color.green(color), Color.blue(color));
        return (DEF_PAINT_ALPHA << 24) | (opaqueColor & 0x00ffffff);
    }

    public void setColor(int color) {
        if (mUserColor != color) {
            mUserColor = color;
            updateColor(color);
        }
    }

    @Override
    public void onRender(Canvas canvas, AudioData data, Rect rect) {
    }

    @Override
    public void onRender(Canvas canvas, FFTData data, Rect rect) {
        mValidator.analyze(data.bytes);

        if (mValidator.isValidStream()) {
            for (int i = 0; i < data.bytes.length / mDivisions; i++) {
                mFFTPoints[i * 4] = i * 4 * mDivisions;
                mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                byte rfk = data.bytes[mDivisions * i];
                byte ifk = data.bytes[mDivisions * i + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                int dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mFFTPoints[i * 4 + 1] = rect.height();
                mFFTPoints[i * 4 + 3] = rect.height() - (dbValue * 2 - 10);
            }
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    public void reset() {
        mValidator.resetFlags();
    }

    public boolean shouldDraw() {
        return mValidator.isValidStream();
    }

    @Override
    public void onColorUpdated(int color) {
        updateColor(color);
    }

    @Override
    public int onGetInitialColor() {
        return mUserColor;
    }

    @Override
    public void onStartLava() {
    }

    @Override
    public void onStopLava(int lastColor) {
        updateColor(mUserColor);
    }
}
