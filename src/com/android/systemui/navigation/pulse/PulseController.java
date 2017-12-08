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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Bitmap;
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
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.animation.Animation;

public class PulseController {
    public interface PulseObserver {
        public int getWidth();
        public int getHeight();
        public void postInvalidate();

        // return false to immediately begin Pulse
        // return true to do pre-processing. Implementation MUST
        // call startDrawing() after processing
        public boolean onStartPulse(Animation animatePulseIn);
        public void onStopPulse(Animation animatePulseOut);
    }

    private static final String TAG = PulseController.class.getSimpleName();
    private static final int RENDER_STYLE_LEGACY = 0;
    private static final int RENDER_STYLE_CM = 1;

    private Context mContext;
    private Handler mHandler;
    private AudioManager mAudioManager;
    private Renderer mRenderer;
    private VisualizerStreamHandler mStreamHandler;
    private PulseObserver mPulseObserver;
    private SettingsObserver mSettingsObserver;
    private Bitmap mAlbumArt;
    private int mAlbumArtColor;
    private boolean mPulseEnabled;
    private boolean mKeyguardShowing;
    private boolean mLinked;
    private boolean mPowerSaveModeEnabled;
    private boolean mScreenOn;
    private boolean mMusicStreamMuted;
    private boolean mLeftInLandscape;
    private boolean mScreenPinningEnabled;
    private int mPulseStyle;
    private boolean mIsMediaPlaying;

    public void onReceive(Intent intent) {
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

    private final VisualizerStreamHandler.Listener mStreamListener = new VisualizerStreamHandler.Listener() {
        @Override
        public void onStreamAnalyzed(boolean isValid) {
            if (mRenderer != null) {
                mRenderer.onStreamAnalyzed(isValid);
            }
            if (isValid) {
                if (!mPulseObserver.onStartPulse(null)) {
                    turnOnPulse();
                }
            } else {
                doSilentUnlinkVisualizer();
            }
        }

        @Override
        public void onFFTUpdate(byte[] bytes) {
            if (mRenderer != null) {
                mRenderer.onFFTUpdate(bytes);
            }
        }

        @Override
        public void onWaveFormUpdate(byte[] bytes) {
            if (mRenderer != null) {
                mRenderer.onWaveFormUpdate(bytes);
            }
        }
    };

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void register() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_ENABLED), false, this,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_RENDER_STYLE_URI), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_ENABLED))) {
                updateEnabled();
                doLinkage();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_RENDER_STYLE_URI))) {
                updateRenderMode();
                if (mPulseObserver != null) {
                    loadRenderer();
                }
            }
        }

        void updateSettings() {
            updateEnabled();
            updateRenderMode();
        }

        void updateEnabled() {
            mPulseEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.FLING_PULSE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        }

        void updateRenderMode() {
            mPulseStyle = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.PULSE_RENDER_STYLE_URI, RENDER_STYLE_CM, UserHandle.USER_CURRENT);
        }
    };

    public PulseController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mSettingsObserver = new SettingsObserver(handler);
        mSettingsObserver.updateSettings();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mMusicStreamMuted = isMusicMuted(AudioManager.STREAM_MUSIC);

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mPowerSaveModeEnabled = pm.isPowerSaveMode();

        mSettingsObserver.register();
        mStreamHandler = new VisualizerStreamHandler(mContext, this, mStreamListener);
        mAlbumArtColor = Color.TRANSPARENT;
    }

    public void setPulseObserver(PulseObserver observer) {
        mPulseObserver = observer;
        loadRenderer();
        // why not check for linkage? No need! If this is a bar
        // change, PhoneStatusBar will call notifyInflateFromUser()
        // which calls notifyScreenOn ;D
    }

    private void loadRenderer() {
        if (mPulseObserver == null) {
            return;
        }
        final boolean isRendering = shouldDrawPulse();
        if (isRendering) {
            mStreamHandler.pause();
        }
        if (mRenderer != null) {
            mRenderer.destroy();
            mRenderer = null;
        }
        mRenderer = getRenderer(mPulseObserver);
        mRenderer.setLeftInLandscape(mLeftInLandscape);
        if (isRendering) {
            mRenderer.onStreamAnalyzed(true);
            mStreamHandler.resume();
        }
    }

    public void setScreenPinningState(boolean enabled) {
        mScreenPinningEnabled = enabled;
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
        if (mLeftInLandscape != leftInLandscape) {
            mLeftInLandscape = leftInLandscape;
            if (mRenderer != null) {
                mRenderer.setLeftInLandscape(leftInLandscape);
            }
        }
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mRenderer != null) {
            mRenderer.onSizeChanged(w, h, oldw, oldh);
        }
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
        return mLinked && mStreamHandler.isValidStream() && mRenderer != null;
    }

    public void turnOnPulse() {
        if (isPulseEnabled() && shouldDrawPulse()) {
            mStreamHandler.resume(); // let bytes hit visualizer
        }
    }

    public void onDraw(Canvas canvas) {
        if (isPulseEnabled() && shouldDrawPulse()) {
            mRenderer.draw(canvas);
        }
    }

    public void doUnlinkVisualizer() {
        if (mStreamHandler != null) {
            if (mLinked) {
                mStreamHandler.unlink();
                setVisualizerLocked(false);
                mLinked = false;
                if (mRenderer != null) {
                    mRenderer.onVisualizerLinkChanged(false);
                }
                if (mPulseObserver != null) {
                    mPulseObserver.postInvalidate();
                    mPulseObserver.onStopPulse(null);
                }
            }
        }
    }

    private Renderer getRenderer(PulseObserver observer) {
        switch (mPulseStyle) {
            case RENDER_STYLE_LEGACY:
                return new FadingBlockRenderer(mContext, mHandler, observer);
            case RENDER_STYLE_CM:
                return new SolidLineRenderer(mContext, mHandler, observer);
            default:
                return new FadingBlockRenderer(mContext, mHandler, observer);
        }
    }

    private boolean isMusicMuted(int streamType) {
        return streamType == AudioManager.STREAM_MUSIC &&
                (mAudioManager.isStreamMute(streamType) ||
                mAudioManager.getStreamVolume(streamType) == 0);
    }

    public static void setVisualizerLocked(boolean doLock) {
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
                || !isPulseEnabled()
                || mPowerSaveModeEnabled
                || mMusicStreamMuted
                || mScreenPinningEnabled;
    }

    /**
     * All of these conditions must be met to allow a visualizer link
     *
     * @return true if all conditions are met to allow link, false if and conditions are not met
     */
    private boolean isAbleToLink() {
        return isPulseEnabled()
                && mScreenOn
                && mIsMediaPlaying
                && !mLinked
                && !mPowerSaveModeEnabled
                && !mKeyguardShowing
                && !mMusicStreamMuted
                && !mScreenPinningEnabled;
    }

    /**
     * Incoming event in which we need to
     * toggle our link state.
     */
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

    /**
     * Invalid media event not providing
     * a data stream to visualizer. Unlink
     * without calling into navbar. Like it
     * never happened
     */
    private void doSilentUnlinkVisualizer() {
        if (mStreamHandler != null) {
            if (mLinked) {
                mStreamHandler.unlink();
                setVisualizerLocked(false);
                mLinked = false;
            }
        }
    }

    /**
     * Link to visualizer after conditions
     * are confirmed
     */
    private void doLinkVisualizer() {
        if (mStreamHandler != null) {
            if (!mLinked) {
                setVisualizerLocked(true);
                mStreamHandler.link(0);
                mLinked = true;
                if (mRenderer != null) {
                    mRenderer.onVisualizerLinkChanged(true);
                }
            }
        }
    }

    public void setMediaPlaying(boolean playing) {
        mIsMediaPlaying = playing;
        doLinkage();
    }
}
