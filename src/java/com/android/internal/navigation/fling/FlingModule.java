
package com.android.internal.navigation.fling;

import android.graphics.Canvas;

public interface FlingModule {
    /**
     * @param call back to nx host
     */
    public void setCallbacks(Callbacks callbacks);

    /**
     * @param canvas NX canvas to render
     */
    public void onDraw(Canvas canvas);

    public interface Callbacks {
        /**
         * @return width on NX host
         */
        public int onGetWidth();

        /**
         * @return height on NX host
         */
        public int onGetHeight();

        /**
         * invalidate NX host
         */
        public void onInvalidate();

        /**
         * force setDiabledFlags for bar element view state
         */
        public void onUpdateState();
    }
}
