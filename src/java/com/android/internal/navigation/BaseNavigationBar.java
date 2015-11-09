/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2014 The TeamEos Project
 * 
 * Contributor: Randall Rushing aka Bigrushdog
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

package com.android.internal.navigation;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.android.internal.navigation.BarTransitions;
import com.android.internal.navigation.utils.SmartObserver;
import com.android.internal.utils.eos.EosActionUtils;

import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public abstract class BaseNavigationBar extends LinearLayout implements Navigator {
    final static String TAG = "PhoneStatusBar/BaseNavigationBar";
    public final static boolean DEBUG = false;
    public static final boolean NAVBAR_ALWAYS_AT_RIGHT = true;
    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    private H mHandler = new H();
    private boolean mKeyguardShowing;

    protected final Display mDisplay;
    protected View[] mRotatedViews = new View[4];
    protected View mCurrentView = null;
    protected FrameLayout mRot0, mRot90;
    protected int mDisabledFlags = 0;
    protected int mNavigationIconHints = 0;
    protected boolean mVertical;
    protected boolean mScreenOn;
    protected boolean mLeftInLandscape;
    protected boolean mLayoutTransitionsEnabled;
    protected boolean mWakeAndUnlocking;
    protected OnVerticalChangedListener mOnVerticalChangedListener;
    protected SmartObserver mSmartObserver;

    // listeners from PhoneStatusBar
    protected View.OnTouchListener mHomeActionListener;
    protected View.OnTouchListener mUserAutoHideListener;

    // call getAvailableResources() to safeguard against null
    private Resources mThemedResources;

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
            }
        }
    }

    public BaseNavigationBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisplay = ((WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        mSmartObserver = new SmartObserver(mHandler, context.getContentResolver());
        mVertical = false;
    }

    // require implementation. Surely they have something to clean up
    protected abstract void onDispose();

    // let subclass know theme changed
    protected void onUpdateResources(Resources res) {}

    // any implementation specific handling can be handled here
    protected void onInflateFromUser() {}

    protected void onKeyguardShowing(boolean showing){}

    public void abortCurrentGesture(){}

    public void setMenuVisibility(final boolean show) {}
    public void setMenuVisibility(final boolean show, final boolean force) {}
    public void setNavigationIconHints(int hints) {}
    public void setNavigationIconHints(int hints, boolean force) {}
    public void onHandlePackageChanged(){}
    public void updateSettings(){};

    public View getRecentsButton() { return null; }
    public View getMenuButton() { return null; }
    public View getBackButton() { return null; }
    public View getHomeButton() { return null; }
    public boolean isInEditMode() { return false; }

	@Override
	public void setListeners(OnTouchListener homeActionListener,
			OnLongClickListener homeLongClickListener,
			OnTouchListener userAutoHideListener,
			OnClickListener recentsClickListener,
			OnTouchListener recentsTouchListener,
			OnLongClickListener recentsLongClickListener) {}

	@Override
    public void setWakeAndUnlocking(boolean wakeAndUnlocking) {
        setUseFadingAnimations(wakeAndUnlocking);
        mWakeAndUnlocking = wakeAndUnlocking;
    }

	protected void setUseFadingAnimations(boolean useFadingAnimations) {
		WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
		if (lp != null) {
			boolean old = lp.windowAnimations != 0;
			if (!old && useFadingAnimations) {
				lp.windowAnimations = EosActionUtils.getIdentifier(mContext,
						"Animation_NavigationBarFadeIn", "style",
						EosActionUtils.PACKAGE_SYSTEMUI);
			} else if (old && !useFadingAnimations) {
				lp.windowAnimations = 0;
			} else {
				return;
			}
			WindowManager wm = (WindowManager) getContext().getSystemService(
					Context.WINDOW_SERVICE);
			wm.updateViewLayout(this, lp);
		}
	}

    public void setLayoutTransitionsEnabled(boolean enabled) {
        mLayoutTransitionsEnabled = enabled;
    }

    public void setForgroundColor(Drawable drawable) {
        if (mRot0 != null) {
            mRot0.setForeground(drawable);
        }
        if (mRot90 != null) {
            mRot90.setForeground(drawable);
        }
    }

    // PhoneStatusBar sets initial value and observes for changes
    // TODO: Observe this in NavigationCoordinator
    public void setLeftInLandscape(boolean leftInLandscape) {
        if (mLeftInLandscape != leftInLandscape) {
            mLeftInLandscape = leftInLandscape;
        }
    }

    // keep keyguard methods final and use getter to access
    public final void setKeyguardShowing(boolean showing) {
        if (mKeyguardShowing != showing) {
            mKeyguardShowing = showing;
            onKeyguardShowing(showing);
        }
    }

    protected final boolean isKeyguardShowing() {
        return mKeyguardShowing;
    }

    // if a bar instance is created from a user mode switch
    // PhoneStatusBar should call this. This allows the view
    // to make adjustments that are otherwise not needed when
    // inflating on boot, such as setting proper transition flags
    public final void notifyInflateFromUser() {
        getBarTransitions().transitionTo(BarTransitions.MODE_TRANSPARENT, false);
        ContentResolver resolver = getContext().getContentResolver();

        // PhoneStatusBar doesn't set this when user inflates a bar, only when
        // actual value changes #common_cm
        mLeftInLandscape = Settings.System.getIntForUser(resolver,
                Settings.System.NAVBAR_LEFT_IN_LANDSCAPE, 0, UserHandle.USER_CURRENT) == 1;
        // we boot with screen off, but we need to force it true here
        mScreenOn = true;
        onInflateFromUser();
    }

    // handle updating bar transitions, lights out
    // pass iterated container to subclass for
    // handling implementation specific updates
    // let subclass know we're done for non-view
    // specific work
    public final void updateResources(Resources res) {
        mThemedResources = res;
        getBarTransitions().updateResources(res);
        onUpdateResources(res);
    }

    public void setTransparencyAllowedWhenVertical(boolean allowed) {
//        getBarTransitions().setTransparencyAllowedWhenVertical(allowed);
    }

    public View getCurrentView() {
        return mCurrentView;
    }

    public View.OnTouchListener getHomeActionListener() {
        return mHomeActionListener;
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
        onDispose();
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(mVertical);
        }
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }      
    }

    @Override
    public void onFinishInflate() {
        int rot0id = EosActionUtils.getId(mContext, "rot0", EosActionUtils.PACKAGE_SYSTEMUI);
        int rot90id = EosActionUtils.getId(mContext, "rot90", EosActionUtils.PACKAGE_SYSTEMUI);
        mRot0 = (FrameLayout) findViewById(rot0id);
        mRot90 = (FrameLayout) findViewById(rot90id);
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

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }
    }

    @Override
    public final View getBaseView() {
        return (View)this;
    }

    // returns themed resources is availabe, otherwise system resources
    protected Resources getAvailableResources() {
        return mThemedResources != null ? mThemedResources : getContext().getResources();
    }

    protected void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
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
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
    }

    protected String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = mContext.getResources();
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
