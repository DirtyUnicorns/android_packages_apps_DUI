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
 * AOSP based Softkey navigation implementation and action executor
 * 
 */

package com.android.internal.navigation.smartbar;

import android.app.StatusBarManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.ActionHandler;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.Config.ActionConfig;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.internal.navigation.BarTransitions;
import com.android.internal.navigation.BaseEditor;
import com.android.internal.navigation.BaseNavigationBar;
import com.android.internal.navigation.Res;
import com.android.internal.navigation.StatusbarImpl;
import com.android.internal.navigation.utils.SmartObserver.SmartObservable;
import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SmartBarView extends BaseNavigationBar {
    final static boolean DEBUG = false;
    final static String TAG = SmartBarView.class.getSimpleName();

    boolean mShowMenu;
    int mNavigationIconHints = 0;

    private SmartBackButtonDrawable mBackIcon, mBackLandIcon;
    private Drawable mHomeIcon, mHomeLandIcon;
    private Drawable mRecentIcon, mRecentLandIcon;
    private Drawable mMenuIcon, mMenuLandIcon;
    private Drawable mImeIcon;

    private final SmartBarTransitions mBarTransitions;
    private SmartActionHandler mActionHandler;
//    private SmartBarEditor mEditor;

    // hold a reference to primary buttons in order of appearance on screen
    private ArrayList<String> mCurrentSequence = new ArrayList<String>();
    private View mContextRight, mContextLeft, mCurrentContext;
    private boolean mHasLeftContext;

    // editor stuff
    private boolean mInEditMode = false;

    public SmartBarView(Context context) {
        super(context);
        mActionHandler = new SmartActionHandler(this);
        mBarTransitions = new SmartBarTransitions(this);
//        mEditor = new SmartBarEditor(this, false);
        createBaseViews();
    }

    ArrayList<String> getCurrentSequence() {
        return mCurrentSequence;
    }

    @Override
    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    @Override
    public void setListeners(OnTouchListener homeActionListener,
            OnLongClickListener homeLongClickListener,
            OnTouchListener userAutoHideListener,
            OnClickListener recentsClickListener,
            OnTouchListener recentsTouchListener,
            OnLongClickListener recentsLongClickListener) {
        super.setListeners(homeActionListener, homeLongClickListener, userAutoHideListener,
                recentsClickListener, recentsTouchListener, recentsLongClickListener);
        setOnTouchListener(mUserAutoHideListener);
    }

    private void getIcons(Resources res) {
        mBackIcon = new SmartBackButtonDrawable(res.getDrawable(
                getDrawableId(Res.Softkey.IC_SYSBAR_BACK), null));
        mBackLandIcon = new SmartBackButtonDrawable(res.getDrawable(
                getDrawableId(Res.Softkey.IC_SYSBAR_BACK_LAND), null));
        mRecentIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_SYSBAR_RECENT), null);
        mRecentLandIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_SYSBAR_RECENT_LAND), null);
        mHomeIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_SYSBAR_HOME), null);
        mHomeLandIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_SYSBAR_HOME_LAND), null);
        mMenuIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_SYSBAR_MENU), null);
        mMenuLandIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_SYSBAR_MENU_LAND), null);
        mImeIcon = res.getDrawable(getDrawableId(Res.Softkey.IC_IME_SWITCHER_DEF), null);
    }

    private int getDrawableId(String name) {
        return DUActionUtils.getIdentifier(mContext, name, DUActionUtils.DRAWABLE,
                DUActionUtils.PACKAGE_SYSTEMUI);
    }

    @Override
    protected void onUpdateResources(Resources res) {
        getIcons(res);
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            ViewGroup lightsOut = (ViewGroup) container.findViewWithTag(Res.Common.LIGHTS_OUT);
            if (lightsOut != null) {
                final int nChildren = lightsOut.getChildCount();
                for (int j = 0; j < nChildren; j++) {
                    final View child = lightsOut.getChildAt(j);
                    if (child instanceof ImageView) {
                        final ImageView iv = (ImageView) child;
                        // clear out the existing drawable, this is required since the
                        // ImageView keeps track of the resource ID and if it is the same
                        // it will not update the drawable.
                        iv.setImageDrawable(null);
                        iv.setImageDrawable(getAvailableResources().getDrawable(
                                getDrawableId(Res.Common.LIGHTS_OUT_LARGE), null));
                    }
                }
            }

            ViewGroup right = (ViewGroup) container.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
            ImageView ime = (ImageView) right.findViewWithTag(Res.Softkey.IME_SWITCHER);
            if (ime != null) {
                ime.setImageDrawable(null);
                ime.setImageDrawable(mImeIcon);
            }
            ViewGroup left = (ViewGroup) container.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
            ime = (ImageView) left.findViewWithTag(Res.Softkey.IME_SWITCHER);
            if (ime != null) {
                ime.setImageDrawable(null);
                ime.setImageDrawable(mImeIcon);
            }

            ViewGroup nav = (ViewGroup) container.findViewWithTag(Res.Common.NAV_BUTTONS);
            if (nav != null) {
                final int nButtons = nav.getChildCount();
                for (int k = 0; k < nButtons; k++) {
                    final View child = nav.getChildAt(k);
                    if (child instanceof SmartButtonView) {
                        final SmartButtonView button = (SmartButtonView) child;
                        updateSmartButtonIcon(button);
                    }
                }
            }
        }
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(getAvailableResources());
        super.setLayoutDirection(layoutDirection);
    }

    @Override
    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    @Override
    public SmartButtonView getBackButton() {
        return (SmartButtonView) mCurrentView.findViewWithTag(Res.Softkey.BUTTON_BACK);
    }

    @Override
    public SmartButtonView getHomeButton() {
        return (SmartButtonView) mCurrentView.findViewWithTag(Res.Softkey.BUTTON_HOME);
    }

    @Override
    public SmartButtonView getRecentsButton() {
        return (SmartButtonView) mCurrentView.findViewWithTag(Res.Softkey.BUTTON_OVERVIEW);
    }

    @Override
    public SmartButtonView getMenuButton() {
        return (SmartButtonView) mCurrentContext.findViewWithTag(Res.Softkey.MENU_BUTTON);
    }

    SmartButtonView getImeSwitchButton() {
        return (SmartButtonView) mCurrentContext.findViewWithTag(Res.Softkey.IME_SWITCHER);
    }

    SmartButtonView findCurrentButton(String tag) {
        return (SmartButtonView) mCurrentView.findViewWithTag(tag);
    }

    boolean buttonHasCustomIcon(SmartButtonView v) {
        return v.getButtonConfig().hasCustomIcon();
    }

    private Drawable getDefaultDrawable(String tag) {
        if (tag.equals(Res.Softkey.BUTTON_BACK)) {
            return mVertical ? mBackLandIcon : mBackIcon;
        } else if (tag.equals(Res.Softkey.BUTTON_HOME)) {
            return mVertical ? mHomeLandIcon : mHomeIcon;
        } else if (tag.equals(Res.Softkey.BUTTON_OVERVIEW)) {
            return mVertical ? mRecentLandIcon : mRecentIcon;
        } else {
            return null;
        }
    }

    // all buttons with custom icons and buttons using default action
    // associated icons, but not the icons we manage manually
    private void updateSmartButtonIcon(SmartButtonView v) {
        v.setImageDrawable(null);
        if (buttonHasCustomIcon(v) || !isSuperPrimaryButton(v)) {
            v.setImageDrawable(v.getButtonConfig().getCurrentIcon(mContext));
        } else {
            v.setImageDrawable(getDefaultDrawable((String) v.getTag()));
        }
    }

    // is back, home, or overview
    private boolean isSuperPrimaryButton(SmartButtonView v) {
        String tag = (String) v.getTag();
        if (TextUtils.isEmpty(tag)) {
            return false;
        }
        return TextUtils.equals(tag, Res.Softkey.BUTTON_BACK)
                || TextUtils.equals(tag, Res.Softkey.BUTTON_HOME)
                || TextUtils.equals(tag, Res.Softkey.BUTTON_OVERVIEW);
    }

    @Override
    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints)
            return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;

        mNavigationIconHints = hints;

        if (!buttonHasCustomIcon(getBackButton())) {
            getBackButton().setImageDrawable(null);
            getBackButton().setImageDrawable(getDefaultDrawable(Res.Softkey.BUTTON_BACK));
            mBackLandIcon.setImeVisible(backAlt);
            mBackIcon.setImeVisible(backAlt);
        }

        updateSmartButtonIcon(getHomeButton());
        updateSmartButtonIcon(getRecentsButton());

        // TODO: we may not need this
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null) {
                if (!isSuperPrimaryButton(v)) {
                    updateSmartButtonIcon(v);
                }
            }
        }

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0);
        getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);

        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);
        setDisabledFlags(mDisabledFlags, true);
    }

    @Override
    public void setDisabledFlags(int disabledFlags, boolean force) {
        super.setDisabledFlags(disabledFlags, force);

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);

        getBackButton()   .setVisibility(disableBack       ? View.INVISIBLE : View.VISIBLE);
        getHomeButton()   .setVisibility(disableHome       ? View.INVISIBLE : View.VISIBLE);
        getRecentsButton().setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);

        // if any stock buttons are disabled, it's likely proper
        // to disable custom buttons as well
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null) {
                if (!isSuperPrimaryButton(v)) {
                    if (disableHome || disableBack || disableRecent) {
                        v.setVisibility(View.INVISIBLE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
    }

    @Override
    protected void onKeyguardShowing(boolean showing) {
        mActionHandler.setKeyguardShowing(showing);
    }

    @Override
    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    @Override
    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show)
            return;

        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
        getMenuButton().setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void createBaseViews() {
        super.createBaseViews();
        getIcons(getResources());
        ArrayList<ButtonConfig> buttonConfigs = Config.getConfig(mContext,
                ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        recreateButtonLayout(buttonConfigs, false, true);
        recreateButtonLayout(buttonConfigs, true, false);
//        updateSettings();
    }

    @Override
    protected void onDispose() {
    }

    @Override
    public void reorient() {
//        mEditor.prepareToReorient();
        super.reorient();
        mBarTransitions.init(mVertical);
//        mEditor.reorient(mCurrentView == mRot90);
        mContextLeft = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        mContextRight = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
        setDisabledFlags(mDisabledFlags, true);
        setMenuVisibility(mShowMenu, true);
        setNavigationIconHints(mNavigationIconHints, true);
    }

    private void updateEditMode() {
        boolean doEdit = Settings.System.getIntForUser(mContext.getContentResolver(),
                "smartbar_edit_mode", 0, UserHandle.USER_CURRENT) == 1;
        if (doEdit != mInEditMode) {
            mInEditMode = doEdit;
//            mEditor.changeEditMode(doEdit ? BaseEditor.MODE_ON : BaseEditor.MODE_OFF);
        }
    }

    void recreateButtonLayout(ArrayList<ButtonConfig> buttonConfigs, boolean landscape,
            boolean updateCurrentButtons) {
        int extraKeyWidth = DUActionUtils
                .getDimenPixelSize(mContext, Res.Softkey.EXTRA_KEY_WIDTH,
                        DUActionUtils.PACKAGE_SYSTEMUI);

        int extraKeyHeight = DUActionUtils
                .getDimenPixelSize(mContext, Res.Softkey.EXTRA_KEY_HEIGHT,
                        DUActionUtils.PACKAGE_SYSTEMUI);

        LinearLayout navButtonLayout = (LinearLayout) (landscape ? mRot90
                .findViewWithTag(Res.Common.NAV_BUTTONS) : mRot0
                .findViewWithTag(Res.Common.NAV_BUTTONS));

        LinearLayout lightsOut = (LinearLayout) (landscape ? mRot90
                .findViewWithTag(Res.Common.LIGHTS_OUT) : mRot0
                .findViewWithTag(Res.Common.LIGHTS_OUT));

        navButtonLayout.removeAllViews();
        lightsOut.removeAllViews();

        if (buttonConfigs == null) {
            buttonConfigs = Config.getConfig(mContext,
                    ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        }

        // left context frame layout
        FrameLayout leftContext = generateContextKeyLayout(landscape,
                Res.Softkey.CONTEXT_VIEW_LEFT,
                extraKeyWidth, extraKeyHeight);
        SmartBarHelper.addViewToRoot(navButtonLayout, leftContext, landscape);
        SmartBarHelper.addLightsOutButton(mContext, lightsOut, leftContext, landscape, true);

        // tablets get a spacer here
        if (mIsTablet) {
            SmartBarHelper.addViewToRoot(navButtonLayout, SmartBarHelper.makeSeparator(mContext),
                    landscape);
            SmartBarHelper.addLightsOutButton(mContext, lightsOut,
                    SmartBarHelper.makeSeparator(mContext), landscape, true);
        }

        // softkey buttons
        ButtonConfig buttonConfig;
        int dimen = SmartBarHelper.getButtonSize(mContext, buttonConfigs.size(), landscape);

        for (int j = 0; j < buttonConfigs.size(); j++) {
            buttonConfig = buttonConfigs.get(j);
            SmartButtonView v = SmartBarHelper.generatePrimaryKey(mContext, landscape,
                    mActionHandler, buttonConfig);
            SmartBarHelper.updateButtonSize(v, dimen, landscape);
            SmartBarHelper.addViewToRoot(navButtonLayout, v, landscape);
            SmartBarHelper.addLightsOutButton(mContext, lightsOut, v, landscape, false);

            // only add once for master sequence holder
            if (updateCurrentButtons) {
                mCurrentSequence.add((String)v.getTag());
            }

            // phones get a spacer between each button
            // tablets get a spacer before first and after last
            if (j != buttonConfigs.size() - 1 && !mIsTablet) {
                // adding spacers between buttons on phones
                SmartBarHelper.addViewToRoot(navButtonLayout,
                        SmartBarHelper.makeSeparator(mContext), landscape);
                SmartBarHelper.addLightsOutButton(mContext, lightsOut,
                        SmartBarHelper.makeSeparator(mContext), landscape, true);
            }
            if (j == buttonConfigs.size() && mIsTablet) {
                // adding spacers after last button on tablets
                SmartBarHelper.addViewToRoot(navButtonLayout,
                        SmartBarHelper.makeSeparator(mContext), landscape);
                SmartBarHelper.addLightsOutButton(mContext, lightsOut,
                        SmartBarHelper.makeSeparator(mContext), landscape, true);
            }
        }

        // right context frame layout
        FrameLayout rightContext = generateContextKeyLayout(landscape,
                Res.Softkey.CONTEXT_VIEW_RIGHT,
                extraKeyWidth, extraKeyHeight);
        SmartBarHelper.addViewToRoot(navButtonLayout, rightContext, landscape);
        SmartBarHelper.addLightsOutButton(mContext, lightsOut, rightContext, landscape, true);
    }

    private FrameLayout generateContextKeyLayout(boolean landscape, String leftOrRight,
            int extraKeyWidth, int extraKeyHeight) {
        FrameLayout contextLayout = new FrameLayout(mContext);
        contextLayout.setLayoutParams(new LinearLayout.LayoutParams(
                landscape && !mIsTablet ? LayoutParams.MATCH_PARENT
                        : extraKeyWidth, landscape && !mIsTablet ? extraKeyHeight
                        : LayoutParams.MATCH_PARENT));
        contextLayout.setTag(leftOrRight);

        // add left menu to left context frame layout
        SmartButtonView menuKeyView = generateContextKey(landscape, Res.Softkey.MENU_BUTTON);
        contextLayout.addView(menuKeyView);

        // add left ime changer to left context frame layout
        SmartButtonView imeChanger = generateContextKey(landscape, Res.Softkey.IME_SWITCHER);
        contextLayout.addView(imeChanger);
        return contextLayout;
    }

    private SmartButtonView generateContextKey(boolean landscape, String tag) {
        SmartButtonView v = new SmartButtonView(mContext, mActionHandler);
        ButtonConfig buttonConfig = new ButtonConfig(mContext);
        ActionConfig actionConfig;
        Drawable d;
        String desc = null;

        int width = DUActionUtils
                .getDimenPixelSize(mContext, Res.Softkey.EXTRA_KEY_WIDTH,
                        DUActionUtils.PACKAGE_SYSTEMUI);

        int height = DUActionUtils
                .getDimenPixelSize(mContext, Res.Softkey.EXTRA_KEY_HEIGHT,
                        DUActionUtils.PACKAGE_SYSTEMUI);

        v.setLayoutParams(new FrameLayout.LayoutParams(landscape && !mIsTablet ? LayoutParams.MATCH_PARENT : width,
                landscape && !mIsTablet ? height : LayoutParams.MATCH_PARENT));
        v.setScaleType(ScaleType.CENTER_INSIDE);

        if (tag.equals(Res.Softkey.MENU_BUTTON)) {
            actionConfig = new ActionConfig(mContext, ActionHandler.SYSTEMUI_TASK_MENU);
            desc = DUActionUtils.getString(mContext, Res.Softkey.MENU_DESC,
                    DUActionUtils.PACKAGE_SYSTEMUI);
            d = landscape && !mIsTablet ? mMenuLandIcon : mMenuIcon;
        } else {
            actionConfig = new ActionConfig(mContext, ActionHandler.SYSTEMUI_TASK_IME_SWITCHER);
            desc = DUActionUtils.getString(mContext, Res.Softkey.IME_DESC,
                    DUActionUtils.PACKAGE_SYSTEMUI);
            d = mImeIcon;
        }

        buttonConfig.setActionConfig(actionConfig, ActionConfig.PRIMARY);
        buttonConfig.setTag(tag);
        v.setButtonConfig(buttonConfig);
        v.setVisibility(View.INVISIBLE);
        v.setImageDrawable(d);
        if (desc != null) {
            v.setContentDescription(desc);
        }
        return v;
    }

    @Override
    public void setStatusBarCallbacks(StatusbarImpl statusbar) {
        // TODO Auto-generated method stub        
    }

    @Override
    public boolean onStartPulse(Animation animatePulseIn) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onStopPulse(Animation animatePulseOut) {
        // TODO Auto-generated method stub
        
    }
}
