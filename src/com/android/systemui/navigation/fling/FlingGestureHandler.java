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
 * Fires actions based on detected motion. calculates long and short swipes
 * as well as double taps. User can set "long swipe thresholds" for custom
 * long swipe definition. 
 *
 */

package com.android.systemui.navigation.fling;

import java.util.HashSet;
import java.util.Set;

import com.android.systemui.R;
import com.android.systemui.navigation.fling.FlingGestureDetector.OnGestureListener;
import com.android.systemui.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.DUActionUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
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

    private static String TAG = "FlingGestureHandler";
    private static boolean DEBUG = false;

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

    public FlingGestureHandler(Context context, Swipeable swiper, View host) {
        mContext = context;
        mReceiver = swiper;
        mHost = host;
        mLeftLandDef = context.getResources().getFloat(R.dimen.config_FlingLongSwipeLandscapeLeft);
        mRightLandDef = context.getResources()
                .getFloat(R.dimen.config_FlingLongSwipeLandscapeRight);
        mLeftPortDef = context.getResources().getFloat(R.dimen.config_FlingLongSwipePortraitLeft);
        mRightPortDef = context.getResources().getFloat(R.dimen.config_FlingLongSwipePortraitRight);
        mUpVertDef = context.getResources().getFloat(R.dimen.config_FlingLongSwipeVerticalUp);
        mDownVertDef = context.getResources().getFloat(R.dimen.config_FlingLongSwipeVerticalDown);
        updateSettings();
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

        if (DEBUG)
            Log.d(TAG,
                    String.format("(%f,%f) -> (%f,%f)", e1.getX(), e1.getY(), e2.getX(), e2.getY()));

        FlingSwipe swipe = new FlingSwipe(e1, e2);

        if (swipe.swipeDirection == SwipeDirection.LEFT) {

            if (swipe.isThisLongSwipe()) {
                mReceiver.onLongLeftSwipe();
            } else {
                mReceiver.onShortLeftSwipe();
            }

        } else {

            if (swipe.isThisLongSwipe()) {
                mReceiver.onLongRightSwipe();
            } else {
                mReceiver.onShortRightSwipe();
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

    private enum SwipeDirection {
        LEFT,
        RIGHT
    }

    // This class will help despagettify motion handling
    private class FlingSwipe {

        private final SwipeDirection swipeDirection;

        // Between -1.0 to 1.0 - percent of bar swiped - however we explose only absolute value -
        // direction should be checked with getSwipeDirection
        private final float horizontalSwipeLength;

        // Not used (for now at least) - I thought we can detect swipe outside of navbar, but we
        // can't :-(
        private final float verticalSwipeLength;

        private final float longSwipeThreshold;

        public FlingSwipe(MotionEvent start, MotionEvent end) {

            boolean isNavbarHorizontal = mHost.getWidth() > mHost.getHeight();

            float x1 = start.getX();
            float y1 = start.getY();

            float x2 = end.getX();
            float y2 = end.getY();

            // Right -> Up;Left -> Down
            horizontalSwipeLength = isNavbarHorizontal ? (x2 - x1) / mHost.getWidth() :
                    (y1 - y2) / mHost.getHeight();

            verticalSwipeLength = isNavbarHorizontal ? (y2 - y1) / mHost.getWidth() :
                    (x2 - x1) / mHost.getHeight();

            // Tablets have "isNavbarHorizontal" always true
            if (horizontalSwipeLength >= 0) {
                swipeDirection = SwipeDirection.RIGHT;

                if (isNavbarHorizontal) {
                    longSwipeThreshold = DUActionUtils.isNormalScreen() ? mRightPort : mRightLand;
                } else {
                    longSwipeThreshold = mUpVert;
                }

            } else {
                swipeDirection = SwipeDirection.LEFT;

                if (isNavbarHorizontal) {
                    longSwipeThreshold = DUActionUtils.isNormalScreen() ? mLeftPort : mLeftLand;
                } else {
                    longSwipeThreshold = mDownVert;
                }

            }

            if (DEBUG)
                Log.d("FlingGestureHandler:", String.format("Direction: %s, " +
                        "horizontal length: %f, vertical length: %f, threshold: %f",
                        swipeDirection.name(), horizontalSwipeLength,
                        verticalSwipeLength, longSwipeThreshold));

        }

        public float getVerticalSwipeLength() {
            return Math.abs(verticalSwipeLength);
        }

        public float getHorizontalSwipeLength() {
            return Math.abs(horizontalSwipeLength);
        }

        public boolean isThisLongSwipe() {
            return getHorizontalSwipeLength() > longSwipeThreshold;
        }

        public SwipeDirection getSwipeDirection() {
            return swipeDirection;
        }

    }

}
