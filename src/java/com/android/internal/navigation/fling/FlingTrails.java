/*
 * Copyright (C) 2015 The TeamEos Project
 * Author: Randall Rushing aka Bigrushdog
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

package com.android.internal.navigation.fling;

import com.orange.dgil.trail.android.animation.IAnimListener;
import com.orange.dgil.trail.android.impl.TrailDrawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemProperties;
import android.view.MotionEvent;
import android.view.View;

public class FlingTrails implements FlingModule, View.OnTouchListener, IAnimListener {
    private static final int TRAIL_COLOR = 0xff8213e0;
    private static final int ANIM_DELAY = 100;
    private static final int ANIM_DURATION = 400;
    private static final float SIZE_RELATIVE_TO_BAR = 0.3f;
    private static final int NUM_TRAIL_SIZE = 25; // how much trail history
    private static final int TRAIL_SIZE = SystemProperties.getInt("ro.fling.trails.num_trails", NUM_TRAIL_SIZE);


    private boolean mEnabled = "1".equals(SystemProperties.get("ro.fling.trails.enabled", "0"));
    private Context mContext;
    private Callbacks mCallbacks;
    private TrailDrawer mTrailDrawer;

    public FlingTrails(View v, Callbacks callback) {
        mContext = v.getContext();
        mCallbacks = callback;

        mTrailDrawer = new TrailDrawer(v);
        mTrailDrawer.setMultistrokeEnabled(false);
        mTrailDrawer.getTrailOptions().setColor(TRAIL_COLOR);
        mTrailDrawer.getTrailOptions().selectMarkerPen();
        mTrailDrawer.getTrailOptions().setShadowEnabled(true);
        mTrailDrawer.getAnimationParameters().setTimeProperties(ANIM_DELAY, ANIM_DURATION);
        mTrailDrawer.getAnimationParameters().setColorProperties(TRAIL_COLOR, TRAIL_COLOR);
        mTrailDrawer.getAnimationParameters().setColorForAlphaAnimation(TRAIL_COLOR);
        mTrailDrawer.setAnimationListener(this);
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) {
            return;
        }
        //int dimen = w > h ? h : w;
        //dimen = Math.round(dimen * SIZE_RELATIVE_TO_BAR);
        //mTrailDrawer.getTrailOptions().setTrailWidthMicrometers(dimen);
        mTrailDrawer.clear();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public void setCallbacks(Callbacks callbacks) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mEnabled) {
            mTrailDrawer.draw(canvas);
        }
    }

    @Override
    public void animationFinished() {
        mTrailDrawer.clear();
        mCallbacks.onInvalidate();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTrailDrawer.touchDown((int) event.getX(), (int) event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                int oldestPoint = event.getHistorySize() - TRAIL_SIZE;
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
}
