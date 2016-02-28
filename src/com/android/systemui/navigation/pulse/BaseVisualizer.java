/**
 * Copyright 2011, Felix Palmer
 * Copyright 2015, The TeamEos Project
 *
 * Licensed under the MIT license:
 * http://creativecommons.org/licenses/MIT/
 */

package com.android.systemui.navigation.pulse;

import com.android.systemui.navigation.pulse.Renderer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.util.Log;

/**
 * A class that draws visualizations of data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture } and
 * {@link Visualizer.OnDataCaptureListener#onFftDataCapture }
 */
public abstract class BaseVisualizer {
    protected static String TAG = "BaseVisualizer";

    protected byte[] mFFTBytes;
    protected byte[] mFftData;
    protected Matrix mMatrix;
    protected boolean mDrawingEnabled = false;
    protected Rect mRect = new Rect();
    protected Visualizer mVisualizer;
    protected int mAudioSessionId;

    protected Renderer mRenderer;

    protected Paint mFlashPaint = new Paint();
    protected Paint mFadePaint = new Paint();
    protected Bitmap mCanvasBitmap;
    protected Canvas mCanvas;
    protected boolean mFlash = false;

    public BaseVisualizer() {
        mFFTBytes = null;
        mFftData = null;
        mMatrix = new Matrix();

        mFlashPaint.setColor(Color.argb(122, 255, 255, 255));
        mFadePaint.setColor(Color.argb(200, 255, 255, 255)); // Adjust alpha to
                                                             // change how
                                                             // quickly the
                                                             // image fades
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
    }

    protected abstract void onInvalidate();
    protected abstract int onGetWidth();
    protected abstract int onGetHeight();

    /**
     * Links the visualizer to a player
     * 
     * @param player - MediaPlayer instance to link to
     */
    public final void link(int audioSessionId)
    {
        if (mVisualizer != null && audioSessionId != mAudioSessionId) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }

        Log.i(TAG, "session=" + audioSessionId);
        mAudioSessionId = audioSessionId;

        if (mVisualizer == null) {

            // Create the Visualizer object and attach it to our media player.
            try {
                mVisualizer = new Visualizer(audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Error enabling visualizer!", e);
                return;
            }
            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

            // Pass through Visualizer data to VisualizerView
            Visualizer.OnDataCaptureListener captureListener = new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                        int samplingRate){}
                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] bytes,
                        int samplingRate){
                    updateVisualizerFFT(bytes);
                }
            };

            mVisualizer.setDataCaptureListener(captureListener,
                    (int) (Visualizer.getMaxCaptureRate() * 0.75), true, true);

        }
        mVisualizer.setEnabled(true);
    }

    public final void unlink() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    public final void addRenderer(Renderer renderer) {
        mRenderer = renderer;
    }

    public final void invalidate() {
        onInvalidate();
    }

    public final int getWidth() {
        return onGetWidth();
    }

    public final int getHeight() {
        return onGetHeight();
    }

    public void setDrawingEnabled(boolean draw) {
        mDrawingEnabled = draw;
    }

    /**
     * Call to release the resources used by VisualizerView. Like with the
     * MediaPlayer it is good practice to call this method
     */
    public final void release() {
        mVisualizer.release();
    }

    /**
     * Pass FFT data to the visualizer. Typically this will be obtained from the
     * Android Visualizer.OnDataCaptureListener call back. See
     * {@link Visualizer.OnDataCaptureListener#onFftDataCapture }
     * 
     * @param bytes
     */
    public void updateVisualizerFFT(byte[] bytes) {
        mFFTBytes = bytes;
        mFftData = bytes;
        invalidate();
    }

    /**
     * Call this to make the visualizer flash. Useful for flashing at the start
     * of a song/loop etc...
     */
    public void flash() {
        mFlash = true;
        invalidate();
    }

    public void onDraw(Canvas canvas) {

        // Create canvas once we're ready to draw
        mRect.set(0, 0, getWidth(), getHeight());

        if (mCanvasBitmap == null)
        {
            mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(),
                    Config.ARGB_8888);
        }
        if (mCanvas == null)
        {
            mCanvas = new Canvas(mCanvasBitmap);
        }

        if (mFFTBytes != null && mRenderer != null) {
                mRenderer.render(mCanvas, mFftData, mRect);
        }

        if (mDrawingEnabled) {
            // Fade out old contents
            mCanvas.drawPaint(mFadePaint);

            if (mFlash)
            {
                mFlash = false;
                mCanvas.drawPaint(mFlashPaint);
            }
            canvas.drawBitmap(mCanvasBitmap, mMatrix, null);
        }
    }
}