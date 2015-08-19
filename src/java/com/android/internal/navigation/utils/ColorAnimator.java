
package com.android.internal.navigation.utils;

import com.android.internal.navigation.utils.ColorAnimatable.AnimatorControls;
import com.android.internal.navigation.utils.ColorAnimatable.ColorAnimationListener;
import com.android.internal.navigation.utils.ColorAnimatable.ColorAnimatorMachine;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Color;

public class ColorAnimator implements AnimatorUpdateListener, AnimatorControls,
        ColorAnimatorMachine {
    public static final int ANIM_DEF_DURATION = 10 * 1000;
    public static final String RED = "#ffff8080";
    public static final String BLUE = "#ff8080ff";

    protected final float[] from = new float[3], to = new float[3], hsv = new float[3];

    protected  ValueAnimator mColorAnim;
    protected long mAnimTime = ANIM_DEF_DURATION;
    protected int mFromColor = Color.parseColor(RED);
    protected int mToColor = Color.parseColor(BLUE);
    protected int mLastColor = Color.parseColor(RED);

    protected  ColorAnimationListener mListener;

    public ColorAnimator() {
        this(ValueAnimator.ofFloat(0, 1));
    }

    public ColorAnimator(ValueAnimator valueAnimator) {
        this(valueAnimator, ANIM_DEF_DURATION);
    }

    public ColorAnimator(ValueAnimator valueAnimator, long animDurationMillis) {
        this(valueAnimator, animDurationMillis, Color.parseColor(RED), Color.parseColor(BLUE));
    }

    public ColorAnimator(ValueAnimator valueAnimator, long animDurationMillis, int fromColor,
            int toColor) {
        mAnimTime = animDurationMillis;
        mFromColor = fromColor;
        mToColor = toColor;
        mColorAnim = valueAnimator;
        mColorAnim.addUpdateListener(this);
    }

    @Override
    public void start() {
        stop();
        Color.colorToHSV(mFromColor, from);
        Color.colorToHSV(mToColor, to);
        mColorAnim.setDuration(mAnimTime);
        mColorAnim.setRepeatMode(ValueAnimator.REVERSE);
        mColorAnim.setRepeatCount(ValueAnimator.INFINITE);
        if (mListener != null) {
            mListener.onStartAnimation(this, mFromColor);
        }
        mColorAnim.start();
    }

    @Override
    public void stop() {
        if (mColorAnim.isStarted()) {
            mColorAnim.end();
            if (mListener != null) {
                mListener.onStopAnimation(this, mLastColor);
            }
        }
    }

    @Override
    public void setAnimationTime(long millis) {
        if (mAnimTime != millis) {
            mAnimTime = millis;
            if (mColorAnim.isRunning()) {
                start();
            }
        }
    }

    @Override
    public void setColorAnimatorListener(ColorAnimationListener listener) {
        mListener = listener;
    }

    @Override
    public void removeColorAnimatorListener(ColorAnimationListener listener) {
        mListener = null;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        // Transition along each axis of HSV (hue, saturation, value)
        hsv[0] = from[0] + (to[0] - from[0]) * animation.getAnimatedFraction();
        hsv[1] = from[1] + (to[1] - from[1]) * animation.getAnimatedFraction();
        hsv[2] = from[2] + (to[2] - from[2]) * animation.getAnimatedFraction();

        mLastColor = Color.HSVToColor(hsv);

        if (mListener != null) {
            mListener.onColorChanged(this, mLastColor);
        }
    }

    @Override
    public int getCurrentColor() {
        return mLastColor;
    }
}
