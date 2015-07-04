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
 * A smooth HSV color wheel animator
 *
 */

package com.android.internal.navigation.utils;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.Animator.AnimatorPauseListener;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Color;

public class LavaLamp implements AnimatorListener, AnimatorUpdateListener, AnimatorPauseListener {
    public interface LavaListener {
        public int onGetInitialColor();
        public void onStartLava();
        public void onStopLava(int lastColor);
        public void onColorUpdated(int color);
    }

    public static final int ANIM_DEF_DURATION = 10; // seconds

    private static final String RED = "#ffff8080";
    private static final String BLUE = "#ff8080ff";
    private final float[] from = new float[3], to = new float[3], hsv = new float[3];
    private ValueAnimator mColorAnim = ValueAnimator.ofFloat(0, 1);
    private LavaListener mListener;
    private int mAnimSeconds = ANIM_DEF_DURATION;
    private int mAnimTime = ANIM_DEF_DURATION * 1000;

    public LavaLamp(LavaListener listener) {
        mListener = listener;
        mColorAnim.addUpdateListener(this);
        mColorAnim.addListener(this);
        mColorAnim.addPauseListener(this);
    }

    public void startAnimation() {
        stopAnim();
        Color.colorToHSV(Color.parseColor(RED), from);
        Color.colorToHSV(Color.parseColor(BLUE), to);
        mColorAnim.setDuration(mAnimTime);
        mColorAnim.setRepeatMode(ValueAnimator.REVERSE);
        mColorAnim.setRepeatCount(ValueAnimator.INFINITE);
        mColorAnim.start();
    }

    public void stopAnim() {
        if (mColorAnim.isStarted()) {
            mColorAnim.end();
        }
        // implement mLastColor next round
        mListener.onStopLava(-1);
    }

    public void setAnimationTime(int time) {
        if (mAnimSeconds != time) {
            mAnimSeconds = time;
            mAnimTime = time * 1000;
            if (mColorAnim.isRunning()) {
                startAnimation();
            }
        }
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        // Transition along each axis of HSV (hue, saturation, value)
        hsv[0] = from[0] + (to[0] - from[0]) * animation.getAnimatedFraction();
        hsv[1] = from[1] + (to[1] - from[1]) * animation.getAnimatedFraction();
        hsv[2] = from[2] + (to[2] - from[2]) * animation.getAnimatedFraction();

        mListener.onColorUpdated(Color.HSVToColor(hsv));
    }

    @Override
    public void onAnimationStart(Animator animation) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onAnimationPause(Animator animation) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onAnimationResume(Animator animation) {
        // TODO Auto-generated method stub
        
    }
}
