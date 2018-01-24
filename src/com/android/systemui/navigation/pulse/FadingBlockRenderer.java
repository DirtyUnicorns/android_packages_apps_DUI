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
import android.content.res.Resources;
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
import android.util.TypedValue;

import com.android.internal.util.NotificationColorUtil;
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
    private int mAlbumColor = -1;
    private boolean mAutoColor;
    private int mDbFuzzFactor;
    private int mDbFuzz;
    private int mPathEffect1;
    private int mPathEffect2;
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
        postInvalidate();
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
        mPaint.setColor(applyPaintAlphaToColor(mAutoColor && mAlbumColor != -1 ? mAlbumColor : mUserColor));
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
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_AUTO_COLOR), false,
                    this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }

        public void updateSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            final Resources res = mContext.getResources();

            mAutoColor = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_AUTO_COLOR, 0,
                    UserHandle.USER_CURRENT) == 1;

            mLavaLampEnabled = !mAutoColor && Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;

            mUserColor = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_COLOR,
                    mContext.getResources().getColor(R.color.config_pulseFillColor),
                    UserHandle.USER_CURRENT);
            if (!mLavaLampEnabled) {
                mPaint.setColor(applyPaintAlphaToColor(mAutoColor && mAlbumColor != -1 ? mAlbumColor : mUserColor));
            }
            int time = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_SPEED, 10000,
                    UserHandle.USER_CURRENT);
            mLavaLamp.setAnimationTime(time);
            if (mLavaLampEnabled && mIsValidStream) {
                mLavaLamp.start();
            } else {
                mLavaLamp.stop();
            }
            int emptyBlock = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_EMPTY_BLOCK_SIZE, 1,
                    UserHandle.USER_CURRENT);
            int customDimen = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_DIMEN, 14,
                    UserHandle.USER_CURRENT);
            int numDivision = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_DIV, 16,
                    UserHandle.USER_CURRENT);
            int fudgeFactor = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR, 4,
                    UserHandle.USER_CURRENT);
            int filledBlock = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_FILLED_BLOCK_SIZE, 4,
                    UserHandle.USER_CURRENT);

            mPathEffect1 = getLimitedDimenValue(filledBlock, 4, 8, res);
            mPathEffect2 = getLimitedDimenValue(emptyBlock, 0, 4, res);
            mPaint.setPathEffect(null);
            mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[] {
                    mPathEffect1,
                    mPathEffect2
            }, 0));
            mPaint.setStrokeWidth(getLimitedDimenValue(customDimen, 1, 30, res));
            mDivisions = validateDivision(numDivision);
            mDbFuzzFactor = Math.max(2, Math.min(6, fudgeFactor));
        }
    }

    private static int getLimitedDimenValue(int val, int min, int max, Resources res) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                Math.max(min, Math.min(max, val)), res.getDisplayMetrics());
    }

    private static int validateDivision(int val) {
        // if a bad value was passed from settings (not divisible by 2)
        // reset to default value of 16. Validate range.
        if (val % 2 != 0) {
            val = 16;
        }
        return Math.max(2, Math.min(44, val));
    }

    public void setColors(boolean colorizedMedia, int[] colors) {
        if (colorizedMedia) {
            // be sure the color will always have an acceptable contrast against black navbar
            mAlbumColor = NotificationColorUtil.findContrastColorAgainstDark(colors[0], 0x000000, true, 2);
            // now be sure the color will always have an acceptable contrast against white navbar
            mAlbumColor = NotificationColorUtil.findContrastColor(mAlbumColor, 0xffffff, true, 2);
        } else {
            mAlbumColor = -1;
        }
        if (mAutoColor && !mLavaLampEnabled) {
            mPaint.setColor(applyPaintAlphaToColor(mAlbumColor != 1 ? mAlbumColor : mUserColor));
        }
    }
}
