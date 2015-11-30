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
 * Fires actions based on detected motion. calculates long and short swipes
 * as well as double taps. User can set "long swipe thresholds" for custom
 * long swipe definition. 
 *
 */

package com.android.internal.navigation.fling;


import java.util.HashSet;
import java.util.Set;

import com.android.internal.navigation.fling.FlingGestureDetector.OnGestureListener;
import com.android.internal.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.ActionConstants.Fling;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;

public class FlingGestureHandler implements OnGestureListener, SmartObservable {

    public interface Swipeable {
        public boolean onDoubleTapEnabled();
        public void onSingleLeftPress();
        public void onSingleRightPress();
        public void onDoubleLeftTap();
        public void onDoubleRightTap();
        public void onLongLeftPress();
        public void onLongRightPress();
        public void onShortLeftSwipe();
        public void onLongLeftSwipe();
        public void onShortRightSwipe();
        public void onLongRightSwipe();
    }

    private static Set<Uri> sUris = new HashSet<Uri>();    
    static {
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGSWIPE_THRESHOLD_LEFT_LAND));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGSWIPE_THRESHOLD_RIGHT_LAND));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGSWIPE_THRESHOLD_LEFT_PORT));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGSWIPE_THRESHOLD_RIGHT_PORT));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGSWIPE_THRESHOLD_UP_LAND));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.FLING_LONGSWIPE_THRESHOLD_DOWN_LAND));
    }

    // AOSP DT timeout feels a bit slow on nx
    private static final int DT_TIMEOUT = ViewConfiguration.getDoubleTapTimeout() - 100;

    // in-house double tap logic
    private Handler mHandler = new Handler();
    private boolean isDoubleTapPending;
    private boolean wasConsumed;

    // long swipe thresholds
    private float mLeftLandDef;
    private float mRightLandDef;
    private float mLeftPortDef;
    private float mRightPortDef;
    private float mUpVertDef;
    private float mDownVertDef;

    // long swipe thresholds
    private float mLeftLand;
    private float mRightLand;
    private float mLeftPort;
    private float mRightPort;
    private float mUpVert;
    private float mDownVert;

    // pass motion events to listener
    private Swipeable mReceiver;
    private Context mContext;

    // for width/height logic
    private View mHost;
    private boolean mVertical;

    private Runnable mDoubleTapLeftTimeout = new Runnable() {
        @Override
        public void run() {
            wasConsumed = false;
            isDoubleTapPending = false;
            mReceiver.onSingleLeftPress();
        }
    };

    private Runnable mDoubleTapRightTimeout = new Runnable() {
        @Override
        public void run() {
            wasConsumed = false;
            isDoubleTapPending = false;
            mReceiver.onSingleRightPress();
        }
    };

    public FlingGestureHandler(Context context, Swipeable swiper, View host, Bundle configs) {
        mContext = context;
        mReceiver = swiper;
        mHost = host;
        loadConfigs(configs);
        updateSettings();
    }

    private void loadConfigs(Bundle configs) {
        mLeftLandDef = configs.getFloat(Fling.CONFIG_FlingLongSwipeLandscapeLeft);
        mRightLandDef = configs.getFloat(Fling.CONFIG_FlingLongSwipeLandscapeRight);
        mLeftPortDef = configs.getFloat(Fling.CONFIG_FlingLongSwipePortraitLeft);
        mRightPortDef = configs.getFloat(Fling.CONFIG_FlingLongSwipePortraitRight);
        mUpVertDef = configs.getFloat(Fling.CONFIG_FlingLongSwipeVerticalUp);
        mDownVertDef = configs.getFloat(Fling.CONFIG_FlingLongSwipeVerticalDown);
    }

    // special case: double tap for screen off we never capture up motion event
    // maybe use broadcast receiver instead on depending on host
    public void onScreenStateChanged(boolean screeOn) {
        wasConsumed = false;
    }

    public void setOnSwipeListener(Swipeable swiper) {
        if (swiper != null) {
            mReceiver = swiper;
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (isDoubleTapPending) {
            boolean isRight = isRightSide(e.getX(), e.getY());
            isDoubleTapPending = false;
            wasConsumed = true;
            mHandler.removeCallbacks(mDoubleTapLeftTimeout);
            mHandler.removeCallbacks(mDoubleTapRightTimeout);
            if (isRight) {
                mReceiver.onDoubleRightTap();
            } else {
                mReceiver.onDoubleLeftTap();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        boolean isRight = isRightSide(e.getX(), e.getY());
        if (mReceiver.onDoubleTapEnabled()) {
            if (wasConsumed) {
                wasConsumed = false;
                return true;
            }
            isDoubleTapPending = true;
            if (isRight) {
                mHandler.postDelayed(mDoubleTapRightTimeout, DT_TIMEOUT);
            } else {
                mHandler.postDelayed(mDoubleTapLeftTimeout, DT_TIMEOUT);
            }
        } else {
            if (isRight) {
                mReceiver.onSingleRightPress();
            } else {
                mReceiver.onSingleLeftPress();
            }
        }
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        boolean isRight = isRightSide(e.getX(), e.getY());
        if (isRight) {
            mReceiver.onLongRightPress();
        } else {
            mReceiver.onLongLeftPress();
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {

        final boolean isVertical = mVertical;
        final boolean isLandscape = DUActionUtils.isLandscape(mContext);

        final float deltaParallel = isVertical ? e2.getY() - e1.getY() : e2
                .getX() - e1.getX();

        boolean isLongSwipe = isLongSwipe(mHost.getWidth(), mHost.getHeight(),
                deltaParallel, isVertical, isLandscape);

        if (deltaParallel > 0) {
            if (isVertical) {
                if (isLongSwipe) {
                    mReceiver.onLongLeftSwipe();
                } else {
                    mReceiver.onShortLeftSwipe();
                }
            } else {
                if (isLongSwipe) {
                    mReceiver.onLongRightSwipe();
                } else {
                    mReceiver.onShortRightSwipe();
                }
            }
        } else {
            if (isVertical) {
                if (isLongSwipe) {
                    mReceiver.onLongRightSwipe();
                } else {
                    mReceiver.onShortRightSwipe();
                }
            } else {
                if (isLongSwipe) {
                    mReceiver.onLongLeftSwipe();
                } else {
                    mReceiver.onShortLeftSwipe();
                }
            }
        }
        return true;
    }

    public void setIsVertical(boolean isVertical) {
        mVertical = isVertical;
    }

    private boolean isRightSide(float x, float y) {
        float length = mVertical ? mHost.getHeight() : mHost.getWidth();
        float pos = mVertical ? y : x;
        length /= 2;
        return mVertical ? pos < length : pos > length;
    }

    private boolean isLongSwipe(float width, float height, float distance,
            boolean isVertical, boolean isLandscape) {
        float size;
        float longPressThreshold;

        // determine correct bar dimensions to calculate against
        if (isLandscape) {
            if (isVertical) {
                size = height;
            } else {
                size = width;
            }
        } else {
            size = width;
        }
        // determine right or left
        // greater than zero is either right or up
        if (distance > 0) {
            if (isLandscape) {
                // must be landscape for vertical bar
                if (isVertical) {
                    // landscape with vertical bar
                    longPressThreshold = mUpVert;
                } else {
                    // landscape horizontal bar
                    longPressThreshold = mRightLand;
                }
            } else {
                // portrait: can't have vertical navbar
                longPressThreshold = mRightPort;
            }
        } else {
            // left or down
            if (isLandscape) {
                // must be landscape for vertical bar
                if (isVertical) {
                    // landscape with vertical bar
                    longPressThreshold = mDownVert;
                } else {
                    // landscape horizontal bar
                    longPressThreshold = mLeftLand;
                }
            } else {
                // portrait: can't have vertical navbar
                longPressThreshold = mLeftPort;
            }
        }
        return Math.abs(distance) > (size * longPressThreshold);
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mLeftLand = Settings.Secure.getFloatForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_LEFT_LAND,
                mLeftLandDef, UserHandle.USER_CURRENT);

        mRightLand = Settings.Secure.getFloatForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_RIGHT_LAND,
                mRightLandDef, UserHandle.USER_CURRENT);

        mLeftPort = Settings.Secure.getFloatForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_LEFT_PORT,
                mLeftPortDef, UserHandle.USER_CURRENT);

        mRightPort = Settings.Secure.getFloatForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_RIGHT_PORT,
                mRightPortDef, UserHandle.USER_CURRENT);

        mUpVert = Settings.Secure.getFloatForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_UP_LAND,
                mUpVertDef, UserHandle.USER_CURRENT);

        mDownVert = Settings.Secure.getFloatForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_DOWN_LAND,
                mDownVertDef, UserHandle.USER_CURRENT);
    }

    @Override
    public Set<Uri> onGetUris() {
        return sUris;
    }

    @Override
    public void onChange(Uri uri) {
        updateSettings();        
    }
}
