
package com.android.internal.navigation.utils;

public class ColorAnimatable {
    public interface ColorAnimationListener {
        public void onColorChanged(ColorAnimator colorAnimator, int color);
        public void onStartAnimation(ColorAnimator colorAnimator, int firstColor);
        public void onStopAnimation(ColorAnimator colorAnimator, int lastColor);
    }

    public interface ColorAnimatorMachine {
        public void setColorAnimatorListener(ColorAnimationListener listener);
        public void removeColorAnimatorListener(ColorAnimationListener listener);
    }

    public interface AnimatorControls {
        public void start();
        public void stop();
        public void setAnimationTime(long millis);
        public int getCurrentColor();
    }
}
