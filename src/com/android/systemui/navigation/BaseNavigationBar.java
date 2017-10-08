/**
 * Copyright (C) 2008 The Android Open Source Project
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
 * Base navigation bar abstraction for managing keyguard policy, internal
 * bar behavior, and everything else not feature implementation specific
 * 
 */

package com.android.systemui.navigation;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.android.systemui.navigation.Navigator;
import com.android.systemui.navigation.Res;
import com.android.systemui.navigation.NavbarOverlayResources;
import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.navigation.pulse.PulseController.PulseObserver;
import com.android.systemui.navigation.utils.SmartObserver;
import com.android.systemui.plugins.statusbar.phone.NavGesture;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.R;

import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.ImageHelper;

import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringSystem;

import android.animation.LayoutTransition;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public abstract class BaseNavigationBar extends LinearLayout implements Navigator, PulseObserver {
    final static String TAG = "PhoneStatusBar/BaseNavigationBar";
    public final static boolean DEBUG = false;
    public static final boolean NAVBAR_ALWAYS_AT_RIGHT = true;
    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    public static final int MSG_SET_DISABLED_FLAGS = 101;
    public static final int MSG_INVALIDATE = 102;
    public static boolean sIsTablet;

    private boolean mKeyguardShowing;

    protected H mHandler = new H();
    protected final Display mDisplay;
    private final WindowManager mWm;
    protected View[] mRotatedViews = new View[4];
    protected View mCurrentView = null;
    protected FrameLayout mRot0, mRot90;
    protected int mDisabledFlags = 0;
    protected int mNavigationIconHints = 0;
    private int mCurrentRotation = -1;
    protected boolean mVertical;
    protected boolean mScreenOn;
    protected boolean mLeftInLandscape;
    protected boolean mLayoutTransitionsEnabled;
    protected boolean mWakeAndUnlocking;
    protected boolean mScreenPinningEnabled;
    protected OnVerticalChangedListener mOnVerticalChangedListener;
    protected SmartObserver mSmartObserver;
    protected PulseController mPulse;

    public NavbarOverlayResources mResourceMap;

    // use access methods to keep state proper
    private SpringSystem mSpringSystem;

    protected boolean mCarMode = false;
    protected boolean mDockedStackExists;

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
                case MSG_SET_DISABLED_FLAGS:
                    setDisabledFlags(mDisabledFlags, true);
                    break;
                case MSG_INVALIDATE:
                    invalidate();
                    break;
            }
        }
    }

    public BaseNavigationBar(Context context) {
        this(context, null);
    }

    public BaseNavigationBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisplay = ((WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        mWm = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        mSmartObserver = new SmartObserver(mHandler, context.getContentResolver());
        mSpringSystem = SpringSystem.create();
        sIsTablet = !DUActionUtils.navigationBarCanMove();
    }

    // require implementation. Surely they have something to clean up
    protected abstract void onDispose();

    // any implementation specific handling can be handled here
    protected void onInflateFromUser() {}

    protected void onKeyguardShowing(boolean showing){}

    public void abortCurrentGesture(){}

    public void setMenuVisibility(final boolean show) {}
    public void setMenuVisibility(final boolean show, final boolean force) {} 
    public void setNavigationIconHints(int hints) {}
    public void setNavigationIconHints(int hints, boolean force) {}
    public void onHandlePackageChanged(){}

    public Editor getEditor() { return null; }

    public boolean isInEditMode() { return false; }

    public void onRecreateStatusbar() {}

    public void setResourceMap(NavbarOverlayResources resourceMap) {
        mResourceMap = resourceMap;
        getBarTransitions().updateResources(mResourceMap);
    }

    public void updateNavbarThemedResources(Resources res){
        getBarTransitions().updateResources(mResourceMap);
    }

    @Override
    public void setControllers(PulseController pulseController) {
        mPulse = pulseController;
        mPulse.setPulseObserver(this);
    }

    protected PulseController getPulseController()  {
        return mPulse;
    }

    protected static float alphaIntToFloat(int alpha) {
        return (float) Math.max(0, Math.min(255, alpha)) / 255;
    }

    @Override
    public void setWakeAndUnlocking(boolean wakeAndUnlocking) {
        setUseFadingAnimations(wakeAndUnlocking);
        mWakeAndUnlocking = wakeAndUnlocking;
        updateLayoutTransitionsEnabled();
    }

    @Override
    public boolean needsReorient(int rotation) {
        return mCurrentRotation != rotation;
    }

    public SpringSystem getSpringSystem() {
        if (mSpringSystem == null) {
            mSpringSystem = SpringSystem.create();
        }
        return mSpringSystem;
    }

    public void flushSpringSystem() {
        if (mSpringSystem != null) {
            for (Spring spring : mSpringSystem.getAllSprings()) {
                spring.setAtRest();
                spring.removeAllListeners();
                spring.destroy();
            }
            mSpringSystem.removeAllListeners();
            mSpringSystem = null;
        }
    }

    protected boolean areAnyHintsActive() {
        return ((mDisabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0)
                || ((mDisabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0)
                || (((mDisabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0)));

    }

    private WindowManager.LayoutParams getParamsFromParent() {
        // we need to get params from the parent container otherwise we'd get FrameLayout params
        return  (WindowManager.LayoutParams) ((ViewGroup) getParent()).getLayoutParams();
    }

    protected void setUseFadingAnimations(boolean useFadingAnimations) {
        WindowManager.LayoutParams lp = getParamsFromParent();
        if (lp != null) {
            boolean old = lp.windowAnimations != 0;
            if (!old && useFadingAnimations) {
                lp.windowAnimations = R.style.Animation_NavigationBarFadeIn;
            } else if (old && !useFadingAnimations) {
                lp.windowAnimations = 0;
            } else {
                return;
            }
            mWm.updateViewLayout((View) getParent(), lp);
        }
    }

    protected void updateLayoutTransitionsEnabled() {
        boolean enabled = !mWakeAndUnlocking && mLayoutTransitionsEnabled;
        ViewGroup navButtons = (ViewGroup) getCurrentView().findViewById(R.id.nav_buttons);
        if (navButtons == null) {
            navButtons = (ViewGroup) mCurrentView.findViewWithTag(Res.Common.NAV_BUTTONS);
        }
        LayoutTransition lt = navButtons.getLayoutTransition();
        if (lt != null) {
            if (enabled) {
                lt.enableTransitionType(LayoutTransition.APPEARING);
                lt.enableTransitionType(LayoutTransition.DISAPPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            } else {
                lt.disableTransitionType(LayoutTransition.APPEARING);
                lt.disableTransitionType(LayoutTransition.DISAPPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            }
        }
    }

    @Override
    public void setLayoutTransitionsEnabled(boolean enabled) {
        mLayoutTransitionsEnabled = enabled;
        updateLayoutTransitionsEnabled();
    }

    public int findViewByIdName(String name) {
        return DUActionUtils.getId(getContext(), name,
                DUActionUtils.PACKAGE_SYSTEMUI);
    }

    public void setForgroundColor(Drawable drawable) {
        if (mRot0 != null) {
            mRot0.setForeground(drawable);
        }
        if (mRot90 != null) {
            mRot90.setForeground(drawable);
        }
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        if (mLeftInLandscape != leftInLandscape) {
            mLeftInLandscape = leftInLandscape;
            if (mPulse != null) {
                mPulse.setLeftInLandscape(leftInLandscape);
            }
        }
    }

    // keep keyguard methods final and use getter to access
    public final void setKeyguardShowing(boolean showing) {
        if (mKeyguardShowing != showing) {
            mKeyguardShowing = showing;
            if (mPulse != null) {
                mPulse.setKeyguardShowing(showing);
            }
            onKeyguardShowing(showing);
        }
    }

    public final boolean isKeyguardShowing() {
        return mKeyguardShowing;
    }

    public boolean isLandscape() {
        return mCurrentView == mRot90;
    }

    // if a bar instance is created from a user mode switch
    // PhoneStatusBar should call this. This allows the view
    // to make adjustments that are otherwise not needed when
    // inflating on boot, such as setting proper transition flags
    public final void notifyInflateFromUser() {
        getBarTransitions().transitionTo(BarTransitions.MODE_TRANSPARENT, false);
        mScreenOn = true;
        if (mPulse != null) {
            mPulse.notifyScreenOn(mScreenOn);
        }
        onInflateFromUser();
    }

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getHiddenView() {
        if (mCurrentView.equals(mRot0)) {
            return mRot90;
        } else {
            return mRot0;
        }
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public boolean isVertical() {
        return mVertical;
    }

    public final void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    public final void dispose() {
        mSmartObserver.cleanUp();
        if (mPulse != null) {
            mPulse.doUnlinkVisualizer();
        }
        flushSpringSystem();
        onDispose();
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(mVertical);
        }
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        if (mPulse != null) {
            mPulse.notifyScreenOn(screenOn);
        }
        setDisabledFlags(mDisabledFlags, true);
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);
        mCurrentRotation = rot;

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }
    }

    protected void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = getParamsFromParent();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            mWm.updateViewLayout((View) getParent(), lp);
        }
    }

    public void setNotificationPanelExpanded(boolean expanded) {
    }

    @Override
    public void onFinishInflate() {
        mRot0 = (FrameLayout) findViewById(R.id.rot0);
        mRot90 = (FrameLayout) findViewById(R.id.rot90);
        mRotatedViews[Surface.ROTATION_0] =
        mRotatedViews[Surface.ROTATION_180] = mRot0;
        mRotatedViews[Surface.ROTATION_90] = mRot90;
        mRotatedViews[Surface.ROTATION_270] = mRotatedViews[Surface.ROTATION_90];
        mCurrentView = mRotatedViews[Surface.ROTATION_0];
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags)
            return;
        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);
    }

    @Override
    public final View getBaseView() {
        return (View)this;
    }

    @Override
    public void onPluginConnected(NavGesture plugin, Context context) {

    }

    @Override
    public void onPluginDisconnected(NavGesture plugin) {

    }

    // for when we don't inflate xml
    protected void createBaseViews() {
        LinearLayout rot0NavButton = new LinearLayout(getContext());
        rot0NavButton.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        rot0NavButton.setOrientation(LinearLayout.HORIZONTAL);
        rot0NavButton.setClipChildren(false);
        rot0NavButton.setClipToPadding(false);
        rot0NavButton.setLayoutTransition(new LayoutTransition());
        rot0NavButton.setTag(Res.Common.NAV_BUTTONS);

        LinearLayout rot90NavButton = new LinearLayout(getContext());
        rot90NavButton.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        rot90NavButton.setOrientation(sIsTablet ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        rot90NavButton.setClipChildren(false);
        rot90NavButton.setClipToPadding(false);
        rot90NavButton.setLayoutTransition(new LayoutTransition());
        rot90NavButton.setTag(Res.Common.NAV_BUTTONS);

        mRot0 = new FrameLayout(getContext());
        mRot0.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        mRot90 = new FrameLayout(getContext());
        mRot90.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mRot90.setVisibility(View.GONE);
        mRot90.setPadding(mRot90.getPaddingLeft(), 0, mRot90.getPaddingRight(),
                mRot90.getPaddingBottom());

        if (!BarTransitions.HIGH_END) {
            setBackground(getContext().getDrawable(R.drawable.system_bar_background));
        }

//        addBatteryBarLayout(mRot0);
        mRot0.addView(rot0NavButton);

//        addBatteryBarLayout(mRot90);
        mRot90.addView(rot90NavButton);

        addView(mRot0);
        addView(mRot90);

        mRotatedViews[Surface.ROTATION_0] =
                mRotatedViews[Surface.ROTATION_180] = mRot0;
        mRotatedViews[Surface.ROTATION_90] = mRot90;
        mRotatedViews[Surface.ROTATION_270] = mRotatedViews[Surface.ROTATION_90];
        mCurrentView = mRotatedViews[Surface.ROTATION_0];
    }
/*
    private void addBatteryBarLayout(ViewGroup parent) {
        int which = -1;
        if (parent.equals(mRot0)) {
            which = R.layout.battery_bar_rot0;
        } else if (parent.equals(mRot90)) {
            which = R.layout.battery_bar_rot90;
        } else {
            return;
        }
        try {
            View bar = View.inflate(getContext(), which, null);
            parent.addView(bar);
        } catch (Exception e) {
            Log.e(TAG, "BatteryBarController failed to inflate");
        }
    }
*/
    protected void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }
        postCheckForInvalidLayout("sizeChanged");
        if (mPulse != null) {
            mPulse.onSizeChanged(w, h, oldw, oldh);
        }
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mPulse != null) {
            mPulse.onDraw(canvas);
        }
    }

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    protected String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    protected static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public Drawable getLocalDrawable(String resName, Resources res) {
        int id = getDrawableId(resName);
        Drawable icon = ImageHelper.getVector(res, id, false);
        if (icon == null) {
            icon = res.getDrawable(id);
        }
        return icon;
    }

    public int getDrawableId(String resName) {
        try {
            int ident = getContext().getResources().getIdentifier(resName, "drawable",
                    getContext().getPackageName());
            return ident;
        } catch (Exception e) {
            return -1;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println("    }");
    }

    protected static String viewInfo(View v) {
        return "[(" + v.getLeft() + "," + v.getTop() + ")(" + v.getRight() + "," + v.getBottom()
                + ") " + v.getWidth() + "x" + v.getHeight() + "]";
    }
}
