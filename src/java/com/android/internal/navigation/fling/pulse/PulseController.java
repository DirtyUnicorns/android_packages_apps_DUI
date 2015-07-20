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
 * Control class for Pulse media fuctions and visualizer state management
 * Basic logic flow inspired by Roman Burg aka romanbb in his Equalizer
 * tile produced for Cyanogenmod
 *
 */

package com.android.internal.navigation.fling.pulse;

import com.android.internal.navigation.fling.FlingModule;
import com.android.internal.navigation.utils.MediaMonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.media.IAudioService;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

public abstract class PulseController implements FlingModule {
    private static final String TAG = PulseController.class.getSimpleName();

    public static final int STATE_STARTED = 1;
    public static final int STATE_STOPPED = 2;

    private Context mContext;
    private MediaMonitor mMediaMonitor;
    private PulseVisualizer mVisualizer;
    private PulseRenderer mRenderer;
    private FlingModule.Callbacks mCallback;
    private boolean mKeyguardShowing;
    private boolean mLinked;
    private boolean mPowerSaveModeEnabled;
    private boolean mPulseEnabled;
    private boolean mScreenOn;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                mCallback.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        doLinkage();
                    }
                });
            }
        }
    };

    public PulseController(Context context, FlingModule.Callbacks callback) {
        mContext = context;
        mCallback = callback;
        mVisualizer = new PulseVisualizer(callback);
        mRenderer = new PulseRenderer(16) {
            @Override
            public void onRenderStateChanged(int state) {
                if (state == PulseRenderer.STATE_SUCCESS) {
                    final boolean needsPrepare = onPrepareToPulse();
                    if (!needsPrepare) {
                        turnOnPulse();
                    }
                } else if (state == PulseRenderer.STATE_FAILED) {
                    AsyncTask.execute(mUnlinkVisualizer);
                }
            }
        };
        mVisualizer.addRenderer(mRenderer);
        mMediaMonitor = new MediaMonitor(mContext) {
            @Override
            public void onPlayStateChanged(boolean playing) {
                doLinkage();
            }
        };
    }

    /**
     * Pulse has successfully prepared rendering. Let subclasses adjust UI if needed
     * 
     * @return false if no preparation is needed, true if preparation is needed NOTE: if true is
     *         returned, a call to turnOnPulse() is required to start rendering
     */
    public abstract boolean onPrepareToPulse();

    /**
     * Visualizer has started or stopped drawing
     * 
     * @param state STATE_STARTED to indicate drawing, STATE_STOPPED if stopped
     */
    public abstract void onPulseStateChanged(int state);

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

    /**
     * @param enabled Set true to turn on Pulse, false to turn off
     */
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
            setVisualizerLocked(false);
            mMediaMonitor.setListening(false);
            mContext.unregisterReceiver(mReceiver);
        }
        doLinkage();
    }

    /**
     * @return true if Pulse is enabled, false if not
     */
    public boolean isPulseEnabled() {
        return mPulseEnabled;
    }

    /**
     * Current rendering state: There is a visualizer link and the fft stream is validated
     * 
     * @return true if bar elements should be hidden, false if not
     */
    public boolean shouldDrawPulse() {
        return mLinked && mRenderer.shouldDraw();
    }

    /**
     * @param time The time in seconds for animation to traverse the HSV wheel
     */
    public void setLavaAnimationTime(int time) {
        mRenderer.setLavaAnimationTime(time);
    }

    /**
     * @param enabled Set true to enable, false to disable
     */
    public void setLavaLampEnabled(boolean enabled) {
        mRenderer.setLavaLampEnabled(enabled);
    }

    public void turnOnPulse() {
        if (mPulseEnabled && shouldDrawPulse()) {
            mRenderer.startLavaLamp(); // start lava lamp
            // mVisualizer.setDrawingEnabled(true); // enable visualizer drawing
            mCallback.onInvalidate(); // all systems go: start pulsing
            onPulseStateChanged(STATE_STARTED);
        }
    }

    private void setVisualizerLocked(boolean doLock) {
        try {
            IBinder b = ServiceManager.getService(Context.AUDIO_SERVICE);
            IAudioService audioService = IAudioService.Stub.asInterface(b);
            audioService.setVisualizerLocked(doLock);
        } catch (RemoteException e) {
            Log.e(TAG, "Error setting visualizer lock");
        }
    }

    /**
     * if any of these conditions are met, we unlink regardless of any other states
     * 
     * @return true if unlink is required, false if unlinking is not mandatory
     */
    private boolean isUnlinkRequired() {
        return mKeyguardShowing
                || !mScreenOn
                || !mPulseEnabled
                || mPowerSaveModeEnabled;
    }

    /**
     * All of these conditions must be met to allow a visualizer link
     * 
     * @return true if all conditions are met to allow link, false if and conditions are not met
     */
    private boolean isAbleToLink() {
        return mMediaMonitor != null
                && mPulseEnabled
                && mScreenOn
                && mMediaMonitor.isAnythingPlaying()
                && !mLinked
                && !mPowerSaveModeEnabled
                && !mKeyguardShowing;
    }

    private void unlinkAndRefreshState() {
        AsyncTask.execute(mUnlinkVisualizer); // start unlink thread
        mRenderer.stopLavaLamp(); // turn off lava lamp
        // mVisualizer.setDrawingEnabled(false); // disable visualizer drawing
        mCallback.onInvalidate(); // this should clear the bar canvas
        onPulseStateChanged(STATE_STOPPED);// bring back logo or
                                                                               // anything else that
                                                                               // hides for Pulse
    }

    private void doLinkage() {
        if (isUnlinkRequired()) {
            if (mLinked) {
                // explicitly unlink
                unlinkAndRefreshState();
            }
        } else {
            if (isAbleToLink()) {
                AsyncTask.execute(mLinkVisualizer);
            } else if (mLinked) {
                unlinkAndRefreshState();
            }
        }
    }

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (!mLinked) {
                    setVisualizerLocked(true);
                    mRenderer.reset(); // reset validation flags
                    mVisualizer.resetDrawing(); // clear stale bitmaps
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
                    setVisualizerLocked(false);
                    mLinked = false;
                }
            }
        }
    };

    @Override
    public void onDraw(Canvas canvas) {
        if (mLinked) {
            mVisualizer.onDraw(canvas);
        }
    }

    public void updateRenderColor(int color) {
        mRenderer.setColor(color);
    }

    @Override
    public void setCallbacks(Callbacks callbacks) {
        // TODO Auto-generated method stub

    }
}
