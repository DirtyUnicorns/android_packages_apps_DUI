/**
 * Copyright 2011, Felix Palmer
 * Copyright (C) 2014 The TeamEos Project
 * Copyright (C) 2016 The DirtyUnicorns Project
 *
 * AOSP Navigation implementation by
 * @author: Randall Rushing <randall.rushing@gmail.com>
 *
 * Licensed under the MIT license:
 * http://creativecommons.org/licenses/MIT/
 *
 * Old school FFT renderer adapted from
 * @link https://github.com/felixpalmer/android-visualizer
 *
 */

package com.android.systemui.navigation.pulse;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.navigation.pulse.PulseController.PulseObserver;
import com.android.systemui.navigation.utils.ColorAnimator;

public class FadingBlockRenderer extends Renderer implements ColorAnimator.ColorAnimationListener {
    private static final int DEF_PAINT_ALPHA = (byte) 188;
    private byte[] mFFTBytes;
    private Paint mPaint;
    private Paint mFadePaint;
    private boolean mVertical;
    private boolean mLeftInLandscape;
    private float[] mFFTPoints;
    private byte rfk, ifk;
    private int dbValue;
    private float magnitude;
    private int mDivisions;
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
    private Bitmap mCanvasBitmap;
    private Canvas mCanvas;
    private Matrix mMatrix;
    private int mWidth;
    private int mHeight;

    private ColorAnimator mLavaLamp;
    private LegacySettingsObserver mObserver;
    private boolean mLavaLampEnabled;
    private boolean mIsValidStream;

    public FadingBlockRenderer(Context context, Handler handler, PulseObserver callback) {
        super(context, handler, callback);
        mObserver = new LegacySettingsObserver(handler);
        mLavaLamp = new ColorAnimator();
        mLavaLamp.setColorAnimatorListener(this);
        mPaint = new Paint();
        mFadePaint = new Paint();
        mFadePaint.setColor(Color.argb(200, 255, 255, 255));
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
        mMatrix = new Matrix();
        mDbFuzz = mContext.getResources().getInteger(R.integer.config_pulseDbFuzz);
        mObserver.updateSettings();
        mPaint.setAntiAlias(true);
        onSizeChanged(0, 0, 0, 0);
    }

    @Override
    public void onStreamAnalyzed(boolean isValid) {
        mIsValidStream = isValid;
        if (isValid) {
            onSizeChanged(0, 0, 0, 0);
            if (mLavaLampEnabled) {
                mLavaLamp.start();
            }
        }
    }

    @Override
    public void onFFTUpdate(byte[] bytes) {
        mFFTBytes = bytes;
        if (mFFTBytes != null) {
            if (mFFTPoints == null || mFFTPoints.length < mFFTBytes.length * 4) {
                mFFTPoints = new float[mFFTBytes.length * 4];
            }
            for (int i = 0; i < mFFTBytes.length / mDivisions; i++) {
                if (mVertical) {
                    mFFTPoints[i * 4 + 1] = i * 4 * mDivisions;
                    mFFTPoints[i * 4 + 3] = i * 4 * mDivisions;
                } else {
                    mFFTPoints[i * 4] = i * 4 * mDivisions;
                    mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                }
                rfk = mFFTBytes[mDivisions * i];
                ifk = mFFTBytes[mDivisions * i + 1];
                magnitude = (rfk * rfk + ifk * ifk);
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;
                if (mVertical) {
                    mFFTPoints[i * 4] = mLeftInLandscape ? 0 : mWidth;
                    mFFTPoints[i * 4 + 2] = mLeftInLandscape ? (dbValue * mDbFuzzFactor + mDbFuzz)
                            : (mWidth - (dbValue * mDbFuzzFactor + mDbFuzz));
                } else {
                    mFFTPoints[i * 4 + 1] = mHeight;
                    mFFTPoints[i * 4 + 3] = mHeight - (dbValue * mDbFuzzFactor + mDbFuzz);
                }
            }
        }
        mCanvas.drawLines(mFFTPoints, mPaint);
        mCanvas.drawPaint(mFadePaint);
        mCallback.postInvalidate();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mCallback.getWidth() > 0 && mCallback.getHeight() > 0) {
            mWidth = mCallback.getWidth();
            mHeight = mCallback.getHeight();
            mVertical = mHeight > mWidth;
            mCanvasBitmap = Bitmap.createBitmap(mWidth, mHeight, Config.ARGB_8888);
            mCanvas = new Canvas(mCanvasBitmap);
        }
    }

    @Override
    public void onColorChanged(ColorAnimator colorAnimator, int color) {
        mPaint.setColor(applyPaintAlphaToColor(color));
    }

    @Override
    public void onStartAnimation(ColorAnimator colorAnimator, int firstColor) {
    }

    @Override
    public void onStopAnimation(ColorAnimator colorAnimator, int lastColor) {
        mPaint.setColor(applyPaintAlphaToColor(mUserColor));
    }

    @Override
    public void setLeftInLandscape(boolean leftInLandscape) {
        if (mLeftInLandscape != leftInLandscape) {
            mLeftInLandscape = leftInLandscape;
            onSizeChanged(0, 0, 0, 0);
        }
    }

    @Override
    public void destroy() {
        mContext.getContentResolver().unregisterContentObserver(mObserver);
        mLavaLamp.stop();
        mCanvasBitmap = null;
    }

    @Override
    public void onVisualizerLinkChanged(boolean linked) {
        if (!linked) {
            mLavaLamp.stop();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(mCanvasBitmap, mMatrix, null);
    }

    private int applyPaintAlphaToColor(int color) {
        int opaqueColor = Color.rgb(Color.red(color),
                Color.green(color), Color.blue(color));
        return (DEF_PAINT_ALPHA << 24) | (opaqueColor & 0x00ffffff);
    }

    private class LegacySettingsObserver extends ContentObserver {
        public LegacySettingsObserver(Handler handler) {
            super(handler);
            register();
        }

        void register() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED), false,
                    this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_SPEED), false,
                    this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIMEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIV), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_FILLED_BLOCK_SIZE), false,
                    this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_EMPTY_BLOCK_SIZE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR), false,
                    this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }

        public void updateSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            mLavaLampEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
            mUserColor = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_COLOR,
                    mContext.getResources().getColor(R.color.config_pulseFillColor),
                    UserHandle.USER_CURRENT);
            if (!mLavaLampEnabled) {
                mPaint.setColor(applyPaintAlphaToColor(mUserColor));
            }
            int time = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_SPEED, ColorAnimator.ANIM_DEF_DURATION,
                    UserHandle.USER_CURRENT);
            mLavaLamp.setAnimationTime(time);
            if (mLavaLampEnabled && mIsValidStream) {
                mLavaLamp.start();
            } else {
                mLavaLamp.stop();
            }
            mFilledBlock = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_FILLED_BLOCK_SIZE, 0,
                    UserHandle.USER_CURRENT);
            mEmptyBlock = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_EMPTY_BLOCK_SIZE, 0,
                    UserHandle.USER_CURRENT);
            mCustomDimen = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_DIMEN, 0,
                    UserHandle.USER_CURRENT);
            mNumDivision = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_DIV, 0,
                    UserHandle.USER_CURRENT);
            mFudgeFactor = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR, 0,
                    UserHandle.USER_CURRENT);
            if (mFilledBlock == 0) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect_1);
            }
            else if (mFilledBlock == 1) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect1_1);
            }
            else if (mFilledBlock == 2) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect2_1);
            }
            else if (mFilledBlock == 3) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect3_1);
            }
            else if (mFilledBlock == 4) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect4_1);
            }
            if (mEmptyBlock == 0) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect_2);
            }
            else if (mEmptyBlock == 1) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect1_2);
            }
            else if (mEmptyBlock == 2) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect2_2);
            }
            else if (mEmptyBlock == 3) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect3_2);
            }
            else if (mEmptyBlock == 4) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect4_2);
            }
            mPaint.setPathEffect(null);
            mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[] {
                    mPathEffect1,
                    mPathEffect2
            }, 0));
            if (mCustomDimen == 0) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth));
            }
            else if (mCustomDimen == 1) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth1));
            }
            else if (mCustomDimen == 2) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth2));
            }
            else if (mCustomDimen == 3) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth3));
            }
            else if (mCustomDimen == 4) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth4));
            }
            else if (mCustomDimen == 5) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth5));
            }
            else if (mCustomDimen == 6) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth6));
            }
            else if (mCustomDimen == 7) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth7));
            }
            else if (mCustomDimen == 8) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth8));
            }
            else if (mCustomDimen == 9) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth9));
            }
            else if (mCustomDimen == 10) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth10));
            }
            else if (mCustomDimen == 11) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth11));
            }
            else if (mCustomDimen == 12) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth12));
            }
            else if (mCustomDimen == 13) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth13));
            }
            else if (mCustomDimen == 14) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth14));
            }
            else if (mCustomDimen == 15) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth15));
            }
            else if (mCustomDimen == 16) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth16));
            }
            else if (mCustomDimen == 17) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth17));
            }
            else if (mCustomDimen == 18) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth18));
            }
            else if (mCustomDimen == 19) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth19));
            }
            if (mNumDivision == 0) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions);
            }
            else if (mNumDivision == 1) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions1);
            }
            else if (mNumDivision == 2) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions2);
            }
            else if (mNumDivision == 3) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions3);
            }
            else if (mNumDivision == 4) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions4);
            }
            else if (mNumDivision == 5) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions5);
            }
            else if (mNumDivision == 6) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions6);
            }
            else if (mNumDivision == 7) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions7);
            }
            else if (mNumDivision == 8) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions8);
            }
            if (mFudgeFactor == 0) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor);
            }
            else if (mFudgeFactor == 1) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor1);
            }
            else if (mFudgeFactor == 2) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor2);
            }
            else if (mFudgeFactor == 3) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor3);
            }
            else if (mFudgeFactor == 4) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor4);
            }
        }

    }
}
