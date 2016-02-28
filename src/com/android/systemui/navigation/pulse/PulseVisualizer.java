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
 * Visualizer drawing. Handles orientation change, size change, and reset
 * flags for clearing bitmaps
 *
 */

package com.android.systemui.navigation.pulse;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Bitmap.Config;

import com.android.systemui.navigation.pulse.BaseVisualizer;
import com.android.systemui.navigation.pulse.PulseController.PulseObserver;

public class PulseVisualizer extends BaseVisualizer {
    private PulseObserver mCallback;
    private Bitmap mRotatedBitmap;
    private Matrix mRotMatrix;
    private boolean mVertical;
    private boolean mLeftInLandscape;
    private boolean mResetDrawing = true;    

    public PulseVisualizer(PulseObserver callback) {
        super();
        mRotMatrix = new Matrix();
        mCallback = callback;
    }

    public void setPulseObserver(PulseObserver observer) {
        mCallback = observer;
    }

    @Override
    protected void onInvalidate() {
        mCallback.invalidate();
    }

    @Override
    protected int onGetWidth() {
        return mCallback.getWidth();
    }

    @Override
    protected int onGetHeight() {
        return mCallback.getHeight();
    }

    public void resetDrawing() {
        mResetDrawing = true;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        if (mLeftInLandscape != leftInLandscape) {
            mLeftInLandscape = leftInLandscape;
            mResetDrawing = true;
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        final int sWidth = getWidth();
        final int sHeight = getHeight();
        final int cWidth = canvas.getWidth();
        final int cHeight = canvas.getHeight();
        final boolean isVertical = sHeight > sWidth;

        // only null resources on orientation change
        // to allow proper fade effect
        if (isVertical != mVertical || mResetDrawing) {
            mResetDrawing = false;
            mVertical = isVertical;
            mCanvas = null;
            mCanvasBitmap = null;
            mRotatedBitmap = null;
        }

        // Create canvas once we're ready to draw
        // the renderers don't like painting vertically
        // if vertical, create a horizontal canvas based on flipped current
        // dimensions, let renders paint, then rotate the bitmap to draw to Fling surface
        mRect.set(0, 0, isVertical ? sHeight : sWidth, isVertical ? sWidth : sHeight);

        if (mCanvasBitmap == null) {
            mCanvasBitmap = Bitmap.createBitmap(isVertical ? cHeight : cWidth,
                    isVertical ? cWidth : cHeight,
                    Config.ARGB_8888);
        }

        if (mCanvas == null) {
            mCanvas = new Canvas(mCanvasBitmap);
        }

        if (mFFTBytes != null) {
            mRenderer.render(mCanvas, mFftData, mRect);
        }

        if (mDrawingEnabled) {
            // Fade out old contents
            mCanvas.drawPaint(mFadePaint);

            // if vertical flip our horizontally rendered bitmap
            if (isVertical) {
                mRotMatrix.reset();
                mRotMatrix.postRotate(mLeftInLandscape ? 90 : -90);
                mRotatedBitmap = Bitmap.createBitmap(mCanvasBitmap, 0, 0,
                        mCanvasBitmap.getWidth(), mCanvasBitmap.getHeight(),
                        mRotMatrix, true);
            }
            canvas.drawBitmap(isVertical ? mRotatedBitmap : mCanvasBitmap, mMatrix, null);
        }
    }
}
