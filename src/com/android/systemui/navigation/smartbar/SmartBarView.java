/**
 * Copyright (C) 2016-2017 The DirtyUnicorns Project
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

package com.android.systemui.navigation.smartbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import com.android.systemui.navigation.BaseEditor;
import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.Editor;
import com.android.systemui.navigation.OpaLayout;
import com.android.systemui.navigation.Res;
import com.android.systemui.navigation.NavbarOverlayResources;
import com.android.systemui.navigation.smartbar.SmartBackButtonDrawable;
import com.android.systemui.navigation.smartbar.SmartBarEditor;
import com.android.systemui.navigation.smartbar.SmartBarHelper;
import com.android.systemui.navigation.smartbar.SmartBarTransitions;
import com.android.systemui.navigation.smartbar.SmartBarView;
import com.android.systemui.navigation.smartbar.SmartButtonView;
import com.android.systemui.navigation.pulse.PulseController;
import com.android.systemui.navigation.utils.SmartObserver.SmartObservable;
import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.statusbar.policy.KeyButtonDrawable;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SmartBarView extends BaseNavigationBar {
    final static boolean DEBUG = false;
    final static String TAG = SmartBarView.class.getSimpleName();
    final static int PULSE_FADE_OUT_DURATION = 250;
    final static int PULSE_FADE_IN_DURATION = 200;

    static final int IME_HINT_MODE_HIDDEN = 0;
    static final int IME_HINT_MODE_ARROWS = 1;
    static final int IME_HINT_MODE_PICKER = 2;
    static final int IME_AND_MEDIA_HINT_MODE_ARROWS = 3;

    private static Set<Uri> sUris = new HashSet<Uri>();
    static {
        sUris.add(Settings.Secure.getUriFor("smartbar_context_menu_mode"));
        sUris.add(Settings.Secure.getUriFor("smartbar_ime_hint_mode"));
        sUris.add(Settings.Secure.getUriFor("smartbar_button_animation_style"));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.NAVBAR_BUTTONS_ALPHA));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_BUTTONS_OPACITY));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.SMARTBAR_LONGPRESS_DELAY));
        sUris.add(Settings.Secure.getUriFor(Settings.Secure.SMARTBAR_CUSTOM_ICON_SIZE));
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
            } else if (uri.equals(Settings.Secure.getUriFor("smartbar_button_animation_style"))) {
                updateAnimationStyle();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.NAVBAR_BUTTONS_ALPHA))) {
                updateButtonAlpha();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_BUTTONS_OPACITY))) {
                updatePulseNavButtonsOpacity();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.SMARTBAR_LONGPRESS_DELAY))) {
                updateButtonLongpressDelay();
            } else if (uri.equals(Settings.Secure.getUriFor(Settings.Secure.SMARTBAR_CUSTOM_ICON_SIZE))) {
                updateCustomIconSize();
                updateCurrentIcons();
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
    private boolean mMusicStreamMuted;
    private int mImeHintMode;
    private int mButtonAnimationStyle;
    private float mCustomAlpha;
    private float mCustomIconScale;
    public float mPulseNavButtonsOpacity;

    private boolean mIsMediaPlaying;

    private AudioManager mAudioManager;

    @Override
    public void onReceive(Intent intent) {
        if (AudioManager.STREAM_MUTE_CHANGED_ACTION.equals(intent.getAction())
                || (AudioManager.VOLUME_CHANGED_ACTION.equals(intent.getAction()))) {
            int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
            if (streamType == AudioManager.STREAM_MUSIC) {
                boolean muted = isMusicMuted(streamType);
                if (mMusicStreamMuted != muted) {
                    mMusicStreamMuted = muted;
                    Handler mHandler = new Handler();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setNavigationIconHints(mNavigationIconHints, true);
                        }
                    });
                }
            }
        }
    }

    private boolean isMusicMuted(int streamType) {
        return streamType == AudioManager.STREAM_MUSIC &&
                (mAudioManager.isStreamMute(streamType) ||
                mAudioManager.getStreamVolume(streamType) == 0);
    }

    public SmartBarView(Context context) {
        super(context);
        mBarTransitions = new SmartBarTransitions(this);
        mEditor = new SmartBarEditor(this);
        mSmartObserver.addListener(mObservable);
        createBaseViews();

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mMusicStreamMuted = isMusicMuted(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void setMediaPlaying(boolean playing) {
        mIsMediaPlaying = playing;
        setNavigationIconHints(mNavigationIconHints, true);
        PulseController mPulse = getPulseController();
        if (mPulse != null) {
            mPulse.setMediaPlaying(playing);
        }
    }

    ArrayList<String> getCurrentSequence() {
        return mCurrentSequence;
    }

    float getButtonAlpha() {
        return mCustomAlpha;
    }

    @Override
    public Editor getEditor() {
        return mEditor;
    }

    public void setEditMode(boolean on) {
        Drawable d = mContext.getResources().getDrawable(R.drawable.smartbar_editmode_color);
        setBackgroundColor(on ? d : null);
    }

    @Override
    public void setResourceMap(NavbarOverlayResources resourceMap) {
        super.setResourceMap(resourceMap);
        updateCustomIconSize();
        updateCurrentIcons();
        recreateLayouts();
        updateImeHintModeSettings();
        updateContextLayoutSettings();
        updateButtonLongpressDelay();
    }

    @Override
    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    @Override
    public LightBarTransitionsController getLightTransitionsController() {
        return mBarTransitions.getLightTransitionsController();
    }

    @Override
    protected void onInflateFromUser() {
        if (mEditor != null) {
            mEditor.notifyScreenOn(mScreenOn);
        }
    }

    @Override
    public void onRecreateStatusbar() {
        if (mEditor != null) {
            mEditor.updateResources(null);
        }
        updateCurrentIcons();
    }

    @Override
    public void updateNavbarThemedResources(Resources res){
        super.updateNavbarThemedResources(res);
        updateCurrentIcons();
        updateButtonAlpha();
    }

    public void updateCurrentIcons() {
        for (SmartButtonView button : DUActionUtils.getAllChildren(this, SmartButtonView.class)) {
            setButtonDrawable(button);
        }
    }

    public void setButtonDrawable(SmartButtonView button) {
        ButtonConfig config = button.getButtonConfig();
        if (config != null) {
            Context ctx = getContext();
            KeyButtonDrawable d = null;
            SmartBackButtonDrawable bd = null;
            boolean isBackButton = TextUtils.equals(config.getTag(), Res.Softkey.BUTTON_BACK);
            final boolean backAlt = (mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;

            if (!config.hasCustomIcon()
                    && config.isSystemAction()) {
                Drawable light = mResourceMap.getActionDrawable(config.getActionConfig(ActionConfig.PRIMARY).getAction());
                Drawable dark = mResourceMap.getDarkActionDrawable(config.getActionConfig(ActionConfig.PRIMARY).getAction());
                if (isBackButton) {
                    bd = SmartBackButtonDrawable.create(light, dark);
                    bd.setImeVisible(backAlt);
                    button.setImageDrawable(bd);
                } else {
                    d = KeyButtonDrawable.create(light, dark);
                    button.setImageDrawable(d);
                }
            } else {
                if (isBackButton) {
                    bd = SmartBackButtonDrawable.create(
                            SmartBarHelper.resizeCustomButtonIcon(config.getCurrentIcon(ctx), ctx, mCustomIconScale),
                            null);
                    bd.setImeVisible(backAlt);
                    button.setImageDrawable(bd);
                } else {
                    d = KeyButtonDrawable.create(
                            SmartBarHelper.resizeCustomButtonIcon(config.getCurrentIcon(ctx), ctx, mCustomIconScale),
                            null);
                    button.setImageDrawable(d);
                }
            }
        }
    }

    @Override
    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public SmartButtonView getSmartBackButton() {
        return (SmartButtonView) mCurrentView.findViewWithTag(Res.Softkey.BUTTON_BACK);
    }

    public SmartButtonView getSmartHomeButton() {
        return (SmartButtonView) mCurrentView.findViewWithTag(Res.Softkey.BUTTON_HOME);
    }

    public SmartButtonView getSmartMenuButton() {
        return (SmartButtonView) mCurrentContext.findViewWithTag(Res.Softkey.MENU_BUTTON);
    }

    SmartButtonView getSmartImeSwitchButton() {
        return (SmartButtonView) mCurrentContext.findViewWithTag(Res.Softkey.IME_SWITCHER);
    }

    SmartButtonView findCurrentButton(String tag) {
        return (SmartButtonView) mCurrentView.findViewWithTag(tag);
    }

    SmartBackButtonDrawable getSmartBackButtonIcon() {
        return (SmartBackButtonDrawable) getSmartBackButton().getDrawable();
    }

    private ViewGroup getHiddenContext() {
        return (ViewGroup) (mCurrentContext == mContextRight ? mContextLeft : mContextRight);
    }

    private void setImeArrowsVisibility(View currentOrHidden, int visibility) {
        ViewGroup contextLeft = (ViewGroup)currentOrHidden.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        contextLeft.findViewWithTag(Res.Softkey.IME_ARROW_LEFT).setVisibility(visibility);
        ViewGroup contextRight = (ViewGroup)currentOrHidden.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        contextRight.findViewWithTag(Res.Softkey.IME_ARROW_RIGHT).setVisibility(visibility);
    }

    private void setMediaArrowsVisibility(boolean backAlt) {
        setMediaArrowsVisibility(mCurrentView, (!backAlt && (mIsMediaPlaying&& mAudioManager.isMusicActive()))
                ? View.VISIBLE : View.INVISIBLE);
    }

    private void setMediaArrowsVisibility(View currentOrHidden, int visibility) {
        ViewGroup contextLeft = (ViewGroup)currentOrHidden.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        contextLeft.findViewWithTag(Res.Softkey.MEDIA_ARROW_LEFT).setVisibility(visibility);
        ViewGroup contextRight = (ViewGroup)currentOrHidden.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        contextRight.findViewWithTag(Res.Softkey.MEDIA_ARROW_RIGHT).setVisibility(visibility);
    }

    @Override
    protected boolean areAnyHintsActive() {
        return super.areAnyHintsActive() || mShowMenu;
    }

    @Override
    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints)
            return;
        if (mEditor != null) {
            mEditor.changeEditMode(BaseEditor.MODE_OFF);
        }
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;

        mNavigationIconHints = hints;
        getSmartBackButtonIcon().setImeVisible(backAlt);

        final boolean showImeButton = /*(*/(hints /*& StatusBarManager.NAVIGATION_HINT_IME_SHOWN)*/ != 0);
        switch(mImeHintMode) {
            case IME_HINT_MODE_ARROWS: // arrows
                getSmartImeSwitchButton().setVisibility(View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, backAlt ? View.VISIBLE : View.INVISIBLE);
                setMediaArrowsVisibility(mCurrentView, View.INVISIBLE);
                break;
            case IME_AND_MEDIA_HINT_MODE_ARROWS:
                getSmartImeSwitchButton().setVisibility(View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, backAlt ? View.VISIBLE : View.INVISIBLE);
                setMediaArrowsVisibility(backAlt);
                break;
            case IME_HINT_MODE_PICKER:
                getHiddenContext().findViewWithTag(Res.Softkey.IME_SWITCHER).setVisibility(INVISIBLE);
                getSmartImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, View.INVISIBLE);
                setMediaArrowsVisibility(mCurrentView, View.INVISIBLE);
                break;
            default: // hidden
                getSmartImeSwitchButton().setVisibility(View.INVISIBLE);
                setImeArrowsVisibility(mCurrentView, View.INVISIBLE);
                setMediaArrowsVisibility(mCurrentView, View.INVISIBLE);
        }

        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);
        setDisabledFlags(mDisabledFlags, true);
    }

    @Override
    public void setDisabledFlags(int disabledFlags, boolean force) {
        super.setDisabledFlags(disabledFlags, force);
        if (mEditor != null) {
            mEditor.changeEditMode(BaseEditor.MODE_OFF);
        }

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);

        OpaLayout opaBack = (OpaLayout)getSmartBackButton().getParent();
        opaBack.setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        OpaLayout opaHome = (OpaLayout)getSmartHomeButton().getParent();
        opaHome.setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);

        // if any stock buttons are disabled, it's likely proper
        // to disable custom buttons as well
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            OpaLayout opa = (OpaLayout) v.getParent();
            if (v != null && v != getSmartBackButton() && v != getSmartHomeButton()) {
                if (disableHome || disableBack || disableRecent) {
                    opa.setVisibility(View.INVISIBLE);
                } else {
                    opa.setVisibility(View.VISIBLE);
                }
            }
        }
        if (mImeHintMode == 3) {
            if (disableHome) {
                setMediaArrowsVisibility(mCurrentView, View.INVISIBLE);
            } else {
                final boolean backAlt = (mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
                setMediaArrowsVisibility(backAlt);
            }
        }
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        super.notifyScreenOn(screenOn);
        if (mEditor != null) {
            mEditor.notifyScreenOn(screenOn);
        }
        ViewGroup hidden = (ViewGroup) getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null) {
                v.onScreenStateChanged(screenOn);
            }
            v = (SmartButtonView) hidden.findViewWithTag(buttonTag);
            if (v != null) {
                v.onScreenStateChanged(screenOn);
            }
        }
        // onStopPulse may not have had time to animate alpha to proper value before screen went
        // off. Reset alpha when we come back on. we should never have pulse running when this is called
        final View currentNavButtons = getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final View hiddenNavButtons = getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final float fadeAlpha = mCustomAlpha;
        if (screenOn && (currentNavButtons.getAlpha() != fadeAlpha || hiddenNavButtons.getAlpha() != fadeAlpha)) {
            hiddenNavButtons.setAlpha(fadeAlpha);
            currentNavButtons.setAlpha(fadeAlpha);
        }
    }

    @Override
    protected void onKeyguardShowing(boolean showing) {
        if (mEditor != null) {
            mEditor.setKeyguardShowing(showing);
        }
        // TODO: temp hax to address package manager not having activity icons ready yet
        // this is new to N, likely part of new optimized boot time. In theory, activity
        // icons should be ready by the time lockscreen goes away. We will be stuck with this
        // unless we can find a way for package manager to have activity icons ready sooner, but
        // do so without slowing faster boot time.
        if (!showing) {
            ViewGroup hidden = (ViewGroup) getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
            for (String buttonTag : mCurrentSequence) {
                SmartButtonView v = findCurrentButton(buttonTag);
                if (v != null) {
                    ButtonConfig config = v.getButtonConfig();
                    if (config != null && v.getDrawable() == null) {
                        v.setImageDrawable(config.getCurrentIcon(getContext()));
                    }
                }
                v = (SmartButtonView) hidden.findViewWithTag(buttonTag);
                if (v != null) {
                    ButtonConfig config = v.getButtonConfig();
                    if (config != null && v.getDrawable() == null) {
                        v.setImageDrawable(config.getCurrentIcon(getContext()));
                    }
                }
            }
        }
    }

    @Override
    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    @Override
    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show)
            return;
        if (mEditor != null) {
            mEditor.changeEditMode(BaseEditor.MODE_OFF);
        }
        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                /*(*/(mNavigationIconHints == 0 /*& StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0*/);
        getSmartMenuButton().setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
    }

    void recreateLayouts() {
        mCurrentSequence.clear();
        ArrayList<ButtonConfig> buttonConfigs;
        buttonConfigs = Config.getConfig(getContext(),
                ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        recreateButtonLayout(buttonConfigs, false, true);
        recreateButtonLayout(buttonConfigs, true, false);
        mContextLeft = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        mContextRight = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
        updateCurrentIcons();
        setDisabledFlags(mDisabledFlags, true);
        setMenuVisibility(mShowMenu, true);
        setNavigationIconHints(mNavigationIconHints, true);
        updateAnimationStyle();
        updateButtonAlpha();
        setOpaLandscape(mVertical);
    }

    @Override
    protected void onDispose() {
        removeAllViews();
    }

    @Override
    public void reorient() {
        if (mEditor != null) {
            mEditor.prepareToReorient();
        }
        super.reorient();
        mBarTransitions.init();
        if (mEditor != null) {
            mEditor.reorient(mCurrentView == mRot90);
        }
        mContextLeft = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_LEFT);
        mContextRight = mCurrentView.findViewWithTag(Res.Softkey.CONTEXT_VIEW_RIGHT);
        mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
        setDisabledFlags(mDisabledFlags, true);
        setMenuVisibility(mShowMenu, true);
        setNavigationIconHints(mNavigationIconHints, true);
        setButtonAlpha();
        updatePulseNavButtonsOpacity();
        setOpaLandscape(mVertical);
    }

    private void updateContextLayoutSettings() {
        boolean onLeft = Settings.Secure.getIntForUser(getContext().getContentResolver(),
                "smartbar_context_menu_mode", 0, UserHandle.USER_CURRENT) == 1;
        if (mHasLeftContext != onLeft) {
            getSmartMenuButton().setVisibility(INVISIBLE);
            getSmartImeSwitchButton().setVisibility(INVISIBLE);
            getHiddenContext().findViewWithTag(Res.Softkey.MENU_BUTTON).setVisibility(INVISIBLE);
            getHiddenContext().findViewWithTag(Res.Softkey.IME_SWITCHER).setVisibility(INVISIBLE);
            mHasLeftContext = onLeft;
            mCurrentContext = mHasLeftContext ? mContextLeft : mContextRight;
            setDisabledFlags(mDisabledFlags, true);
            setMenuVisibility(mShowMenu, true);
            setNavigationIconHints(mNavigationIconHints, true);
        }
    }

    private void updateImeHintModeSettings() {
        mImeHintMode = Settings.Secure.getIntForUser(getContext().getContentResolver(),
                "smartbar_ime_hint_mode", IME_HINT_MODE_HIDDEN, UserHandle.USER_CURRENT);
    }

    private void updateAnimationStyle() {
        mButtonAnimationStyle = Settings.Secure.getIntForUser(getContext().getContentResolver(),
                "smartbar_button_animation_style", SmartButtonView.ANIM_STYLE_RIPPLE, UserHandle.USER_CURRENT);
        ViewGroup hidden = (ViewGroup) getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            if (v != null) {
                v.setAnimationStyle(mButtonAnimationStyle);
            }
            v = (SmartButtonView) hidden.findViewWithTag(buttonTag);
            if (v != null) {
                v.setAnimationStyle(mButtonAnimationStyle);
            }
        }
    }

    private void setOpaLandscape(boolean landscape) {
        for (String buttonTag : mCurrentSequence) {
            SmartButtonView v = findCurrentButton(buttonTag);
            OpaLayout opa = (OpaLayout) v.getParent();
            opa.setLandscape(landscape);
        }
    }

    private void refreshImeHintMode() {
        getSmartMenuButton().setVisibility(INVISIBLE);
        getSmartImeSwitchButton().setVisibility(INVISIBLE);
        getHiddenContext().findViewWithTag(Res.Softkey.MENU_BUTTON).setVisibility(INVISIBLE);
        getHiddenContext().findViewWithTag(Res.Softkey.IME_SWITCHER).setVisibility(INVISIBLE);
        setNavigationIconHints(mNavigationIconHints, true);
    }

    void recreateButtonLayout(ArrayList<ButtonConfig> buttonConfigs, boolean landscape,
            boolean updateCurrentButtons) {
        int extraKeyWidth = getContext().getResources().getDimensionPixelSize(R.dimen.navigation_extra_key_width);
        int extraKeyHeight = getContext().getResources().getDimensionPixelSize(R.dimen.navigation_extra_key_height);

        LinearLayout navButtonLayout = (LinearLayout) (landscape ? mRot90
                .findViewWithTag(Res.Common.NAV_BUTTONS) : mRot0
                .findViewWithTag(Res.Common.NAV_BUTTONS));

        navButtonLayout.removeAllViews();

        if (buttonConfigs == null) {
            buttonConfigs = Config.getConfig(getContext(),
                    ActionConstants.getDefaults(ActionConstants.SMARTBAR));
        }

        // left context frame layout
        FrameLayout leftContext = generateContextKeyLayout(landscape,
                Res.Softkey.CONTEXT_VIEW_LEFT,
                extraKeyWidth, extraKeyHeight);
        SmartBarHelper.addViewToRoot(navButtonLayout, leftContext, landscape);

        // tablets get a spacer here
        if (BaseNavigationBar.sIsTablet) {
            SmartBarHelper.addViewToRoot(navButtonLayout, SmartBarHelper.makeSeparator(getContext()),
                    landscape);
        }

        // softkey buttons
        ButtonConfig buttonConfig;
        int dimen = SmartBarHelper.getButtonSize(getContext(), buttonConfigs.size(), landscape);

        for (int j = 0; j < buttonConfigs.size(); j++) {
            buttonConfig = buttonConfigs.get(j);
            OpaLayout v = SmartBarHelper.generatePrimaryKey(getContext(), this, landscape, buttonConfig);
            SmartBarHelper.updateButtonSize(v, dimen, landscape);
            SmartButtonView sb = v.getButton();
            SmartBarHelper.updateButtonSize(sb, dimen, landscape);
            SmartBarHelper.addViewToRoot(navButtonLayout, v, landscape);

            // only add once for master sequence holder
            if (updateCurrentButtons) {
                mCurrentSequence.add((String) v.getButton().getTag());
            }

            // phones get a spacer between each button
            // tablets get a spacer before first and after last
            if (j != buttonConfigs.size() - 1 && !BaseNavigationBar.sIsTablet) {
                // adding spacers between buttons on phones
                SmartBarHelper.addViewToRoot(navButtonLayout,
                        SmartBarHelper.makeSeparator(getContext()), landscape);
            }
            if (j == buttonConfigs.size() - 1 && BaseNavigationBar.sIsTablet) {
                // adding spacers after last button on tablets
                SmartBarHelper.addViewToRoot(navButtonLayout,
                        SmartBarHelper.makeSeparator(getContext()), landscape);
            }
        }

        // right context frame layout
        FrameLayout rightContext = generateContextKeyLayout(landscape,
                Res.Softkey.CONTEXT_VIEW_RIGHT,
                extraKeyWidth, extraKeyHeight);
        SmartBarHelper.addViewToRoot(navButtonLayout, rightContext, landscape);
    }

    private FrameLayout generateContextKeyLayout(boolean landscape, String leftOrRight,
            int extraKeyWidth, int extraKeyHeight) {
        FrameLayout contextLayout = new FrameLayout(getContext());
        contextLayout.setLayoutParams(new LinearLayout.LayoutParams(
                landscape && !BaseNavigationBar.sIsTablet ? LayoutParams.MATCH_PARENT
                        : extraKeyWidth, landscape && !BaseNavigationBar.sIsTablet ? extraKeyHeight
                        : LayoutParams.MATCH_PARENT));
        contextLayout.setTag(leftOrRight);

        SmartButtonView menuKeyView = generateContextKey(landscape, Res.Softkey.MENU_BUTTON);
        contextLayout.addView(menuKeyView);

        SmartButtonView imeChanger = generateContextKey(landscape, Res.Softkey.IME_SWITCHER);
        contextLayout.addView(imeChanger);

        if (TextUtils.equals(Res.Softkey.CONTEXT_VIEW_LEFT, leftOrRight)) {
            SmartButtonView imeArrowLeft = generateContextKey(landscape, Res.Softkey.IME_ARROW_LEFT);
            contextLayout.addView(imeArrowLeft);
            SmartButtonView mediaArrowLeft = generateContextKey(landscape, Res.Softkey.MEDIA_ARROW_LEFT);
            contextLayout.addView(mediaArrowLeft);
        } else if (TextUtils.equals(Res.Softkey.CONTEXT_VIEW_RIGHT, leftOrRight)) {
            SmartButtonView imeArrowRight = generateContextKey(landscape, Res.Softkey.IME_ARROW_RIGHT);
            contextLayout.addView(imeArrowRight);
            SmartButtonView mediaArrowRight = generateContextKey(landscape, Res.Softkey.MEDIA_ARROW_RIGHT);
            contextLayout.addView(mediaArrowRight);
        }

        return contextLayout;
    }

    private SmartButtonView generateContextKey(boolean landscape, String tag) {
        SmartButtonView v = new SmartButtonView(getContext());
        ButtonConfig buttonConfig = new ButtonConfig(getContext());
        ActionConfig actionConfig = new ActionConfig(getContext());

        int extraKeyWidth = getContext().getResources().getDimensionPixelSize(R.dimen.navigation_extra_key_width);
        int extraKeyHeight = getContext().getResources().getDimensionPixelSize(R.dimen.navigation_extra_key_height);

        v.setHost(this);
        v.setLayoutParams(new FrameLayout.LayoutParams(
                landscape && !BaseNavigationBar.sIsTablet ? LayoutParams.MATCH_PARENT : extraKeyWidth,
                landscape && !BaseNavigationBar.sIsTablet ? extraKeyHeight : LayoutParams.MATCH_PARENT));
        v.loadRipple();
        v.setScaleType(ScaleType.CENTER_INSIDE);

        if (tag.equals(Res.Softkey.MENU_BUTTON)) {
            actionConfig = new ActionConfig(getContext(), ActionHandler.SYSTEMUI_TASK_MENU);
        } else if (tag.equals(Res.Softkey.IME_SWITCHER)) {
            actionConfig = new ActionConfig(getContext(), ActionHandler.SYSTEMUI_TASK_IME_SWITCHER);
        } else if (tag.equals(Res.Softkey.IME_ARROW_LEFT)) {
            actionConfig = new ActionConfig(getContext(), ActionHandler.SYSTEMUI_TASK_IME_NAVIGATION_LEFT);
        } else if (tag.equals(Res.Softkey.IME_ARROW_RIGHT)) {
            actionConfig = new ActionConfig(getContext(), ActionHandler.SYSTEMUI_TASK_IME_NAVIGATION_RIGHT);
        } else if (tag.equals(Res.Softkey.MEDIA_ARROW_LEFT)) {
            actionConfig = new ActionConfig(getContext(), ActionHandler.SYSTEMUI_TASK_MEDIA_PREVIOUS);
        } else if (tag.equals(Res.Softkey.MEDIA_ARROW_RIGHT)) {
            actionConfig = new ActionConfig(getContext(), ActionHandler.SYSTEMUI_TASK_MEDIA_NEXT);
        }

        buttonConfig.setActionConfig(actionConfig, ActionConfig.PRIMARY);
        buttonConfig.setTag(tag);
        v.setButtonConfig(buttonConfig);
        v.setVisibility(View.INVISIBLE);
        setButtonDrawable(v);
        v.setContentDescription(buttonConfig.getActionConfig(ActionConfig.PRIMARY).getLabel());
        v.setAnimationStyle(SmartButtonView.ANIM_STYLE_RIPPLE);
        return v;
    }

    boolean isBarPulseFaded() {
        if (mPulse == null) {
            return false;
        } else {
            return mPulse.shouldDrawPulse();
        }
    }

    private void updateButtonAlpha() {
        mCustomAlpha = alphaIntToFloat(Settings.Secure.getIntForUser(getContext().getContentResolver(),
                Settings.Secure.NAVBAR_BUTTONS_ALPHA, 255, UserHandle.USER_CURRENT));
        setButtonAlpha();
    }

    private void setButtonAlpha() {
        // only set this if pulse is not running. If pulse is running
        // we will set proper alpha when it ends
        if (!isBarPulseFaded()) {
            final View currentNavButtons = getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
            final View hiddenNavButtons = getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
            final float fadeAlpha = mCustomAlpha;
            currentNavButtons.setAlpha(fadeAlpha);
            hiddenNavButtons.setAlpha(fadeAlpha);
        }
    }

    private void updatePulseNavButtonsOpacity() {
        mPulseNavButtonsOpacity = alphaIntToFloat(Settings.Secure.getIntForUser(getContext().getContentResolver(),
                Settings.Secure.PULSE_CUSTOM_BUTTONS_OPACITY, 200, UserHandle.USER_CURRENT));
        if (isBarPulseFaded()) {
            final View currentNavButtons = getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
            final View hiddenNavButtons = getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
            currentNavButtons.setAlpha(mPulseNavButtonsOpacity);
            hiddenNavButtons.setAlpha(mPulseNavButtonsOpacity);
        }
    }

    @Override
    public boolean onStartPulse(Animation animatePulseIn) {
        if (mEditor != null && mEditor.getMode() == BaseEditor.MODE_ON) {
            mEditor.changeEditMode(BaseEditor.MODE_OFF);
        }
        final View currentNavButtons = getCurrentView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final View hiddenNavButtons = getHiddenView().findViewWithTag(Res.Common.NAV_BUTTONS);
        final float fadeAlpha = mPulseNavButtonsOpacity;

        // no need to animate the GONE view, but keep alpha inline since onStartPulse
        // is a oneshot call
        hiddenNavButtons.setAlpha(fadeAlpha);
        currentNavButtons.animate()
                .alpha(fadeAlpha)
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
        final float fadeAlpha = mCustomAlpha;
        hiddenNavButtons.setAlpha(fadeAlpha);
        currentNavButtons.animate()
                .alpha(fadeAlpha)
                .setDuration(PULSE_FADE_IN_DURATION)
                .start();
    }

    private void updateButtonLongpressDelay() {
        int systemLpDelay = ViewConfiguration.getLongPressTimeout();
        int userDelay = Settings.Secure.getIntForUser(getContext().getContentResolver(),
                Settings.Secure.SMARTBAR_LONGPRESS_DELAY, 0, UserHandle.USER_CURRENT);
        switch (userDelay) {
            default:
                SmartButtonView.setButtonLongpressDelay(systemLpDelay - 100);
                break;
            case 1:
                SmartButtonView.setButtonLongpressDelay(systemLpDelay);
                break;
            case 2:
                SmartButtonView.setButtonLongpressDelay(systemLpDelay + 200);
                break;
        }
    }

    private void updateCustomIconSize() {
        int iconSize = Settings.Secure.getIntForUser(getContext().getContentResolver(),
                Settings.Secure.SMARTBAR_CUSTOM_ICON_SIZE, 60, UserHandle.USER_CURRENT);
        mCustomIconScale = 0.01f * iconSize;
    }
}
