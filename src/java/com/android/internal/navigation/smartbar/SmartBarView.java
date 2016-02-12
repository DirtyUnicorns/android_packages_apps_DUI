/**
 * Copyright (C) 2016 The DirtyUnicorns Project
 * Copyright (C) 2014 SlimRoms
 * 
 * @author: Randall Rushing <randall.rushing@gmail.com>
 *
 * Much love and respect to SlimRoms for writing and inspiring
 * some of the dynamic layout methods
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
 * A new software key based navigation implementation that just vaporizes
 * AOSP and quite frankly everything currently on the custom firmware scene
 *
 */

package com.android.internal.navigation.smartbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SmartBarView extends BaseNavigationBar {
    final static boolean DEBUG = false;
    final static String TAG = SmartBarView.class.getSimpleName();
    final static float PULSE_ALPHA_FADE = 0.3f; // take bar alpha low so keys are vaguely visible
                                                // but not intrusive during Pulse
    final static int PULSE_FADE_OUT_DURATION = 250;
    final static int PULSE_FADE_IN_DURATION = 200;

    static final int IME_HINT_MODE_HIDDEN = 0;
    static final int IME_HINT_MODE_ARROWS = 1;
    static final int IME_HINT_MODE_PICKER = 2;

    private static Set<Uri> sUris = new HashSet<Uri>();    
    static {
        sUris.add(Settings.Secure.getUriFor("smartbar_context_menu_mode"));
        sUris.add(Settings.Secure.getUriFor("smartbar_ime_hint_mode"));
    }

    private SmartObservable mObservable = new SmartObservable() {
        @Override
        public Set<Uri> onGetUris() {
            return sUris;
        }

        @Override
        public void onChange(Uri uri) {
            if (uri.equals(Settings.Secure.getUriFor("smartbar_context_menu_mode"))) {
                updateContextLayoutSettings();
            } else if (uri.equals(Settings.Secure.getUriFor("smartbar_ime_hint_mode"))) {
                updateImeHintModeSettings();
                refreshImeHintMode();
            }
        }
    };

    boolean mShowMenu;
    int mNavigationIconHints = 0;

    private final SmartBarTransitions mBarTransitions;
    private SmartBarEditor mEditor;

    // hold a reference to primary buttons in order of appearance on screen
    private ArrayList<String> mCurrentSequence = new ArrayList<String>();
    private View mContextRight, mContextLeft, mCurrentContext;
    private boolean mHasLeftContext;
    private int mImeHintMode;

    public SmartBarView(Context context) {
        super(context);
        mBarTransitions = new SmartBarTransitions(this);
        mEditor = new SmartBarEditor(this);
        mSmartObserver.addListener(mObservable);
        createBaseViews();
        updateCurrentIcons(true);
        updateImeHintModeSettings();
        updateContextLayoutSettings();
    }

    ArrayList<String> getCurrentSequence() {
        return mCurrentSequence;
    }

    @Override
    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    @Override
    public void screenPinningStateChanged(boolean enabled) {
        super.screenPinningStateChanged(enabled);
        mEditor.screenPinningStateChanged(enabled);
        setScreenPinningVisibility();
    }

    @Override
    protected void onInflateFromUser() {
        mEditor.notifyScreenOn(mScreenOn);
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

    private int getDrawableId(String name) {
        return DUActionUtils.getIdentifier(mContext, name, DUActionUtils.DRAWABLE,
                DUActionUtils.PACKAGE_SYSTEMUI);
    }

    @Override
    protected void onUpdateResources(Resources res) {
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

            // right context button layout
            ViewGroup right = (ViewGroup) container.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
            SmartButtonView ime = (SmartButtonView) right.findViewWithTag(Res.Softkey.IME_SWITCHER);
            if (ime != null) {
                ime.setImageDrawable(null);
                ime.setImageDrawable(ime.getButtonConfig().getCurrentIcon(mContext));
            }
            SmartButtonView pinning = (SmartButtonView) right.findViewWithTag(Res.Softkey.STOP_SCREENPINNING);
            if (pinning != null) {
                pinning.setImageDrawable(null);
                pinning.setImageDrawable(pinning.getButtonConfig().getCurrentIcon(mContext));
            }
            SmartButtonView imeRight = (SmartButtonView) right.findViewWithTag(Res.Softkey.IME_ARROW_RIGHT);
            if (imeRight != null) {
                imeRight.setImageDrawable(null);
                imeRight.setImageDrawable(imeRight.getButtonConfig().getCurrentIcon(mContext));
            }
            SmartButtonView menuRight = (SmartButtonView) right.findViewWithTag(Res.Softkey.MENU_BUTTON);
            if (menuRight != null) {
                menuRight.setImageDrawable(null);
                menuRight.setImageDrawable(menuRight.getButtonConfig().getCurrentIcon(mContext));
            }

            // left context layout
            ViewGroup left = (ViewGroup) container.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
            ime = (SmartButtonView) left.findViewWithTag(Res.Softkey.IME_SWITCHER);
            if (ime != null) {
                ime.setImageDrawable(null);
                ime.setImageDrawable(ime.getButtonConfig().getCurrentIcon(mContext));
            }
            pinning = (SmartButtonView) left.findViewWithTag(Res.Softkey.STOP_SCREENPINNING);
            if (pinning != null) {
                pinning.setImageDrawable(null);
                pinning.setImageDrawable(pinning.getButtonConfig().getCurrentIcon(mContext));
            }
            SmartButtonView imeLeft = (SmartButtonView) left.findViewWithTag(Res.Softkey.IME_ARROW_LEFT);
            if (imeLeft != null) {
                imeLeft.setImageDrawable(null);
                imeLeft.setImageDrawable(imeLeft.getButtonConfig().getCurrentIcon(mContext));
            }
            SmartButtonView menuLeft = (SmartButtonView) left.findViewWithTag(Res.Softkey.MENU_BUTTON);
            if (menuLeft != null) {
                menuLeft.setImageDrawable(null);
                menuLeft.setImageDrawable(menuLeft.getButtonConfig().getCurrentIcon(mContext));
            }

            ViewGroup nav = (ViewGroup) container.findViewWithTag(Res.Common.NAV_BUTTONS);
            if (nav != null) {
                final int nButtons = nav.getChildCount();
                for (int k = 0; k < nButtons; k++) {
                    final View child = nav.getChildAt(k);
                    if (child instanceof SmartButtonView) {
                        final SmartButtonView button = (SmartButtonView) child;
                        button.setImageDrawable(null);
                        button.setImageDrawable(button.getButtonConfig().getCurrentIcon(mContext));
                    }
                }
            }
        }
        updateCurrentIcons(true);
        mEditor.updateResources(res);
    }

    void updateCurrentIcons(boolean updateBack) {
        if (updateBack) {
            SmartBackButtonDrawable currentBackIcon = new SmartBackButtonDrawable(getBackButton()
                    .getButtonConfig()
                    .getCurrentIcon(mContext));
            getBackButton().setImageDrawable(currentBackIcon);

            SmartButtonView hiddenBack = (SmartButtonView) getHiddenView().findViewWithTag(
                    Res.Softkey.BUTTON_BACK);
            SmartBackButtonDrawable hiddenBackIcon = new SmartBackButtonDrawable(hiddenBack
                    .getButtonConfig()
                    .getCurrentIcon(mContext));
            hiddenBack.setImageDrawable(hiddenBackIcon);
            currentBackIcon
                    .setImeVisible((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0);
        }

        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null && v != getBackButton()) {
                v.setImageDrawable(null);
                v.setImageDrawable(v.getButtonConfig().getCurrentIcon(mContext));
            }
        }
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

    SmartButtonView getScreenPinningButton() {
        // screenpinning button always in opposing context view
        ViewGroup group = (ViewGroup) (mHasLeftContext ? mContextRight : mContextLeft);
        return (SmartButtonView) group.findViewWithTag(Res.Softkey.STOP_SCREENPINNING);
    }

    SmartBackButtonDrawable getBackButtonIcon() {
        return (SmartBackButtonDrawable) getBackButton().getDrawable();
    }

    private void setImeArrowsVisibility(View currentOrHidden, int visibility) {
        ViewGroup contextLeft = (ViewGroup)currentOrHidden.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        contextLeft.findViewWithTag(Res.Softkey.IME_ARROW_LEFT).setVisibility(visibility);
        ViewGroup contextRight = (ViewGroup)currentOrHidden.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        contextRight.findViewWithTag(Res.Softkey.IME_ARROW_RIGHT).setVisibility(visibility);
    }

    @Override
    protected boolean areAnyHintsActive() {
        return super.areAnyHintsActive() || mShowMenu;
    }

    @Override
    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints)
            return;
        mEditor.changeEditMode(BaseEditor.MODE_OFF);
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;

        mNavigationIconHints = hints;
        getBackButtonIcon().setImeVisible(backAlt);

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0);
        switch(mImeHintMode) {
            case IME_HINT_MODE_HIDDEN: // always hidden
                getImeSwitchButton().setVisibility(View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, View.INVISIBLE);
                break;
            case IME_HINT_MODE_PICKER:
                getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, View.INVISIBLE);
                break;
            default: // arrows
                getImeSwitchButton().setVisibility(View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, showImeButton ? View.VISIBLE : View.INVISIBLE);
        }

        // Update menu button in case the IME state has changed.
        setScreenPinningVisibility();
        setMenuVisibility(mShowMenu, true);
        setDisabledFlags(mDisabledFlags, true);
    }

    @Override
    public void setDisabledFlags(int disabledFlags, boolean force) {
        super.setDisabledFlags(disabledFlags, force);
        mEditor.changeEditMode(BaseEditor.MODE_OFF);

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);

        getBackButton().setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        getHomeButton().setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);
        getRecentsButton().setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);

        // if any stock buttons are disabled, it's likely proper
        // to disable custom buttons as well
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null && v != getBackButton() && v != getHomeButton()
                    && v != getRecentsButton()) {
                if (disableHome || disableBack || disableRecent) {
                    v.setVisibility(View.INVISIBLE);
                } else {
                    v.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void setScreenPinningVisibility() {
        final boolean imeOccupying = ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0)
                && mImeHintMode == IME_HINT_MODE_ARROWS;
        getScreenPinningButton().setVisibility(
                mScreenPinningEnabled && !imeOccupying ? VISIBLE : INVISIBLE);
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        super.notifyScreenOn(screenOn);
        mEditor.notifyScreenOn(screenOn);
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null) {
                v.onScreenStateChanged(screenOn);
            }
        }
    }

    @Override
    protected void onKeyguardShowing(boolean showing) {
        SmartButtonView.setKeyguardShowing(showing);
        mEditor.setKeyguardShowing(showing);
    }

    @Override
    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    @Override
    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show)
            return;
        mEditor.changeEditMode(BaseEditor.MODE_OFF);
        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
        getMenuButton().setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void createBaseViews() {
        super.createBaseViews();
        recreateLayouts();
    }

    void recreateLayouts() {
        mCurrentSequence.clear();
        ArrayList<ButtonConfig> buttonConfigs = Config.getConfig(mContext,
                ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        recreateButtonLayout(buttonConfigs, false, true);
        recreateButtonLayout(buttonConfigs, true, false);
        SmartButtonView.setKeyguardShowing(isKeyguardShowing());
        mContextLeft = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        mContextRight = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
        updateCurrentIcons(true);
        setDisabledFlags(mDisabledFlags, true);
        setScreenPinningVisibility();
        setMenuVisibility(mShowMenu, true);
        setNavigationIconHints(mNavigationIconHints, true);
    }

    @Override
    protected void onDispose() {
        mEditor.unregister();
    }

    @Override
    public void reorient() {
        mEditor.prepareToReorient();
        super.reorient();
        mBarTransitions.init();
        mEditor.reorient(mCurrentView == mRot90);
        mContextLeft = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        mContextRight = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
        updateCurrentIcons(true);
        setDisabledFlags(mDisabledFlags, true);
        setScreenPinningVisibility();
        setMenuVisibility(mShowMenu, true);
        setNavigationIconHints(mNavigationIconHints, true);
    }

    private void updateContextLayoutSettings() {
        boolean onLeft = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                "smartbar_context_menu_mode", 0, UserHandle.USER_CURRENT) == 1;
        if (mHasLeftContext != onLeft) {
            getMenuButton().setVisibility(INVISIBLE);
            getImeSwitchButton().setVisibility(INVISIBLE);
            getScreenPinningButton().setVisibility(INVISIBLE);
            mHasLeftContext = onLeft;
            mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
            setDisabledFlags(mDisabledFlags, true);
            setScreenPinningVisibility();
            setMenuVisibility(mShowMenu, true);
            setNavigationIconHints(mNavigationIconHints, true);
        }
    }

    private void updateImeHintModeSettings() {
        mImeHintMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                "smartbar_ime_hint_mode", IME_HINT_MODE_ARROWS, UserHandle.USER_CURRENT);
    }

    private void refreshImeHintMode() {
        getMenuButton().setVisibility(INVISIBLE);
        getImeSwitchButton().setVisibility(INVISIBLE);
        getScreenPinningButton().setVisibility(INVISIBLE);
        setNavigationIconHints(mNavigationIconHints, true);
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
            SmartButtonView v = SmartBarHelper.generatePrimaryKey(mContext, landscape, buttonConfig);
            SmartBarHelper.updateButtonSize(v, dimen, landscape);
            SmartBarHelper.addViewToRoot(navButtonLayout, v, landscape);
            SmartBarHelper.addLightsOutButton(mContext, lightsOut, v, landscape, false);

            // only add once for master sequence holder
            if (updateCurrentButtons) {
                mCurrentSequence.add((String) v.getTag());
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
            if (j == buttonConfigs.size() - 1 && mIsTablet) {
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

        SmartButtonView menuKeyView = generateContextKey(landscape, Res.Softkey.MENU_BUTTON);
        contextLayout.addView(menuKeyView);

        SmartButtonView imeChanger = generateContextKey(landscape, Res.Softkey.IME_SWITCHER);
        contextLayout.addView(imeChanger);

        SmartButtonView stopScreenpinning = generateContextKey(landscape, Res.Softkey.STOP_SCREENPINNING);
        contextLayout.addView(stopScreenpinning);

        if (TextUtils.equals(Res.Softkey.CONTEXT_VIEW_LEFT, leftOrRight)) {
            SmartButtonView imeArrowLeft = generateContextKey(landscape, Res.Softkey.IME_ARROW_LEFT);
            contextLayout.addView(imeArrowLeft);
        } else if (TextUtils.equals(Res.Softkey.CONTEXT_VIEW_RIGHT, leftOrRight)) {
            SmartButtonView imeArrowRight = generateContextKey(landscape, Res.Softkey.IME_ARROW_RIGHT);
            contextLayout.addView(imeArrowRight);
        }

        return contextLayout;
    }

    private SmartButtonView generateContextKey(boolean landscape, String tag) {
        SmartButtonView v = new SmartButtonView(mContext);
        ButtonConfig buttonConfig = new ButtonConfig(mContext);
        ActionConfig actionConfig;

        int width = DUActionUtils
                .getDimenPixelSize(mContext, Res.Softkey.EXTRA_KEY_WIDTH,
                        DUActionUtils.PACKAGE_SYSTEMUI);

        int height = DUActionUtils
                .getDimenPixelSize(mContext, Res.Softkey.EXTRA_KEY_HEIGHT,
                        DUActionUtils.PACKAGE_SYSTEMUI);

        v.setLayoutParams(new FrameLayout.LayoutParams(
                landscape && !mIsTablet ? LayoutParams.MATCH_PARENT : width,
                landscape && !mIsTablet ? height : LayoutParams.MATCH_PARENT));
        v.loadRipple();
        v.setScaleType(ScaleType.CENTER_INSIDE);

        if (tag.equals(Res.Softkey.MENU_BUTTON)) {
            actionConfig = new ActionConfig(mContext, ActionHandler.SYSTEMUI_TASK_MENU);
        } else if (tag.equals(Res.Softkey.IME_SWITCHER)) {
            actionConfig = new ActionConfig(mContext, ActionHandler.SYSTEMUI_TASK_IME_SWITCHER);
        } else if (tag.equals(Res.Softkey.IME_ARROW_LEFT)) {
            actionConfig = new ActionConfig(mContext, ActionHandler.SYSTEMUI_TASK_IME_NAVIGATION_LEFT);
        } else if (tag.equals(Res.Softkey.IME_ARROW_RIGHT)) {
            actionConfig = new ActionConfig(mContext, ActionHandler.SYSTEMUI_TASK_IME_NAVIGATION_RIGHT);
        } else {
            actionConfig = new ActionConfig(mContext,
                    ActionHandler.SYSTEMUI_TASK_STOP_SCREENPINNING);
        }

        buttonConfig.setActionConfig(actionConfig, ActionConfig.PRIMARY);
        buttonConfig.setTag(tag);
        v.setButtonConfig(buttonConfig);
        v.setVisibility(View.INVISIBLE);
        v.setImageDrawable(null);
        v.setImageDrawable(buttonConfig.getCurrentIcon(mContext));
        v.setContentDescription(buttonConfig.getActionConfig(ActionConfig.PRIMARY).getLabel());
        return v;
    }

    @Override
    public void setStatusBarCallbacks(StatusbarImpl statusbar) {
        // TODO Auto-generated method stub
    }

    boolean isBarPulseFaded() {
        if (mPulse == null) {
            return false;
        } else {
            return mPulse.shouldDrawPulse();
        }
    }

    @Override
    public boolean onStartPulse(Animation animatePulseIn) {
        if (mEditor.getMode() == BaseEditor.MODE_ON) {
            mEditor.changeEditMode(BaseEditor.MODE_OFF);
        }
        final View currentNavButtons = getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final View hiddenNavButtons = getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);

        // no need to animate the GONE view, but keep alpha inline since onStartPulse
        // is a oneshot call
        hiddenNavButtons.setAlpha(PULSE_ALPHA_FADE);
        currentNavButtons.animate()
                .alpha(PULSE_ALPHA_FADE)
                .setDuration(PULSE_FADE_OUT_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        // shouldn't be null, mPulse just called into us
                        if (mPulse != null) {
                            mPulse.turnOnPulse();
                        }
                    }
                })
                .start();
        return true;
    }

    @Override
    public void onStopPulse(Animation animatePulseOut) {
        final View currentNavButtons = getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final View hiddenNavButtons = getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);

        hiddenNavButtons.setAlpha(1.0f);
        currentNavButtons.animate()
                .alpha(1.0f)
                .setDuration(PULSE_FADE_IN_DURATION)
                .start();
    }
}
