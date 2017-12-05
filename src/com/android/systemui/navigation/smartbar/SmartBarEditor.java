/**
 * Copyright (C) 2016 The DirtyUnicorns Project
 * Copyright (C) 2015 The CyanogenMod Project
 * 
 * @author: Randall Rushing <randall.rushing@gmail.com>
 *
 * Contributions from The CyanogenMod Project
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
 */

package com.android.systemui.navigation.smartbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.systemui.navigation.BaseEditor;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.OpaLayout;
import com.android.systemui.navigation.Res;
import com.android.systemui.navigation.smartbar.SmartBarEditor;
import com.android.systemui.navigation.smartbar.SmartBarHelper;
import com.android.systemui.navigation.smartbar.SmartBarView;
import com.android.systemui.navigation.smartbar.SmartButtonView;
import com.android.systemui.navigation.editor.ActionItem;
import com.android.systemui.navigation.editor.QuickAction;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.Uri;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView.ScaleType;
import android.widget.PopupWindow;

public class SmartBarEditor extends BaseEditor implements View.OnTouchListener {
    private static final String TAG = SmartBarEditor.class.getSimpleName();
    // time is takes button pressed to become draggable
    private static final int QUICK_LONG_PRESS = 200;
    private static final int POPUP_LONG_PRESS = QUICK_LONG_PRESS + 300;
    // time popup hide animation takes before we hide the conatiner
    private static final int POPUP_ANIM_DELAY = 300;

    public static final int MENU_MAP_ACTIONS = 1;
    public static final int MENU_MAP_ICON = 2;
    public static final int MENU_MAP_ADD = 3;
    public static final int MENU_MAP_REMOVE = 4;
    public static final int MENU_MAP_CANCEL = 5;
    public static final int MENU_MAP_FINISH = 6;
    public static final int MENU_MAP_ACTIONS_SINGLE_TAP = 7;
    public static final int MENU_MAP_ACTIONS_DOUBLE_TAP = 8;
    public static final int MENU_MAP_ACTIONS_LONG_PRESS = 9;
    public static final int MENU_MAP_ICON_ICON_PACK = 10;
    public static final int MENU_MAP_ICON_ICON_GALLERY = 11;
    public static final int MENU_MAP_ICON_ICON_COLOR = 12;
    public static final int MENU_MAP_ICON_ICON_RESET = 13;

    static final int POPUP_TYPE_ROOT = 1;
    static final int POPUP_TYPE_TAP = 2;
    static final int POPUP_TYPE_ICON = 3;

    public static final String BACK = ActionConstants.Smartbar.BUTTON1_TAG;
    public static final String HOME = ActionConstants.Smartbar.BUTTON2_TAG;

    public static final int PHONE_MAX_BUTTONS = 7;
    public static final int TABLET_MAX_BUTTONS = 10;

    private boolean mIsButtonMoving;
    // true == we're currently checking for long press
    private boolean mLongPressed;
    // start point of the current drag operation
    private float mDragOrigin;
    // just to avoid reallocations
    private static final int[] sLocation = new int[2];

    private SmartBarView mHost;

    private FrameLayout mEditContainer;
    private View mHidden;
    private Point mOriginPoint = new Point();

    // buttons to animate when changing positions
    private ArrayList<OpaLayout> mSquatters = new ArrayList<>();

    // which action are we editing
    private int mTapHasFocusTag;

    // editor popup menu current instance holder
    private QuickAction mPopup;
    private Map<Integer, ActionItem> mPrimaryMenuItems = new HashMap<Integer, ActionItem>();
    private Map<Integer, ActionItem> mTapMenuItems = new HashMap<Integer, ActionItem>();
    private Map<Integer, ActionItem> mIconMenuItems = new HashMap<Integer, ActionItem>();

    private QuickAction.OnActionItemClickListener mQuickClickListener = new QuickAction.OnActionItemClickListener() {
        @Override
        public void onItemClick(QuickAction source, int pos, int actionId) {
            if (!mIsButtonMoving) { //don't do anything if the user is dragging the button
                Intent intent;
                switch (actionId) {
                    case MENU_MAP_ACTIONS:
                        postSecondaryPopup(POPUP_TYPE_TAP);
                        break;
                    case MENU_MAP_ICON:
                        postSecondaryPopup(POPUP_TYPE_ICON);
                        break;
                    case MENU_MAP_ADD:
                        addButton();
                        break;
                    case MENU_MAP_REMOVE:
                        removeButton();
                        break;
                    case MENU_MAP_CANCEL:
                        break;
                    case MENU_MAP_FINISH:
                        changeEditMode(MODE_OFF);
                        break;
                    case MENU_MAP_ACTIONS_SINGLE_TAP:
                        startActionPicker(ActionConfig.PRIMARY);
                        break;
                    case MENU_MAP_ACTIONS_LONG_PRESS:
                        startActionPicker(ActionConfig.SECOND);
                        break;
                    case MENU_MAP_ACTIONS_DOUBLE_TAP:
                        startActionPicker(ActionConfig.THIRD);
                        break;
                    case MENU_MAP_ICON_ICON_PACK:
                        intent = new Intent();
                        intent.setAction(Intent.ACTION_MAIN);
                        intent.setClassName(INTENT_ACTION_EDIT_CLASS,
                                INTENT_ACTION_ICON_PICKER_COMPONENT);
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                        break;
                    case MENU_MAP_ICON_ICON_GALLERY:
                        intent = new Intent();
                        intent.setAction(Intent.ACTION_MAIN);
                        intent.setClassName(INTENT_ACTION_EDIT_CLASS,
                                INTENT_ACTION_GALLERY_PICKER_COMPONENT);
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                        break;
                    case MENU_MAP_ICON_ICON_COLOR:
                        break;
                    case MENU_MAP_ICON_ICON_RESET:
                        resetIcon();
                        break;
                }
            }
        }
    };

    private final Runnable mHidePopupContainer = new Runnable() {
        @Override
        public void run() {
            mEditContainer.setVisibility(View.GONE);
        }
    };

    private PopupWindow.OnDismissListener mPopupDismissListener = new PopupWindow.OnDismissListener() {
        @Override
        public void onDismiss() {
            mHost.postDelayed(mHidePopupContainer, POPUP_ANIM_DELAY);
        }
    };

    private View.OnTouchListener mEditorWindowTouchListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            return mPopupTouchWrapper.onTouch(v, event);
        }
    };

    private View.OnTouchListener mPopupTouchWrapper = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                mPopup.dismiss();
                return true;
            }
            return false;
        }
    };

    private void postSecondaryPopup(final int type) {
        final PopupWindow.OnDismissListener listener = new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                showPopup(type);
            }
        };
        mPopup.mWindow.setOnDismissListener(listener);
        mHost.removeCallbacks(mHidePopupContainer);
        mPopup.dismiss();
    }

    public SmartBarEditor(SmartBarView host) {
        super(host);
        mHost = host;
    }

    private String getEditButtonTag() {
        return (String) mHidden.getTag();
    }

    private void resetIcon() {
        final String buttonFocus = getEditButtonTag();
        SmartButtonView currentButton = mHost.findCurrentButton(buttonFocus);
        SmartButtonView otherButton = (SmartButtonView) getHiddenNavButtons().findViewWithTag(
                buttonFocus);
        ButtonConfig currentConfig = currentButton.getButtonConfig();
        ButtonConfig otherConfig = otherButton.getButtonConfig();
        OpaLayout currentOpa = (OpaLayout) currentButton.getParent();
        OpaLayout otherOpa = (OpaLayout) otherButton.getParent();
        currentConfig.clearCustomIconIconUri();
        otherConfig.clearCustomIconIconUri();
        currentButton.setButtonConfig(currentConfig);
        otherButton.setButtonConfig(otherConfig);

        mHost.setButtonDrawable(currentButton);
        SmartBarHelper.updateButtonScalingAndPadding(currentOpa, isLandscape());

        mHost.setButtonDrawable(otherButton);
        SmartBarHelper.updateButtonScalingAndPadding(otherOpa, !isLandscape());

        onCommitChanges();
    }

    @Override
    protected void onIconPicked(String type, String packageName, String iconName) {
        final String buttonFocus = getEditButtonTag();
        SmartButtonView currentButton = mHost.findCurrentButton(buttonFocus);
        SmartButtonView otherButton = (SmartButtonView) getHiddenNavButtons().findViewWithTag(
                buttonFocus);
        ButtonConfig currentConfig = currentButton.getButtonConfig();
        ButtonConfig otherConfig = otherButton.getButtonConfig();
        OpaLayout currentOpa = (OpaLayout) currentButton.getParent();
        OpaLayout otherOpa = (OpaLayout) otherButton.getParent();
        currentConfig.setCustomIconUri(type, packageName, iconName);
        otherConfig.setCustomIconUri(type, packageName, iconName);
        currentButton.setButtonConfig(currentConfig);
        otherButton.setButtonConfig(otherConfig);

        mHost.setButtonDrawable(currentButton);
        SmartBarHelper.updateButtonScalingAndPadding(currentOpa, isLandscape());

        mHost.setButtonDrawable(otherButton);
        SmartBarHelper.updateButtonScalingAndPadding(otherOpa, !isLandscape());

        onCommitChanges();
    }

    protected void onImagePicked(String uri) {
        final String buttonFocus = getEditButtonTag();
        SmartButtonView currentButton = mHost.findCurrentButton(buttonFocus);
        SmartButtonView otherButton = (SmartButtonView) getHiddenNavButtons().findViewWithTag(
                buttonFocus);
        ButtonConfig currentConfig = currentButton.getButtonConfig();
        ButtonConfig otherConfig = otherButton.getButtonConfig();
        OpaLayout currentOpa = (OpaLayout) currentButton.getParent();
        OpaLayout otherOpa = (OpaLayout) otherButton.getParent();
        currentConfig.setCustomImageUri(Uri.parse(uri));
        otherConfig.setCustomImageUri(Uri.parse(uri));
        currentButton.setButtonConfig(currentConfig);
        otherButton.setButtonConfig(otherConfig);

        mHost.setButtonDrawable(currentButton);
        SmartBarHelper.updateButtonScalingAndPadding(currentOpa, isLandscape());

        mHost.setButtonDrawable(otherButton);
        SmartBarHelper.updateButtonScalingAndPadding(otherOpa, !isLandscape());

        onCommitChanges();
    }

    @Override
    protected void onActionPicked(String action, ActionConfig actionConfig) {
        final String buttonFocus = getEditButtonTag();
        final int tapFocus = mTapHasFocusTag;
        SmartButtonView currentButton = mHost.findCurrentButton(buttonFocus);
        SmartButtonView otherButton = (SmartButtonView) getHiddenNavButtons().findViewWithTag(
                buttonFocus);
        ActionConfig currentAction = new ActionConfig(mContext, action);
        ActionConfig otherAction = new ActionConfig(mContext, action);
        ButtonConfig currentConfig = currentButton.getButtonConfig();
        ButtonConfig otherConfig = otherButton.getButtonConfig();
        OpaLayout currentOpa = (OpaLayout) currentButton.getParent();
        OpaLayout otherOpa = (OpaLayout) otherButton.getParent();
        currentConfig.setActionConfig(currentAction, tapFocus);
        otherConfig.setActionConfig(otherAction, tapFocus);
        currentButton.setButtonConfig(currentConfig);
        otherButton.setButtonConfig(otherConfig);
        if (tapFocus == ActionConfig.PRIMARY) { // update icon for single tap only
            mHost.setButtonDrawable(currentButton);
            SmartBarHelper.updateButtonScalingAndPadding(currentOpa, isLandscape());
            mHost.setButtonDrawable(otherButton);
            SmartBarHelper.updateButtonScalingAndPadding(otherOpa, !isLandscape());
        }
        onCommitChanges();
    }

    private void startActionPicker(int focusActionTap) {
        mTapHasFocusTag = focusActionTap;
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setClassName(INTENT_ACTION_EDIT_CLASS, INTENT_ACTION_EDIT_COMPONENT);
        if (mTapHasFocusTag == ActionConfig.PRIMARY) { // exclude single tap back, home, recent
            String[] exclude = {
                    ActionHandler.SYSTEMUI_TASK_BACK,
                    ActionHandler.SYSTEMUI_TASK_HOME
            };
            intent.putExtra("excluded_actions", exclude);
        }
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    private int getMaxButtons() {
        return BaseNavigationBar.sIsTablet ? TABLET_MAX_BUTTONS : PHONE_MAX_BUTTONS;
    }

    private boolean getHasMaxButtons() {
        return getMaxButtons() == mHost.getCurrentSequence().size();
    }

    @Override
    public void onEditModeChanged(int mode) {
        boolean isOn = isInEditMode();
        if (isOn) {
            createPopupContainer();
        } else {
            removePopupContainer();
        }
        setButtonsEditMode(isOn);

        mHost.setEditMode(isOn);
    }

    @Override
    protected void onResetLayout() {
        mHost.recreateLayouts();
        setButtonsEditMode(isInEditMode());
    }

    private void removeButton() {
        mLockEditMode = true;
        final String buttonFocus = getEditButtonTag();
        ArrayList<ButtonConfig> buttonConfigs = Config.getConfig(mContext,
                ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        ButtonConfig toRemove = null;
        for (ButtonConfig config : buttonConfigs) {
            if (TextUtils.equals(config.getTag(), buttonFocus)) {
                toRemove = config;
                break;
            }
        }
        if (toRemove != null) {
            buttonConfigs.remove(toRemove);
            Config.setConfig(mContext, ActionConstants.getDefaults(ActionConstants.SMARTBAR),
                    buttonConfigs);
            mHost.recreateLayouts();
            setButtonsEditMode(true);
        }
        mLockEditMode = false;
    }

    private void addButton() {
        mLockEditMode = true;
        final String buttonFocus = getEditButtonTag();
        ArrayList<ButtonConfig> buttonConfigs = Config.getConfig(mContext,
                ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        int newIndex = mHost.getCurrentSequence().indexOf(buttonFocus) + 1;
        String newTag = String.valueOf(System.currentTimeMillis());
        ButtonConfig newConfig = new ButtonConfig(mContext);
        newConfig.setTag(newTag);
        buttonConfigs.add(newIndex, newConfig);
        Config.setConfig(mContext, ActionConstants.getDefaults(ActionConstants.SMARTBAR),
                buttonConfigs);
        mHost.recreateLayouts();
        setButtonsEditMode(true);
        mLockEditMode = false;
    }

    @Override
    public void onCommitChanges() {
        ArrayList<ButtonConfig> buttonConfigs = new ArrayList<ButtonConfig>();
        final int size = mHost.getCurrentSequence().size();
        for (int i = 0; i < size; i++) {
            String tag = mHost.getCurrentSequence().get(i);
            SmartButtonView v = mHost.findCurrentButton(tag);
            if (v == null)
                continue;
            ButtonConfig config = v.getButtonConfig();
            if (config == null)
                continue;
            buttonConfigs.add(config);
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
                OpaLayout opa = (OpaLayout) v.getParent();
                opa.setOnTouchListener(isInEditMode ? this : null);
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

    private Runnable mCheckShowPopup = new Runnable() {
        public void run() {
            if (isInEditMode()) {
                showPopup(POPUP_TYPE_ROOT);
            }
        }
    };

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!isInEditMode()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mIsButtonMoving = true;
            if (mPopup != null) {
                QuickAction popup = mPopup;
                popup.dismiss();
            }
            view.setPressed(true);
            view.getLocationOnScreen(sLocation);
            mDragOrigin = sLocation[mHost.isVertical() ? 1 : 0];
            mOriginPoint.set(sLocation[0], sLocation[1]);
            prepareToShowPopup(view);
            mHost.postDelayed(mCheckLongPress, QUICK_LONG_PRESS);
            mHost.postDelayed(mCheckShowPopup, POPUP_LONG_PRESS);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            // mIsButtonMoving still true;
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
            mHost.removeCallbacks(mCheckLongPress);
            mHost.removeCallbacks(mCheckShowPopup);
            if (mPopup != null) {
                QuickAction popup = mPopup;
                popup.dismiss();
            }
            switchId(affectedView, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            mIsButtonMoving = false;
            view.setPressed(false);
            mHost.removeCallbacks(mCheckLongPress);
            mHost.removeCallbacks(mCheckShowPopup);
            if (mPopup == null || !mPopup.mWindow.isShowing()) {
                mEditContainer.setVisibility(View.GONE);
            }
            if (mLongPressed) {
                // Reset the dragged view to its original location
                ViewGroup parent = (ViewGroup) view.getParent();
                boolean vertical = mHost.isVertical();
                float slideTo = vertical ? mDragOrigin - parent.getTop() : mDragOrigin
                        - parent.getLeft();
                Animator anim = getButtonSlideAnimator(view, vertical, slideTo);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.setDuration(100);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mSquatters.clear();
                        onCommitChanges();
                    }
                });
                anim.start();
            }
            mLongPressed = false;
        }
        return true;
    }

    private WindowManager.LayoutParams getEditorParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        lp.gravity = Gravity.BOTTOM;
        lp.setTitle("SmartBar Editor");
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        return lp;
    }

    private void showPopup(int type) {
        QuickAction popup = new QuickAction(mHost.getContext(), QuickAction.VERTICAL);
        boolean hasMaxButtons = getHasMaxButtons();
        String tag = getEditButtonTag();
        ActionItem item;
        if (type == POPUP_TYPE_TAP) {
            for (int i = 1; i < mTapMenuItems.size() + 1; i++) {
                item = mTapMenuItems.get(i);
                int id = item.getActionId();
                if (id == MENU_MAP_ACTIONS_SINGLE_TAP &&
                        (tag.equals(BACK) || tag.equals(HOME))) {
                    continue;
                }
                popup.addActionItem(item);
            }
        } else if (type == POPUP_TYPE_ICON) {
            for (int i = 1; i < mIconMenuItems.size() + 1; i++) {
                item = mIconMenuItems.get(i);
                int id = item.getActionId();
                if (id == MENU_MAP_ICON_ICON_COLOR) {
                    continue;
                }
                popup.addActionItem(item);
            }
        } else {
            for (int i = 1; i < mPrimaryMenuItems.size() + 1; i++) {
                item = mPrimaryMenuItems.get(i);
                int id = item.getActionId();
                if (id == MENU_MAP_ADD && hasMaxButtons) {
                    continue;
                }
                if (id == MENU_MAP_REMOVE &&
                        (tag.equals(BACK) || tag.equals(HOME))) {
                    continue;
                }
                popup.addActionItem(item);
            }
        }
        popup.setOnActionItemClickListener(mQuickClickListener);
        popup.mWindow.setOnDismissListener(mPopupDismissListener);
        popup.mWindow.setTouchInterceptor(mPopupTouchWrapper);
        popup.mWindow.setFocusable(true);
        final View anchor = (View) mHidden;
        popup.show(anchor);
        mPopup = popup;
    }

    @Override
    protected void updateResources(Resources res) {
        // anything themable is created on-the-fly
    }

    private void prepareToShowPopup(View editView) {
        mHost.removeCallbacks(mHidePopupContainer);
        ViewGroup parent = (ViewGroup) editView.getParent();
        mEditContainer.setVisibility(View.VISIBLE);
        OpaLayout opa = (OpaLayout) editView;
        mHidden.setTag(opa.getButton().getTag());
        mHidden.getLayoutParams().width = editView.getWidth();
        mHidden.getLayoutParams().height = editView.getHeight();
        mHidden.setLayoutParams(mHidden.getLayoutParams());
        mHidden.setX(mOriginPoint.x - parent.getLeft());
        mHidden.setY(mOriginPoint.y - parent.getTop());
    }

    private void createPopupContainer() {
        removePopupContainer();
        loadPrimaryMenuMap();
        loadTapMenuMap();
        loadIconMenuMap();
        mEditContainer = new FrameLayout(mHost.getContext());
        mHidden = new SmartButtonView(mHost.getContext());
        mEditContainer.setOnTouchListener(mEditorWindowTouchListener);
        mHidden.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        mHidden.setVisibility(View.INVISIBLE);
        mEditContainer.addView(mHidden);
        mEditContainer.setVisibility(View.GONE);
        mWindowManager.addView(mEditContainer, getEditorParams());
    }

    private void removePopupContainer() {
        if (mPopup != null) {
            mPopup.dismiss();
            mPopup = null;
        }
        if (mEditContainer != null && mEditContainer.isAttachedToWindow()) {
            mEditContainer.removeAllViews();
            mEditContainer.setVisibility(View.GONE);
            mWindowManager.removeViewImmediate(mEditContainer);
        }
        mPrimaryMenuItems.clear();
        mTapMenuItems.clear();
        mIconMenuItems.clear();
    }

    /*
     * Leave the reflection alone here. Since we don't inflate SmartBar, these vectors need to be
     * inflated by the library
     */
    private void loadPrimaryMenuMap() {
        mPrimaryMenuItems.clear();
        ActionItem action = new ActionItem(1,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_actions",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_actions",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mPrimaryMenuItems.put(1, action);

        action = new ActionItem(2,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_icon",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_icon",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mPrimaryMenuItems.put(2, action);

        action = new ActionItem(3,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_add",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_add",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mPrimaryMenuItems.put(3, action);

        action = new ActionItem(4,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_remove",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_remove",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mPrimaryMenuItems.put(4, action);

        action = new ActionItem(5,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_cancel",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_cancel",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mPrimaryMenuItems.put(5, action);

        action = new ActionItem(6,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_finish",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_finish",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mPrimaryMenuItems.put(6, action);
    }

    private void loadTapMenuMap() {
        mTapMenuItems.clear();
        ActionItem action = new ActionItem(7,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_single_tap",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_single_tap",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mTapMenuItems.put(1, action);

        action = new ActionItem(8,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_double_tap",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_double_tap",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mTapMenuItems.put(2, action);

        action = new ActionItem(9,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_long_press",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_long_press",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mTapMenuItems.put(3, action);
    }

    private void loadIconMenuMap() {
        mIconMenuItems.clear();
        ActionItem action = new ActionItem(10,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_icon_pack",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_icon_pack",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mIconMenuItems.put(1, action);

        action = new ActionItem(11,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_icon_gallery",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_icon_gallery",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mIconMenuItems.put(2, action);

        action = new ActionItem(12,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_icon_color",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_icon_color",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mIconMenuItems.put(3, action);

        action = new ActionItem(13,
                DUActionUtils.getString(mHost.getContext(), "label_smartbar_icon_reset",
                        DUActionUtils.PACKAGE_SYSTEMUI),
                DUActionUtils.getDrawable(mHost.getContext(), "ic_smartbar_editor_icon_reset",
                        DUActionUtils.PACKAGE_SYSTEMUI));
        mIconMenuItems.put(3, action);
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
            SmartButtonView otherButton = mHost.findCurrentButton(buttonTag);
            OpaLayout otherOpa = (OpaLayout)otherButton.getParent();
            if (otherOpa == v) {
                continue;
            }
            if (mSquatters.contains(otherOpa)) {
                continue;
            }

            otherOpa.getLocationOnScreen(sLocation);
            float otherPos = sLocation[mHost.isVertical() ? 1 : 0];
            float otherDimension = mHost.isVertical() ? v.getHeight() : v.getWidth();

            if (pos > (otherPos + otherDimension / 4) && pos < (otherPos + otherDimension)) {
                mSquatters.add(otherOpa);
                return otherOpa;
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
        final OpaLayout squatter = (OpaLayout) replaceView;
        final OpaLayout dragger = (OpaLayout) dragView;

        final boolean vertical = mHost.isVertical();

        ViewGroup parent = (ViewGroup) replaceView.getParent();
        float slideTo = vertical ? mDragOrigin - parent.getTop() : mDragOrigin - parent.getLeft();
        replaceView.getLocationOnScreen(sLocation);
        mDragOrigin = sLocation[vertical ? 1 : 0];

        final int targetIndex = mHost.getCurrentSequence().indexOf(squatter.getButton().getTag());
        final int draggedIndex = mHost.getCurrentSequence().indexOf(dragger.getButton().getTag());
        Collections.swap(mHost.getCurrentSequence(), draggedIndex, targetIndex);

        SmartButtonView hidden1 = (SmartButtonView) getHiddenNavButtons().findViewWithTag(
                squatter.getButton().getTag());
        SmartButtonView hidden2 = (SmartButtonView) getHiddenNavButtons().findViewWithTag(
                dragger.getButton().getTag());
        OpaLayout hidden1Opa = (OpaLayout) hidden1.getParent();
        OpaLayout hidden2Opa = (OpaLayout) hidden2.getParent();
        swapConfigs(hidden1Opa, hidden2Opa);

        Animator anim = getButtonSlideAnimator(squatter, vertical, slideTo);
        anim.setInterpolator(new OvershootInterpolator());
        anim.setDuration(250);
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

    private void swapConfigs(OpaLayout v1, OpaLayout v2) {
        ButtonConfig config1 = v1.getButton().getButtonConfig();
        ButtonConfig config2 = v2.getButton().getButtonConfig();
        int[] padding1 = SmartBarHelper.getViewPadding(v1);
        int[] padding2 = SmartBarHelper.getViewPadding(v2);
        ScaleType scale1 = v1.getButton().getScaleType();
        ScaleType scale2 = v2.getButton().getScaleType();
        v1.getButton().setButtonConfig(config2);
        v2.getButton().setButtonConfig(config1);
        mHost.setButtonDrawable(v1.getButton());
        mHost.setButtonDrawable(v2.getButton());
        SmartBarHelper.applyPaddingToView(v1, padding2);
        SmartBarHelper.applyPaddingToView(v2, padding1);
        v1.getButton().setScaleType(scale2);
        v2.getButton().setScaleType(scale1);
    }

    private ViewGroup getHiddenNavButtons() {
        return (ViewGroup) mHost.getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
    }

}
