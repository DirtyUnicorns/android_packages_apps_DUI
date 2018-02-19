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

import com.android.systemui.navigation.fling.FlingGestureDetector.OnGestureListener;
import com.android.systemui.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.utils.du.DUActionUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;

public class FlingGestureHandler implements OnGestureListener, SmartObservable {
/*
 * Callback for listeners that are interested in receiving Fling taps and gestures
 *
 */
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

        public void onUpRightSwipe();

        public void onUpLeftSwipe();

        public void onDownPreloadRecents(boolean isRight);

        public void onScrollPreloadRecents();

        public void onCancelPreloadRecents();
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
    private boolean mIsDoubleTapPending;
    private boolean mWasConsumed;

    // long swipe thresholds from user settings or default config
    //
    // left and right thresholds for tablets when device orientation is landscape
    private float mLeftLand;
    private float mRightLand;
    // left and right thresholds for all devices when device orientation is portrait
    private float mLeftPort;
    private float mRightPort;
    // up and down thresholds for phones when device orientation is landscape and navbar is vertical
    private float mUpVert;
    private float mDownVert;

    // pass Fling events to listener
    private Swipeable mReceiver;
    // swipe analysis and state
    private final FlingSwipe mFlingSwipe = new FlingSwipe();
    private Context mContext;

    // for width/height logic
    private View mHost;
    // is navbar in "left in landscape" mode (vertical bar on left instead of right)
    private boolean mLeftInLandscape;
    private final boolean mIsTablet;

    private Runnable mDoubleTapLeftTimeout = new Runnable() {
        @Override
        public void run() {
            mWasConsumed = false;
            mIsDoubleTapPending = false;
            mReceiver.onSingleLeftPress();
        }
    };

    private Runnable mDoubleTapRightTimeout = new Runnable() {
        @Override
        public void run() {
            mWasConsumed = false;
            mIsDoubleTapPending = false;
            mReceiver.onSingleRightPress();
        }
    };

    public FlingGestureHandler(Context context, Swipeable swiper, View host, boolean isTablet) {
        mContext = context;
        mReceiver = swiper;
        mHost = host;
        mIsTablet = isTablet;
        updateSettings();
    }

    // special case: double tap for screen off we never capture up motion event
    // maybe use broadcast receiver instead on depending on host
    public void onScreenStateChanged(boolean screeOn) {
        mWasConsumed = false;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mLeftInLandscape = leftInLandscape;
    }

    public void setOnSwipeListener(Swipeable swiper) {
        if (swiper != null) {
            mReceiver = swiper;
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        boolean isRight = isRightSide(e.getX(), e.getY());
        if (mIsDoubleTapPending) {
            mIsDoubleTapPending = false;
            mWasConsumed = true;
            mHandler.removeCallbacks(mDoubleTapLeftTimeout);
            mHandler.removeCallbacks(mDoubleTapRightTimeout);
            if (isRight) {
                mReceiver.onDoubleRightTap();
            } else {
                mReceiver.onDoubleLeftTap();
            }
            return true;
        }
        mReceiver.onDownPreloadRecents(isRight);
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
            if (mWasConsumed) {
                mWasConsumed = false;
                return true;
            }
            mIsDoubleTapPending = true;
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
    public boolean onFirstScroll() {
        mReceiver.onScrollPreloadRecents();
        return false;
    }

    @Override
    public boolean onCancel() {
        mReceiver.onCancelPreloadRecents();
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
        if (e1 == null) return true;

        mFlingSwipe.process(e1, e2);

        if (mFlingSwipe.swipeDirection == SwipeDirection.UP) {
            if (mFlingSwipe.isSwipeOnRight()) {
                mReceiver.onUpRightSwipe();
            } else {
                mReceiver.onUpLeftSwipe();
            }
        } else if (mFlingSwipe.swipeDirection == SwipeDirection.LEFT) {
            if (mFlingSwipe.isThisLongSwipe()) {
                mReceiver.onLongLeftSwipe();
            } else {
                mReceiver.onShortLeftSwipe();
            }
        } else if (mFlingSwipe.swipeDirection == SwipeDirection.RIGHT) {
            if (mFlingSwipe.isThisLongSwipe()) {
                mReceiver.onLongRightSwipe();
            } else {
                mReceiver.onShortRightSwipe();
            }
        }
        return true;
    }

    private boolean isHorizontal() {
        return mHost.getWidth() > mHost.getHeight();
    }

    /*
     * Used for tap and up/down swipe events
     * NOTE: when bar is vertical, "right" side
     * refers to top half of bar and "left" refers
     * to the bottom half
     */
    private boolean isRightSide(float x, float y) {
        final boolean isVertical = !isHorizontal();
        float length = isVertical ? mHost.getHeight() : mHost.getWidth();
        float pos = isVertical ? y : x;
        length /= 2;
        return isVertical ? pos < length : pos > length;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        Resources res = mContext.getResources();

        mLeftLand = (float) (Settings.Secure.getIntForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_LEFT_LAND,
                25, UserHandle.USER_CURRENT) * 0.01f);

        mRightLand = (float) (Settings.Secure.getIntForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_RIGHT_LAND,
                25, UserHandle.USER_CURRENT) * 0.01f);

        mLeftPort = (float) (Settings.Secure.getIntForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_LEFT_PORT,
                mIsTablet ? 30 : 40, UserHandle.USER_CURRENT) * 0.01f);

        mRightPort = (float) (Settings.Secure.getIntForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_RIGHT_PORT,
                mIsTablet ? 30 : 40, UserHandle.USER_CURRENT) * 0.01f);

        mUpVert = (float) (Settings.Secure.getIntForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_UP_LAND,
                40, UserHandle.USER_CURRENT) * 0.01f);

        mDownVert = (float) (Settings.Secure.getIntForUser(
                resolver, Settings.Secure.FLING_LONGSWIPE_THRESHOLD_DOWN_LAND,
                40, UserHandle.USER_CURRENT) * 0.01f);
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
        RIGHT,
        UP
    }

    // This class will help despagettify motion handling, maybe ;p
    private class FlingSwipe {
        // minimum amount of bar to swipe to validate a vertical swipe
        private static final float VERTICAL_SWIPE_THRESHOLD = 0.80f;
        // Maximum swipe angle before fall over into non-vertical swipes
        private static final double VERTICAL_SWIPE_DEGREES = 30.0d;

        // direction of swipe, either actual or treated as such (vertical bar)
        private SwipeDirection swipeDirection;

        // was vertical swipe on right/left side
        private boolean mVerticalSwipeOnRight;

        // Between -1.0 to 1.0  - percent of bar swiped - however we expose only absolute value -
        // direction should be checked with getSwipeDirection
        private float horizontalSwipePercent;

        // Between -1.0 to 1.0  - percent of bar swiped - however we expose only absolute value -
        // direction should be checked with getSwipeDirection
        private float verticalSwipePercent;

        // the default or user set long swipe threshold for this long swipe event
        private float longSwipeThreshold;

        public FlingSwipe(){}

        void process(MotionEvent start, MotionEvent end) {
            // bar and device states
            final boolean isNavbarHorizontal = isHorizontal();
            final boolean isLandscape = DUActionUtils.isLandscape(mContext);
            final float xStart = start.getX();
            final float yStart = start.getY();
            final float xDist = end.getX() - xStart;
            final float yDist = end.getY() - yStart;

            // how much bar long side did the swipe cover
            horizontalSwipePercent = isNavbarHorizontal ? xDist / mHost.getWidth() :
                    yDist / mHost.getHeight();
            // how much bar short side was covered
            verticalSwipePercent = isNavbarHorizontal ? yDist / mHost.getHeight() :
                xDist / mHost.getWidth();
            // which side was the swipe started from
            mVerticalSwipeOnRight = isRightSide(xStart, yStart);

            final boolean isVerticalSwipe = validateVerticalSwipe(isNavbarHorizontal, isLandscape, xDist, yDist);
            if(!isVerticalSwipe) {
                // normal side to side flinging action
                // first check landscape conditions
                if (isLandscape) {
                    // is bar horizontal (tablet)
                    if (isNavbarHorizontal) {
                        if (horizontalSwipePercent >= 0) {
                            swipeDirection = SwipeDirection.RIGHT;
                            longSwipeThreshold = mRightLand;
                        } else {
                            swipeDirection = SwipeDirection.LEFT;
                            longSwipeThreshold = mLeftLand;
                        }
                    } else {
                        // vertical bar on phones
                        if (horizontalSwipePercent >= 0) {
                            swipeDirection = SwipeDirection.LEFT;
                            longSwipeThreshold = mDownVert;
                        } else {
                            swipeDirection = SwipeDirection.RIGHT;
                            longSwipeThreshold = mUpVert;
                        }
                    }
                } else {
                    // portrait orientation
                    if (horizontalSwipePercent >= 0) {
                        swipeDirection = SwipeDirection.RIGHT;
                        longSwipeThreshold = mRightPort;
                    } else {
                        swipeDirection = SwipeDirection.LEFT;
                        longSwipeThreshold = mLeftPort;
                    }
                }
            }
        }

        private boolean validateVerticalSwipe(boolean isHorizontal, boolean isLandscape, float xDistance, float yDistance) {
            // if we don't meet minimum threshold and we are too sloppy with our vertical swipe
            // return false to continue processing
            if (Math.abs(verticalSwipePercent) < VERTICAL_SWIPE_THRESHOLD) {
                return false;
            }
            // calculate the angle of the swipe. If the angle exceeds 30 degrees, it is an invalid
            // vertical swipe and the event will be handled as a long or short swipe
            double degrees = Math.abs(Math.toDegrees(Math.atan(((isHorizontal ? xDistance : yDistance) / (isHorizontal ? yDistance : xDistance)))));
            if(DEBUG) {
                Log.e(TAG, "Validating a vertical swipe of " + String.valueOf(degrees) + " degrees");
            }
            if (degrees > VERTICAL_SWIPE_DEGREES) {
                return false;
            }
            // conditions which must be met for a valid vertical swipe
            if ((isHorizontal && verticalSwipePercent <= 0)  // horizontal and direction is up
                    || (!isHorizontal && mLeftInLandscape && verticalSwipePercent >= 0) // vertical bar on left and direction is right
                    || (!isHorizontal && !mLeftInLandscape && verticalSwipePercent <= 0)) { // vertical bar on right and direction is left
                swipeDirection = SwipeDirection.UP;
                return true;
            }
            return false;
        }

        public boolean isThisLongSwipe() {
            return Math.abs(horizontalSwipePercent) > longSwipeThreshold;
        }

        public boolean isSwipeOnRight() {
            return mVerticalSwipeOnRight;
        }
    }
}
