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
 * Control class for NX media fuctions. Basic logic flow inspired by
 * Roman Burg aka romanbb in his Equalizer tile produced for Cyanogenmod
 *
 */

package com.android.internal.navigation.fling.pulse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.internal.navigation.fling.FlingModule;
import com.android.internal.navigation.fling.FlingModule.Callbacks;
import com.android.internal.navigation.utils.LavaLamp;
import com.android.internal.navigation.utils.MediaMonitor;
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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;

public class PulseController implements FlingModule {
    private static final int MSG_START_LAVALAMP = 45;
    private static final int MSG_STOP_LAVALAMP = 46;
    private static final int MSG_REFRESH_STATE = 47;
    private static final int MSG_INVALID_STREAM = 48;

    private Context mContext;
    private PulseHandler mHandler;
    private MediaMonitor mMediaMonitor;
    private PulseVisualizer mVisualizer;
    private PulseRenderer mRenderer;
    private FlingModule.Callbacks mCallback;
    private boolean mKeyguardShowing;
    private boolean mLinked;
    private boolean mPowerSaveModeEnabled;
    private boolean mPulseEnabled;
    private boolean mScreenOn;

    private class PulseHandler extends Handler {

        public PulseHandler() {
            super(Looper.getMainLooper());
        }

        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_START_LAVALAMP:
                    mRenderer.startLavaLamp();
                    break;
                case MSG_STOP_LAVALAMP:
                    mRenderer.stopLavaLamp();
                    break;
                case MSG_REFRESH_STATE:
                    removeCallbacks(mRefreshStateRunnable);
                    postDelayed(mRefreshStateRunnable, 250);
                    break;
                case MSG_INVALID_STREAM:
                    AsyncTask.execute(mUnlinkVisualizer);
                    break;
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                if (mPowerSaveModeEnabled) {
                    AsyncTask.execute(mUnlinkVisualizer);
                } else {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            doLinkage();
                        }
                    });
                }
            }
        }
    };

    public PulseController(Context context, FlingModule.Callbacks callback) {
        mContext = context;
        mCallback = callback;
        mHandler = new PulseHandler();
        mVisualizer = new PulseVisualizer(callback);
        mRenderer = new PulseRenderer(16, mHandler);
        mVisualizer.addRenderer(mRenderer);

        mMediaMonitor = new MediaMonitor(mContext) {
            @Override
            public void onPlayStateChanged(boolean playing) {
                doLinkage();
            }
        };
    }

    public void setKeyguardShowing(boolean showing) {
        if (mKeyguardShowing != showing) {
            mKeyguardShowing = showing;
            doLinkage();
        }
    }

    public void notifyScreenOn(boolean screenOn) {
        if (mScreenOn != screenOn) {
            mScreenOn = screenOn;
            doLinkage();
        }
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        mVisualizer.onSizeChanged(w, h, oldw, oldh);
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
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mPowerSaveModeEnabled = pm.isPowerSaveMode();
            mMediaMonitor.setListening(true);
        } else {
            mMediaMonitor.setListening(false);
            mContext.unregisterReceiver(mReceiver);
        }
        doLinkage();
    }

    public boolean isPulseEnabled() {
        return mPulseEnabled;
    }

    public boolean shouldDrawPulse() {
        return mLinked && mRenderer.shouldDraw();
    }

    public void setLavaAnimationTime(int time) {
        mRenderer.setLavaAnimationTime(time);
    }

    public void setLavaLampEnabled(boolean enabled) {
        mRenderer.setLavaLampEnabled(enabled);
    }

    private void doLinkage() {
        if (mKeyguardShowing || !mScreenOn || !mPulseEnabled) {
            if (mLinked) {
                // explicitly unlink
                AsyncTask.execute(mUnlinkVisualizer);
                mHandler.removeCallbacks(mRefreshStateRunnable);
                mHandler.postDelayed(mRefreshStateRunnable, 250);
            }
        } else {
            if (mMediaMonitor != null
                    && mPulseEnabled
                    && mScreenOn
                    && mMediaMonitor.isAnythingPlaying()
                    && !mLinked
                    && !mPowerSaveModeEnabled
                    && !mKeyguardShowing) {
                AsyncTask.execute(mLinkVisualizer);
            } else if (mLinked) {
                AsyncTask.execute(mUnlinkVisualizer);
                mHandler.removeCallbacks(mRefreshStateRunnable);
                mHandler.postDelayed(mRefreshStateRunnable, 250);
            }
        }
    }

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (!mLinked) {
                    mRenderer.reset();
                    if (mRenderer.isLavaLampEnabled()) {
                        mHandler.obtainMessage(MSG_START_LAVALAMP).sendToTarget();
                    }
                    mVisualizer.resetDrawing();
                    mVisualizer.link(0);
                    mLinked = true;
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
                    if (mRenderer.isLavaLampEnabled()) {
                        mHandler.obtainMessage(MSG_STOP_LAVALAMP).sendToTarget();
                    }
                }
            }
        }
    };

    private final Runnable mRefreshStateRunnable = new Runnable() {
        @Override
        public void run() {
            mCallback.onUpdateState();
            mCallback.onInvalidate();
        }
    };

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

    public void updateRenderColor(int color) {
        mRenderer.setColor(color);
    }
}
