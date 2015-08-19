
package com.android.internal.navigation;

import android.content.Context;
import android.view.WindowManager;

public abstract class BaseEditor implements Editor {
    public static int MODE_ON = 1;
    public static int MODE_OFF = 2;

    protected Context mContext;
    protected WindowManager mWindowManager;
    private int mMode = MODE_OFF;
    private boolean mIsLandscape;

    public BaseEditor(Context ctx) {
        mContext = ctx;
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    protected abstract void onEditModeChanged(int mode);

    protected abstract void onPrepareToReorient();

    protected abstract void onReorient();

    @Override
    public final void changeEditMode(int mode) {
        if (mMode == mode)
            return;
        mMode = mode;
        onEditModeChanged(mode);
    }

    @Override
    public final void prepareToReorient() {
        onPrepareToReorient();
    }

    @Override
    public final void reorient(boolean isLandscape) {
        mIsLandscape = isLandscape;
        onReorient();
    }

    protected boolean isLandscape() {
        return mIsLandscape;
    }

    protected int getMode() {
        return mMode;
    }
}
