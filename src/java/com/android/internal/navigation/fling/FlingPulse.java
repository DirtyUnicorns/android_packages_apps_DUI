/*
 * Copyright (C) 2014 The TeamEos Project
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
 * Control class for NX media fuctions. Basic logic flow inspired by
 * Roman Burg aka romanbb in his Equalizer tile produced for Cyanogenmod
 *
 */

package com.android.internal.navigation.fling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.BaseVisualizer;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.renderer.Renderer;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.AsyncTask;
import android.os.PowerManager;

public class FlingPulse implements FlingModule,
        MediaSessionManager.OnActiveSessionsChangedListener {

    private Context mContext;
    private Map<MediaSession.Token, CallbackInfo> mCallbacks = new HashMap<>();
    private MediaSessionManager mMediaSessionManager;
    private NxVisualizer mVisualizer;
    private boolean mKeyguardShowing;
    private boolean mLinked;
    private boolean mIsAnythingPlaying;
    private boolean mPowerSaveModeEnabled;
    private boolean mPulseEnabled;
    private FlingModule.Callbacks mCallback;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                checkIfPlaying();
            }
        }
    };

    public FlingPulse(Context context, FlingModule.Callbacks callback) {
        mContext = context;
        mCallback = callback;
        mVisualizer = new NxVisualizer();
        mVisualizer.addRenderer(new BarGraphRenderer(16));
    }

    public void setKeyguardShowing(boolean showing) {
        mKeyguardShowing = showing;
        doLinkage();
    }

    /*
     * we don't need to pass new size as args here we'll capture fresh dimens on
     * callback
     */
    public void onSizeChanged() {
        if (mLinked) {
            mVisualizer.setSizeChanged();
        }
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mVisualizer.setLeftInLandscape(leftInLandscape);
    }

    public void setPulseEnabled(boolean enabled) {
        if (enabled == mPulseEnabled) {
            return;
        }
        mPulseEnabled = enabled;
        if (mPulseEnabled) {
            mMediaSessionManager = (MediaSessionManager)
                    mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mPowerSaveModeEnabled = pm.isPowerSaveMode();
            if (!mPowerSaveModeEnabled) {
                mIsAnythingPlaying = isAnythingPlayingColdCheck();
            }
            mMediaSessionManager.addOnActiveSessionsChangedListener(this, null);
        } else {
            mIsAnythingPlaying = false;
            mMediaSessionManager.removeOnActiveSessionsChangedListener(this);
            for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
                entry.getValue().unregister();
            }
            mCallbacks.clear();
            mContext.unregisterReceiver(mReceiver);
        }
        doLinkage();
    }

    public boolean isPulseEnabled() {
        return mPulseEnabled;
    }

    public boolean shouldDrawPulse() {
        return mLinked;
    }

    private void doLinkage() {
        if (mKeyguardShowing || !mPulseEnabled) {
            if (mLinked) {
                // explicitly unlink
                AsyncTask.execute(mUnlinkVisualizer);
            }
        } else {
            // no keyguard, relink if there's something playing
            if (mIsAnythingPlaying && !mLinked && mPulseEnabled) {
                AsyncTask.execute(mLinkVisualizer);
            } else if (mLinked) {
                AsyncTask.execute(mUnlinkVisualizer);
            }
        }
    }

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (!mLinked && !mKeyguardShowing) {
                    mVisualizer.link(0);
                    mLinked = true;
                    mCallback.onUpdateState();
                }
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (mLinked) {
                    mVisualizer.unlink();
                    mLinked = false;
                    mCallback.onUpdateState();
                    mCallback.onInvalidate();
                }
            }
        }
    };

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (!mCallbacks.containsKey(controller.getSessionToken())) {
                    mCallbacks.put(controller.getSessionToken(), new CallbackInfo(controller));
                }
            }
        }
    }

    private boolean isAnythingPlayingColdCheck() {
        List<MediaController> activeSessions = mMediaSessionManager.getActiveSessions(null);
        for (MediaController activeSession : activeSessions) {
            PlaybackState playbackState = activeSession.getPlaybackState();
            if (playbackState != null && playbackState.getState()
                        == PlaybackState.STATE_PLAYING) {
                return true;
            }
        }
        return false;
    }

    private void checkIfPlaying() {
        boolean anythingPlaying = false;
        if (!mPowerSaveModeEnabled) {
            for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
                if (entry.getValue().isPlaying()) {
                    anythingPlaying = true;
                    break;
                }
            }
        }
        if (anythingPlaying != mIsAnythingPlaying) {
            mIsAnythingPlaying = anythingPlaying;
            doLinkage();
        }
    }

    @Override
    public void setCallbacks(Callbacks callbacks) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mLinked) {
            mVisualizer.onDraw(canvas);
        }
    }

    private class CallbackInfo {
        MediaController.Callback mCallback;
        MediaController mController;
        boolean mIsPlaying;

        public CallbackInfo(final MediaController controller) {
            this.mController = controller;
            mCallback = new MediaController.Callback() {
                @Override
                public void onSessionDestroyed() {
                    destroy();
                    checkIfPlaying();
                }

                @Override
                public void onPlaybackStateChanged(@NonNull
                PlaybackState state) {
                    mIsPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                    checkIfPlaying();
                }
            };
            controller.registerCallback(mCallback);
        }

        public boolean isPlaying() {
            return mIsPlaying;
        }

        public void unregister() {
            mController.unregisterCallback(mCallback);
            mIsPlaying = false;
        }

        public void destroy() {
            unregister();
            mCallbacks.remove(mController.getSessionToken());
            mController = null;
            mCallback = null;
        }
    }

    private class NxVisualizer extends BaseVisualizer {
        private Bitmap mRotatedBitmap;
        private boolean mVertical;
        private boolean mSizeChanged;
        private boolean mLeftInLandscape;

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

        public void setSizeChanged() {
            mSizeChanged = true;
        }

        public void setLeftInLandscape(boolean leftInLandscape) {
            if (mLeftInLandscape != leftInLandscape) {
                mLeftInLandscape = leftInLandscape;
                mSizeChanged = true;
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
            if (isVertical != mVertical || mSizeChanged) {
                mVertical = isVertical;
                mSizeChanged = false;
                mCanvasBitmap = null;
                mCanvas = null;
            }

            // Create canvas once we're ready to draw
            // the renderers don't like painting vertically
            // if vertical, create a horizontal canvas based on flipped current
            // dimensions
            // let renders paint, the rotate the bitmap to draw to NX surface
            // we keep both bitmaps as class members to minimize GC
            mRect.set(0, 0, isVertical ? sHeight : sWidth, isVertical ? sWidth : sHeight);

            if (mCanvasBitmap == null) {
                mCanvasBitmap = Bitmap.createBitmap(isVertical ? cHeight : cWidth,
                        isVertical ? cWidth : cHeight,
                        Config.ARGB_8888);
            }
            if (mCanvas == null) {
                mCanvas = new Canvas(mCanvasBitmap);
            }

            if (mBytes != null) {
                // Render all audio renderers
                AudioData audioData = new AudioData(mBytes);
                for (Renderer r : mRenderers) {
                    r.render(mCanvas, audioData, mRect);
                }
            }

            if (mFFTBytes != null) {
                // Render all FFT renderers
                FFTData fftData = new FFTData(mFFTBytes);
                for (Renderer r : mRenderers) {
                    r.render(mCanvas, fftData, mRect);
                }
            }

            // Fade out old contents
            mCanvas.drawPaint(mFadePaint);

            if (mFlash) {
                mFlash = false;
                mCanvas.drawPaint(mFlashPaint);
            }

            // if vertical flip our horizontally rendered bitmap
            if (isVertical) {
                Matrix matrix = new Matrix();
                matrix.postRotate(mLeftInLandscape ? 90 : -90);
                mRotatedBitmap = Bitmap.createBitmap(mCanvasBitmap, 0, 0,
                        mCanvasBitmap.getWidth(), mCanvasBitmap.getHeight(),
                        matrix, true);
            }
            canvas.drawBitmap(isVertical ? mRotatedBitmap : mCanvasBitmap, new Matrix(), null);
        }

    }

    private static class BarGraphRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;

        public BarGraphRenderer(int divisions) {
            super();
            mDivisions = divisions;
            mPaint = new Paint();
            mPaint.setStrokeWidth(50f);
            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.argb(188, 255, 255, 255));
        }

        @Override
        public void onRender(Canvas canvas, AudioData data, Rect rect) {
        }

        @Override
        public void onRender(Canvas canvas, FFTData data, Rect rect) {
            for (int i = 0; i < data.bytes.length / mDivisions; i++) {
                mFFTPoints[i * 4] = i * 4 * mDivisions;
                mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                byte rfk = data.bytes[mDivisions * i];
                byte ifk = data.bytes[mDivisions * i + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                int dbValue = (int) (10 * Math.log10(magnitude));

                mFFTPoints[i * 4 + 1] = rect.height();
                mFFTPoints[i * 4 + 3] = rect.height() - (dbValue * 2 - 10);
            }
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }
}
