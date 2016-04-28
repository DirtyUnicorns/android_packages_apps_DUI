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
 * Control class for Pulse media fuctions and visualizer state management
 * Basic logic flow inspired by Roman Birg aka romanbb in his Equalizer
 * tile produced for Cyanogenmod
 *
 */

package com.android.systemui.navigation.pulse;

import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.navigation.pulse.PulseFftValidator;
import com.android.systemui.navigation.pulse.PulseRenderer;
import com.android.systemui.navigation.pulse.PulseVisualizer;
import com.android.systemui.navigation.pulse.StreamValidator;
import com.android.systemui.navigation.utils.ColorAnimator;
import com.android.systemui.navigation.utils.MediaMonitor;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.IAudioService;
import android.net.Uri;
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
    private MediaMonitor mMediaMonitor;
    private AudioManager mAudioManager;
    private PulseVisualizer mVisualizer;
    private Renderer mRenderer;
    private StreamValidator mValidator;
    private PulseObserver mPulseObserver;
    private PulseColorAnimator mLavaLamp;
    private PulseSettingsObserver mSettingsObserver;
    private boolean mKeyguardShowing;
    private boolean mLinked;
    private boolean mPowerSaveModeEnabled;
    private boolean mPulseEnabled;
    private boolean mScreenOn;
    private boolean mMusicStreamMuted;

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
            } else if (AudioManager.STREAM_MUTE_CHANGED_ACTION.equals(intent.getAction())
                    || (AudioManager.VOLUME_CHANGED_ACTION.equals(intent.getAction()))) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_MUSIC) {
                    boolean muted = isMusicMuted(streamType);
                    if (mMusicStreamMuted != muted) {
                        mMusicStreamMuted = muted;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                doLinkage();
                            }
                        });
                    }
                }
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
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIMEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIV), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_FILLED_BLOCK_SIZE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_EMPTY_BLOCK_SIZE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_ENABLED))) {
                updateEnabled();
                doLinkage();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIMEN))
                    || uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIV))
                    || uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_FILLED_BLOCK_SIZE))
                    || uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_EMPTY_BLOCK_SIZE))
                    ||uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR))) {
                resetVisualizer();
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

    public PulseController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mSettingsObserver = new PulseSettingsObserver(handler);
        mSettingsObserver.updateEnabled();
        mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        mMusicStreamMuted = isMusicMuted(AudioManager.STREAM_MUSIC);

        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING);
        filter.addAction(AudioManager.STREAM_MUTE_CHANGED_ACTION);
        filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        context.registerReceiver(mReceiver, filter);

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
        initVisualizer();
    }

    public void initVisualizer() {
        if (mPulseObserver == null) {
            return;
        }
        mValidator = new PulseFftValidator();
        mValidator.addCallbacks(mStreamValidatorCallbacks);
        mLavaLamp = new PulseColorAnimator();
        mRenderer = new PulseRenderer(mContext, mValidator);
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

    private boolean isMusicMuted(int streamType) {
        return streamType == AudioManager.STREAM_MUSIC &&
                (mAudioManager.isStreamMute(streamType) ||
                mAudioManager.getStreamVolume(streamType) == 0);
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
                || mPowerSaveModeEnabled
                || mMusicStreamMuted;
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
                && !mKeyguardShowing
                && !mMusicStreamMuted;
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
        doLinkVisualizer(false);
    }

    private void doLinkVisualizer(boolean force) {
        if (mVisualizer != null) {
            if (!mLinked || force) {
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

    public void resetVisualizer() {
        final boolean wasLinked = mLinked;
        if (wasLinked) {
            doSilentUnlinkVisualizer();
        }
        initVisualizer();
        if (wasLinked) {
            doLinkVisualizer(true);
        }
    }
}
