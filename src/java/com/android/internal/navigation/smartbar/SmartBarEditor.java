
package com.android.internal.navigation.smartbar;

import java.util.ArrayList;
import java.util.Collections;

import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.internal.navigation.BaseEditor;
import com.android.internal.navigation.EditActionItemAdapter;
import com.android.internal.navigation.Res;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;

public class SmartBarEditor extends BaseEditor implements View.OnTouchListener {
    private static final String TAG = SmartBarEditor.class.getSimpleName();

    // time is takes button pressed to become draggable
    private static final int QUICK_LONG_PRESS = 350;

    // once button is draggable, additional time needed
    // to trigger action window
    private static final int SHOW_WINDOW_EXTRA = 100;    

    // once button is draggable, additional time needed
    // to trigger action window
    private static final int SHOW_WINDOW_TIME = QUICK_LONG_PRESS + SHOW_WINDOW_EXTRA;

    // true == we're currently checking for long press
    private boolean mLongPressed;
    // once longpressed, watching for action window
    private boolean mWatchingForWindow;
    // start point of the current drag operation
    private float mDragOrigin;
    // just to avoid reallocations
    private static final int[] sLocation = new int[2];

    // initial touch down points
    private int mTouchDownX;
    private int mTouchDownY;

    private SmartBarView mHost;

    // buttons to animate when changing positions
    private ArrayList<SmartButtonView> mSquatters = new ArrayList<SmartButtonView>();

    // button that is being edited
    private String mButtonHasFocusTag;

    // action window view
    private FrameLayout mEditContainer;

    public SmartBarEditor(SmartBarView host, boolean isLandscape) {
        super(host.getContext());
        mHost = host;
        mEditContainer = new FrameLayout(mContext);
    }

    @Override
    public void onEditModeChanged(int mode) {
        setButtonsEditMode(isInEditMode());
        if (!isInEditMode()) {
            onCommitChanges();
        } 
    }

    @Override
    public void onCommitChanges() {
        ArrayList<ButtonConfig> buttonConfigs = new ArrayList<ButtonConfig>();
        for (String buttonTag : mHost.getCurrentSequence()) {
            SmartButtonView v = mHost.findCurrentButton(buttonTag);
            if (v != null) {
                ButtonConfig config = v.getButtonConfig();
                if (config != null) {
                    buttonConfigs.add(config);
                }
            }
        }
        Config.setConfig(mContext, ActionConstants.getDefaults(ActionConstants.SMARTBAR),
                buttonConfigs);
    }

    @Override
    protected void onPrepareToReorient() {
        // if we are in edit mode turn them off
        // since the new buttons will be visibile very soon
        if (isInEditMode()) {
            setButtonsEditMode(false);
        }
    }

    @Override
    protected void onReorient() {
        // if we are in edit mode turn them on
        if (isInEditMode()) {
            setButtonsEditMode(true);
        }
    }

    private void setButtonsEditMode(boolean isInEditMode) {
        for (String buttonTag : mHost.getCurrentSequence()) {
            SmartButtonView v = mHost.findCurrentButton(buttonTag);
            if (v != null) {
                v.setEditMode(isInEditMode);
                v.setOnTouchListener(isInEditMode ? this : null);
            }
        }
    }

    private boolean isInEditMode() {
        return getMode() == MODE_ON;
    }

    private Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isInEditMode()) {
                mLongPressed = true;
                mHost.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    };

    private Runnable mCheckShowWindow = new Runnable() {
        public void run() {
            if (isInEditMode()) {
                SmartButtonView test = mHost.findCurrentButton(mButtonHasFocusTag);
                if (test != null) {
                showActionView(test);
                }
            }
        }
    };

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!isInEditMode()) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            removeActionView();
            mButtonHasFocusTag = (String)view.getTag();
            mTouchDownX = (int) event.getX();
            mTouchDownY = (int) event.getY();
            view.setPressed(true);
            view.getLocationOnScreen(sLocation);
            mDragOrigin = sLocation[mHost.isVertical() ? 1 : 0];
            view.postDelayed(mCheckLongPress, QUICK_LONG_PRESS);
            view.postDelayed(mCheckShowWindow, SHOW_WINDOW_TIME);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            view.setPressed(false);

            if (!mLongPressed) {
                return false;
            }

            ViewGroup viewParent = (ViewGroup) view.getParent();
            float pos = mHost.isVertical() ? event.getRawY() : event.getRawX();
            float buttonSize = mHost.isVertical() ? view.getHeight() : view.getWidth();
            float min = mHost.isVertical() ? viewParent.getTop()
                    : (viewParent.getLeft() - buttonSize / 2);
            float max = mHost.isVertical() ? (viewParent.getTop() + viewParent.getHeight())
                    : (viewParent.getLeft() + viewParent.getWidth());

            // Prevents user from dragging view outside of bounds
            if (pos < min || pos > max) {
                return false;
            }
            if (!mHost.isVertical()) {
                view.setX(pos - viewParent.getLeft() - buttonSize / 2);
            } else {
                view.setY(pos - viewParent.getTop() - buttonSize / 2);
            }
            View affectedView = findInterceptingView(pos, view);
            if (affectedView == null) {
                return false;
            }
            view.removeCallbacks(mCheckLongPress);
            removeActionView();
            switchId(affectedView, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
            view.removeCallbacks(mCheckLongPress);
            removeActionView();

            if (mLongPressed) {
                // Reset the dragged view to its original location
                ViewGroup parent = (ViewGroup) view.getParent();
                boolean vertical = mHost.isVertical();
                float slideTo = vertical ? mDragOrigin - parent.getTop() : mDragOrigin - parent.getLeft();
                Animator anim = getButtonSlideAnimator(view, vertical, slideTo);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.setDuration(100);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mSquatters.clear();
                    }
                });
                anim.start();
            }
            mLongPressed = false;
        }
        return true;
    }

    private void showActionView(SmartButtonView test) {
        mEditContainer.removeAllViews();
        mEditContainer.addView(getEditList());
        int actionHeight = DUActionUtils.getDimenPixelSize(mContext, Res.Softkey.ACTION_EDITOR_HEIGHT, DUActionUtils.PACKAGE_SYSTEMUI);
        int actionWidth = DUActionUtils.getDimenPixelSize(mContext, Res.Softkey.ACTION_EDITOR_WIDTH, DUActionUtils.PACKAGE_SYSTEMUI);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                actionWidth * 2, actionHeight * 2,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        lp.gravity = Gravity.BOTTOM;
        lp.setTitle("SmartBar Editor");
        lp.windowAnimations = 0;
        if (!mEditContainer.isAttachedToWindow()) {
            mEditContainer.setVisibility(View.VISIBLE);
        mWindowManager.addView(mEditContainer, lp);
        }
    }

    /*
    private SmartButtonView getActionLayout(SmartButtonView test) {
        SmartButtonView v = SmartBarHelper.generatePrimaryKey(mContext, isLandscape(), null, test.getButtonConfig());
        v.setLayoutParams(new FrameLayout.LayoutParams(test.getWidth() * 2, test.getHeight() * 2, Gravity.CENTER));
        //LinearLayout layout = new LinearLayout(mContext);
        //layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        //layout.addView(v);
        return v;
    }
    */

    private ListView getEditList() {
        ListView list = new ListView(mContext);
        EditActionItemAdapter adapter = new EditActionItemAdapter(mContext);
        list.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        list.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return list;
    }

    private void removeActionView() {
        if (mEditContainer != null && mEditContainer.isAttachedToWindow()) {
            mEditContainer.setVisibility(View.GONE);
            mEditContainer.removeAllViews();
            mWindowManager.removeView(mEditContainer);
        }
    }

    /**
     * Find intersecting view in mButtonViews
     * 
     * @param pos - pointer location
     * @param v - view being dragged
     * @return intersecting view or null
     */
    private View findInterceptingView(float pos, View v) {
        for (String buttonTag : mHost.getCurrentSequence()) {
            SmartButtonView otherView = mHost.findCurrentButton(buttonTag);
            if (otherView == v) {
                continue;
            }
            if (mSquatters.contains(otherView)) {
                continue;
            }

            otherView.getLocationOnScreen(sLocation);
            float otherPos = sLocation[mHost.isVertical() ? 1 : 0];
            float otherDimension = mHost.isVertical() ? v.getHeight() : v.getWidth();

            if (pos > (otherPos + otherDimension / 4) && pos < (otherPos + otherDimension)) {
                mSquatters.add(otherView);
                return otherView;
            }
        }
        return null;
    }

    /**
     * Switches positions of two views and updates their mButtonViews entry
     * 
     * @param targetView - view to be replaced animate out
     * @param view - view being dragged
     */
    private void switchId(View replaceView, View dragView) {
        final SmartButtonView squatter = (SmartButtonView)replaceView;
        final SmartButtonView dragger = (SmartButtonView)dragView;
        final boolean vertical = mHost.isVertical();
        
        ViewGroup parent = (ViewGroup) replaceView.getParent();
        float slideTo = vertical ? mDragOrigin - parent.getTop() : mDragOrigin - parent.getLeft();
        replaceView.getLocationOnScreen(sLocation);
        mDragOrigin = sLocation[vertical ? 1 : 0];

        final int targetIndex = mHost.getCurrentSequence().indexOf(squatter.getTag());
        final int draggedIndex = mHost.getCurrentSequence().indexOf(dragger.getTag());
        Collections.swap(mHost.getCurrentSequence(), draggedIndex, targetIndex);

        SmartButtonView hidden1 = (SmartButtonView) getHiddenNavButtons().findViewWithTag(squatter.getTag());
        SmartButtonView hidden2 = (SmartButtonView) getHiddenNavButtons().findViewWithTag(dragger.getTag());
        swapConfigs(hidden1, hidden2);

        Animator anim = getButtonSlideAnimator(squatter, vertical, slideTo);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.setDuration(200);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSquatters.remove(squatter);
            }
        });
        anim.start();
    }

    private Animator getButtonSlideAnimator(View v, boolean vertical, float slideTo) {
        return ObjectAnimator.ofFloat(v, vertical ? View.Y : View.X, slideTo);
    }

    private void swapConfigs(SmartButtonView v1, SmartButtonView v2) {
        ButtonConfig config1 = v1.getButtonConfig();
        ButtonConfig config2 = v2.getButtonConfig();
        v1.setButtonConfig(config2);
        v2.setButtonConfig(config1);
        v1.setImageDrawable(config2.getCurrentIcon(mContext));
        v2.setImageDrawable(config1.getCurrentIcon(mContext));
    }

    private ViewGroup getHiddenNavButtons() {
        return (ViewGroup) mHost.getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
    }

}
