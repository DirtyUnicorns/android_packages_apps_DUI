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
 * Visualizer drawing. Handles orientation change, size change, and reset
 * flags for clearing bitmaps
 *
 */

package com.android.internal.navigation.fling.pulse;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Bitmap.Config;

import com.android.internal.navigation.fling.FlingModule;
import com.pheelicks.visualizer.BaseVisualizer;
import com.pheelicks.visualizer.renderer.Renderer;

public class PulseVisualizer extends BaseVisualizer {
    private FlingModule.Callbacks mCallback;
    private Bitmap mRotatedBitmap;
    private Matrix mRotMatrix;
    private boolean mVertical;
    private boolean mLeftInLandscape;
    private boolean mResetDrawing = true;
    protected boolean mDrawingEnabled = true;
    

    public PulseVisualizer(FlingModule.Callbacks callback) {
        super();
        mCallback = callback;
        mRotMatrix = new Matrix();
    }

    @Override
    protected void onInvalidate() {
        mCallback.onInvalidate();
    }

    @Override
    protected int onGetWidth() {
        return mCallback.onGetWidth();
    }

    @Override
    protected int onGetHeight() {
        return mCallback.onGetHeight();
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

    public boolean isDrawingEnabled() {
        return mDrawingEnabled;
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
            // Render all FFT renderers
            for (Renderer r : mRenderers) {
                r.render(mCanvas, mFftData, mRect);
            }
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
