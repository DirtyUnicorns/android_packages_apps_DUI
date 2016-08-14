/**
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
 * Fling implementation of the trail-drawing library found at
 * https://github.com/Orange-OpenSource/trail-drawing
 *
 */

package com.android.systemui.navigation.fling;

import com.android.internal.utils.du.DUActionUtils;
import com.orange.dgil.trail.android.AndroidMetrics;
import com.orange.dgil.trail.android.animation.IAnimListener;
import com.orange.dgil.trail.android.impl.TrailDrawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemProperties;
import android.view.MotionEvent;
import android.view.View;

public class FlingTrails implements View.OnTouchListener, IAnimListener {
    public static final String TAG = FlingTrails.class.getSimpleName();
    public static final int TRAIL_WIDTH_DEFAULT = 15;

    private static final int ANIM_DELAY = 100;
    private static final int ANIM_DURATION = 400;
    // trail width constraints, in density pixels
    private static final int TRAIL_WIDTH_MIN = 1;
    private static final int TRAIL_WIDTH_MAX = 25;
    private TrailDrawer mTrailDrawer;
    private boolean mEnabled;
    private View mHost;
    private int mTrailColor = Color.WHITE;

    public FlingTrails(View v) {
        mHost = v;
        mTrailDrawer = new TrailDrawer(v);
        mTrailDrawer.setMultistrokeEnabled(false);
        mTrailDrawer.getTrailOptions().setColor(mTrailColor);
        mTrailDrawer.getTrailOptions().selectMarkerPen();
        mTrailDrawer.getTrailOptions().setShadowEnabled(true);
        mTrailDrawer.getAnimationParameters().setTimeProperties(ANIM_DELAY, ANIM_DURATION);
        mTrailDrawer.getAnimationParameters().setColorProperties(mTrailColor, mTrailColor);
        mTrailDrawer.getAnimationParameters().setColorForAlphaAnimation(mTrailColor);
        mTrailDrawer.setAnimationListener(this);
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) {
            return;
        }
        mTrailDrawer.clear();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setTrailsEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
        }
    }

    public void setTrailColor(int color) {
        if (mTrailColor != color) {
            mTrailColor = color;
            mTrailDrawer.getTrailOptions().setColor(color);
            mTrailDrawer.getAnimationParameters().setColorProperties(color, color);
            mTrailDrawer.getAnimationParameters().setColorForAlphaAnimation(color);
        }
    }

    public void setTrailWidth(int dp) {
        mTrailDrawer.clear();
        int px = DUActionUtils.ConvertDpToPixelAsInt(validateTrailWidthRange(dp), mHost.getContext());
        int microns = AndroidMetrics.get(mHost.getContext()).pixelsToMicrometers(px);
        mTrailDrawer.getTrailOptions().setTrailWidthMicrometers(microns);
    }

    public void onDraw(Canvas canvas) {
        if (mEnabled) {
            mTrailDrawer.draw(canvas);
        }
    }

    @Override
    public void animationFinished() {
        mTrailDrawer.clear();
        mHost.invalidate();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTrailDrawer.touchDown((int) event.getX(), (int) event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                int oldestPoint = event.getHistorySize() - 25;
                if (oldestPoint < 0) {
                    oldestPoint = 0;
                }
                for (int i = oldestPoint; i < event.getHistorySize(); i++) {
                    onMove(event.getHistoricalX(i), event.getHistoricalY(i));
                }
                onMove(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
                mTrailDrawer.touchUp();
                mTrailDrawer.animate();
                break;
            default:
                mTrailDrawer.touchCancel();
                mTrailDrawer.animate();
        }
        return false;
    }

    private void onMove(float x, float y) {
        mTrailDrawer.touchMove((int) x, (int) y);
    }

    private static int validateTrailWidthRange(int dp) {
        return (int) Math.max(TRAIL_WIDTH_MIN, Math.min(TRAIL_WIDTH_MAX, dp));
    }
}
