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
import android.os.UserHandle;

import com.android.systemui.navigation.pulse.Renderer;
import com.android.systemui.navigation.pulse.StreamValidator;
import com.android.systemui.navigation.utils.ColorAnimator;
import com.android.systemui.R;

import android.provider.Settings;

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
    private int mNumDivision;
    private int mCustomDimen;
    private int mFilledBlock;
    private int mEmptyBlock;
    private int mFudgeFactor;
    private int mFuzz;
    private Paint mPaint;
    private StreamValidator mValidator;

    public PulseRenderer(Context ctx, StreamValidator validator) {
        super();
        mValidator = validator;
        mDefColor = ctx.getResources().getColor(R.color.config_pulseFillColor);
        mUserColor = mDefColor;
        mPaint = new Paint();
        getcustomizations(ctx);
        mDbFuzz = ctx.getResources().getInteger(R.integer.config_pulseDbFuzz);
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

    public void getcustomizations(Context ctx) {
        mFilledBlock = Settings.Secure.getIntForUser(
            ctx.getContentResolver(), Settings.Secure.PULSE_FILLED_BLOCK_SIZE, 0,
            UserHandle.USER_CURRENT);
        mEmptyBlock = Settings.Secure.getIntForUser(
            ctx.getContentResolver(), Settings.Secure.PULSE_EMPTY_BLOCK_SIZE, 0,
            UserHandle.USER_CURRENT);
        mCustomDimen = Settings.Secure.getIntForUser(
            ctx.getContentResolver(), Settings.Secure.PULSE_CUSTOM_DIMEN, 0,
            UserHandle.USER_CURRENT);
        mNumDivision = Settings.Secure.getIntForUser(
            ctx.getContentResolver(), Settings.Secure.PULSE_CUSTOM_DIV, 0,
            UserHandle.USER_CURRENT);
        mFudgeFactor = Settings.Secure.getIntForUser(
            ctx.getContentResolver(), Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR, 0,
            UserHandle.USER_CURRENT);
        if (mFilledBlock == 0) {
            mPathEffect1 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect_1);
        }
        else if (mFilledBlock == 1) {
            mPathEffect1 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect1_1);
        }
        else if (mFilledBlock == 2) {
            mPathEffect1 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect2_1);
        }
        else if (mFilledBlock == 3) {
            mPathEffect1 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect3_1);
        }
        else if (mFilledBlock == 4) {
            mPathEffect1 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect4_1);
        }
        if (mEmptyBlock  == 0) {
            mPathEffect2 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect_2);
        }
        else if (mEmptyBlock == 1) {
            mPathEffect2 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect1_2);
        }
        else if (mEmptyBlock  == 2) {
            mPathEffect2 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect2_2);
        }
        else if (mEmptyBlock  == 3) {
            mPathEffect2 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect3_2);
        }
        else if (mEmptyBlock  == 4) {
            mPathEffect2 = ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathEffect4_2);
        }
        if (mCustomDimen == 0) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth));
        }
        else if (mCustomDimen == 1) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth1));
        }
        else if (mCustomDimen == 2) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth2));
        }
        else if (mCustomDimen == 3) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth3));
        }
        else if (mCustomDimen == 4) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth4));
        }
        else if (mCustomDimen == 5) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth5));
        }
        else if (mCustomDimen == 6) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth6));
        }
        else if (mCustomDimen == 7) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth7));
        }
        else if (mCustomDimen == 8) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth8));
        }
        else if (mCustomDimen == 9) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth9));
        }
        else if (mCustomDimen == 10) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth10));
        }
        else if (mCustomDimen == 11) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth11));
        }
        else if (mCustomDimen == 12) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth12));
        }
        else if (mCustomDimen == 13) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth13));
        }
        else if (mCustomDimen == 14) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth14));
        }
        else if (mCustomDimen == 15) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth15));
        }
        else if (mCustomDimen == 16) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth16));
        }
        else if (mCustomDimen == 17) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth17));
        }
        else if (mCustomDimen == 18) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth18));
        }
        else if (mCustomDimen == 19) {
            mPaint.setStrokeWidth(ctx.getResources().getDimensionPixelSize(R.dimen.config_pulsePathStrokeWidth19));
        }
        if (mNumDivision == 0) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions);
        }
        else if (mNumDivision == 1) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions1);
        }
        else if (mNumDivision == 2) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions2);
        }
        else if (mNumDivision == 3) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions3);
        }
        else if (mNumDivision == 4) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions4);
        }
        else if (mNumDivision == 5) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions5);
        }
        else if (mNumDivision == 6) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions6);
        }
        else if (mNumDivision == 7) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions7);
        }
        else if (mNumDivision == 8) {
            mDivisions = ctx.getResources().getInteger(R.integer.config_pulseDivisions8);
        }
        if(mFudgeFactor == 0) {
            mDbFuzzFactor = ctx.getResources().getInteger(R.integer.config_pulseDbFuzzFactor);
        }
        else if (mFudgeFactor == 1) {
            mDbFuzzFactor = ctx.getResources().getInteger(R.integer.config_pulseDbFuzzFactor1);
        }
        else if (mFudgeFactor == 2) {
            mDbFuzzFactor = ctx.getResources().getInteger(R.integer.config_pulseDbFuzzFactor2);
        }
        else if (mFudgeFactor == 3) {
            mDbFuzzFactor = ctx.getResources().getInteger(R.integer.config_pulseDbFuzzFactor3);
        }
        else if (mFudgeFactor == 4) {
            mDbFuzzFactor = ctx.getResources().getInteger(R.integer.config_pulseDbFuzzFactor4);
        }
    }
}
