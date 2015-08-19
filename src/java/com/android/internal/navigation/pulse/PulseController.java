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

package com.android.internal.navigation.pulse;

import com.android.internal.navigation.utils.ColorAnimator;
import com.android.internal.navigation.utils.MediaMonitor;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap.Config;
import android.graphics.Matrix;
import android.media.IAudioService;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.animation.Animation;

public class PulseController {
    public interface PulseObserver {
        public int getWidth();
        public int getHeight();
        public void invalidate();

        // return false to immediately begin Pulse
        // return true to do pre-processing. Implementation MUST
        // call startDrawing() after processing
        public boolean onStartPulse(Animation animatePulseIn);
        public void onStopPulse(Animation animatePulseOut);
    }

    private static final String TAG = PulseController.class.getSimpleName();

    private Context mContext;
    private Handler mHandler;
    private Bundle mConfig;
    private MediaMonitor mMediaMonitor;
    private PulseVisualizer mVisualizer;
    private PulseRenderer mRenderer;
    private StreamValidator mValidator;
    private PulseObserver mPulseObserver;
    private PulseColorAnimator mLavaLamp;
    private PulseSettingsObserver mSettingsObserver;
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
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        doLinkage();
                    }
                });
            }
        }
    };

    private final StreamValidator.Callbacks mStreamValidatorCallbacks = new StreamValidator.Callbacks() {
        @Override
        public void onStreamAnalyzed(boolean isValid) {
            if (isValid) {
                if (!mPulseObserver.onStartPulse(null)) {
                    turnOnPulse();
                }
            } else {
                doSilentUnlinkVisualizer();
            }
        }
    };

    private class PulseColorAnimator extends ColorAnimator {
        private boolean mEnabled;

        public PulseColorAnimator() {
            super();
        }

        public void setLavaLampEnabled(boolean enabled) {
            if (mEnabled != enabled) {
                mEnabled = enabled;
                if (mValidator.isValidStream()) {
                    if (enabled) {
                        start();
                    } else {
                        stop();
                    }
                }
            }
        }

        @Override
        public void start() {
            if (mEnabled)
                super.start();
        }
    };

    private class PulseSettingsObserver extends ContentObserver {        
        public PulseSettingsObserver(Handler handler) {
            super(handler);
            register();
        }

        void register() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_SPEED), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_ENABLED))) {
                updateEnabled();
                doLinkage();
            } else {
                update();
            }
        }

        void updateEnabled() {
            ContentResolver resolver = mContext.getContentResolver();
            mPulseEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        }

        void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int color = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
            mRenderer.setColor(color, false);

            boolean doLava = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
            mLavaLamp.setLavaLampEnabled(doLava);

            int time = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_SPEED, ColorAnimator.ANIM_DEF_DURATION,
                    UserHandle.USER_CURRENT);
            mLavaLamp.setAnimationTime(time);
        }
    };

    public PulseController(Context context, Handler handler, Bundle config) {
        mContext = context;
        mHandler = handler;
        mConfig = config;
        mSettingsObserver = new PulseSettingsObserver(handler);
        mSettingsObserver.updateEnabled();

        context.registerReceiver(mReceiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mPowerSaveModeEnabled = pm.isPowerSaveMode();

        mMediaMonitor = new MediaMonitor(mContext) {
            @Override
            public void onPlayStateChanged(boolean playing) {
                doLinkage();
            }
        };
        mMediaMonitor.setListening(true);
    }

    public void setPulseObserver(PulseObserver observer) {
        mPulseObserver = observer;
        mValidator = new PulseFftValidator();
        mValidator.addCallbacks(mStreamValidatorCallbacks);
        mLavaLamp = new PulseColorAnimator();
        mRenderer = new PulseRenderer(mConfig, mValidator);
        mLavaLamp.setColorAnimatorListener(mRenderer);
        mVisualizer = new PulseVisualizer(mPulseObserver);
        mVisualizer.addRenderer(mRenderer);
        mSettingsObserver.update();
    }

    public void removePulseObserver() {
        doUnlinkVisualizer();
    }

    public void setKeyguardShowing(boolean showing) {
        mKeyguardShowing = showing;
        doLinkage();
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        doLinkage();
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mVisualizer.setLeftInLandscape(leftInLandscape);
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
        return mLinked && mValidator.isValidStream();
    }

    public void turnOnPulse() {
        if (mPulseEnabled && shouldDrawPulse()) {
            mLavaLamp.start(); // start lava lamp
            mVisualizer.setDrawingEnabled(true); // enable visualizer drawing
            mPulseObserver.invalidate(); // all systems go: start pulsing
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

    private void doLinkage() {
        if (isUnlinkRequired()) {
            if (mLinked) {
                // explicitly unlink
                doUnlinkVisualizer();
            }
        } else {
            if (isAbleToLink()) {
                doLinkVisualizer();
            } else if (mLinked) {
                doUnlinkVisualizer();
            }
        }
    }

    private void doUnlinkVisualizer() {
        if (mVisualizer != null) {
            if (mLinked) {
                mVisualizer.unlink();
                setVisualizerLocked(false);
                mLinked = false;
                mLavaLamp.stop();
                mPulseObserver.invalidate();
                mPulseObserver.onStopPulse(null);
            }
        }
    }

    private void doSilentUnlinkVisualizer() {
        if (mVisualizer != null) {
            if (mLinked) {
                mVisualizer.unlink();
                setVisualizerLocked(false);
                mLinked = false;
            }
        }
    }

    private void doLinkVisualizer() {
        if (mVisualizer != null) {
            if (!mLinked) {
                setVisualizerLocked(true);
                mValidator.reset(); // reset validation flags
                mVisualizer.resetDrawing(); // clear stale bitmaps
                mVisualizer.setDrawingEnabled(false);
                mVisualizer.link(0);
                mLinked = true;
            }
        }
    }

    public void onDraw(Canvas canvas) {
        if (mLinked) {
            mVisualizer.onDraw(canvas);
        }
    }
}
