
package com.android.internal.navigation.fling;

import android.graphics.Canvas;
import android.os.Handler;

public interface FlingModule {
    /**
     * @param call back to Fling host
     */
    public void setCallbacks(Callbacks callbacks);

    /**
     * @param canvas Fling canvas to render
     */
    public void onDraw(Canvas canvas);

    public interface Callbacks {
        /**
         * @return width on Fling host
         */
        public int onGetWidth();

        /**
         * @return height on Fling host
         */
        public int onGetHeight();

        /**
         * invalidate Fling host
         */
        public void onInvalidate();

        /**
         * force setDiabledFlags for bar element view state
         */
        public void onUpdateState();

        /**
         * get the Fling ui thread handler
         */
        public Handler getHandler();
    }
}
