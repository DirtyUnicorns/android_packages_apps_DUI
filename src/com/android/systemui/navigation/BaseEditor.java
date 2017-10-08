/**
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
 * Core implementation of a inline navigation editor. Respond to many of the
 * same events BaseNavigationBar does and provide subclasses with information
 * regarding state changes
 *
 */

package com.android.systemui.navigation;

import com.android.systemui.navigation.BaseNavigationBar;
import com.android.systemui.navigation.Editor;
import com.android.systemui.R;

import com.android.internal.utils.du.Config.ActionConfig;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

public abstract class BaseEditor implements Editor {

    public static final String INTENT_ACTION_EDIT_CLASS = "com.android.settings";
    public static final String INTENT_ACTION_EDIT_COMPONENT = "com.dirtyunicorns.tweaks.ActionPickerDialogActivity";
    public static final String INTENT_ACTION_ICON_PICKER_COMPONENT = "com.dirtyunicorns.tweaks.IconPickerActivity";
    public static final String INTENT_ACTION_GALLERY_PICKER_COMPONENT = "com.dirtyunicorns.tweaks.IconPickerGallery";
    public static final String INTENT_ICON_PICKER = "intent_icon_picker";
    public static final String INTENT_GALLERY_PICKER = "intent_gallery_picker";
    public static final String INTENT_ACTION_PICKER = "intent_action_action_picker";
    public static final String INTENT_NAVBAR_EDIT_RESET_LAYOUT = "intent_navbar_edit_reset_layout";
    public static int MODE_ON = 1;
    public static int MODE_OFF = 2;

    protected Context mContext;
    protected FrameLayout mFrameLayout;
    protected WindowManager mWindowManager;

    // we want any incoming hints (menu/ime/disabled) to automatcally
    // turn off editor. Except when we call these hint handling methods
    // as part of the editing process
    protected boolean mLockEditMode;
    private int mMode = MODE_OFF;
    private boolean mIsLandscape;
    private boolean mScreenOn;
    private boolean mKeyguardShowing;
    private boolean mScreenPinningOn;
    private BaseNavigationBar mHost;

    public BaseEditor(BaseNavigationBar host) {
        mHost = host;
        mContext = host.getContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    private void toastState(int state) {
        if (state == MODE_ON) {
            editHint();
        } else if (state == MODE_OFF) {
            mWindowManager.removeView(mFrameLayout);
        } else {
            Toast.makeText(mContext, R.string.smartbar_editor_toast_unavailable,
            Toast.LENGTH_SHORT).show();
        }
    }

    private void editHint() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;

        mFrameLayout = new FrameLayout(mContext);

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(mFrameLayout, params);

        LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.edit_hint, mFrameLayout);

        final ImageView imageView = (ImageView) mFrameLayout.findViewById(R.id.imageView);
        imageView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleNavigationEditor();
            }
        });
    }

    protected abstract void onEditModeChanged(int mode);

    protected abstract void onPrepareToReorient();

    protected abstract void onReorient();

    protected void onActionPicked(String action, ActionConfig actionConfig) {}

    protected void onIconPicked(String type, String packageName, String iconName) {}

    protected void onImagePicked(String uri) {}

    protected void updateResources(Resources res) {}

    protected void onResetLayout() {}

    @Override
    public void dispatchNavigationEditorResults(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (TextUtils.equals(INTENT_NAVBAR_EDIT_RESET_LAYOUT, action)) {
                onResetLayout();
            } else if (TextUtils.equals(INTENT_ACTION_PICKER, action)) {
                int result = intent.getIntExtra("result", Activity.RESULT_CANCELED);
                if (result == Activity.RESULT_OK) {
                    String actionString = intent.getStringExtra("action_string");
                    ActionConfig config = intent.getParcelableExtra("action_config");
                    onActionPicked(actionString, config);
                }
            } else if (TextUtils.equals(INTENT_ICON_PICKER, action)) {
                int result = intent.getIntExtra("result", Activity.RESULT_CANCELED);
                if (result == Activity.RESULT_OK) {
                    String iconType = intent.getStringExtra("icon_data_type");
                    String iconPackage = intent.getStringExtra("icon_data_package");
                    String iconName = intent.getStringExtra("icon_data_name");
                    onIconPicked(iconType, iconPackage, iconName);
                }
            } else if (TextUtils.equals(INTENT_GALLERY_PICKER, action)) {
                Log.d("BASEEDITOR", "ICON SELECTED");
                int result = intent.getIntExtra("result", Activity.RESULT_CANCELED);
                if (result == Activity.RESULT_OK) {
                    String uri = intent.getStringExtra("uri");
                    onImagePicked(uri);
                }
            }
        }
    }

    @Override
    public void toggleNavigationEditor() {
        final int mode = mMode;
        if (mode == MODE_ON) {
            changeEditMode(MODE_OFF);
            mHost.setSlippery(true);
        } else {
            if (isEditorAvailable()) {
                changeEditMode(MODE_ON);
                mHost.setSlippery(false);
            } else {
                toastState(69);
            }
        }
    }

    @Override
    public final void changeEditMode(int mode) {
        if (mLockEditMode || mMode == mode)
            return;
        mMode = mode;
        toastState(mode);
        onEditModeChanged(mode);
    }

    public boolean isEditorAvailable() {
        return !mKeyguardShowing
                && mScreenOn
                && !mScreenPinningOn
                && !mHost.mVertical
                && !mHost.mPulse.shouldDrawPulse()
                && !mHost.areAnyHintsActive();
    }

    @Override
    public final void prepareToReorient() {
        onPrepareToReorient();
    }

    @Override
    public final void reorient(boolean isLandscape) {
        mIsLandscape = isLandscape;
        if (mHost.mVertical) {
            changeEditMode(MODE_OFF);
        }
        onReorient();
    }

    @Override
    public void setKeyguardShowing(boolean showing) {
        mKeyguardShowing = showing;
        if (mKeyguardShowing) {
            changeEditMode(MODE_OFF);
        }
    }

    @Override
    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        if (!mScreenOn) {
            changeEditMode(MODE_OFF);
        }
    }

    @Override
    public void screenPinningStateChanged(boolean enabled) {
        mScreenPinningOn = enabled;
        if (mScreenPinningOn) {
            changeEditMode(MODE_OFF);
        }
    }

    protected boolean isLandscape() {
        return mIsLandscape;
    }

    public int getMode() {
        return mMode;
    }
}