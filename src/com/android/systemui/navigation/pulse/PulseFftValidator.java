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
 * FFT byte stream analyzer and stream state management. Once a stream starts,
 * the stream must currently produce three valid frames in 1500 millis to remove
 * the invalid stream callback.
 *
 */

package com.android.systemui.navigation.pulse;

import com.android.systemui.navigation.pulse.StreamValidator;

import android.os.Handler;
import android.os.Message;

public class PulseFftValidator implements StreamValidator {
    private static final int MSG_STREAM_VALID = 55;
    private static final int MSG_STREAM_INVALID = 56;

    // we have 1500 millis to get three consecutive valid frames
    private static final int VALIDATION_TIME_MILLIS = 1500;
    private static final int VALID_BYTES_THRESHOLD = 3;

    private int mConsecutiveFrames;
    private boolean mIsValidated;
    private boolean mIsAnalyzed;
    private boolean mIsPrepared;

    private StreamValidator.Callbacks mListener;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_STREAM_VALID:
                    mIsAnalyzed = true;
                    mIsValidated = true;
                    mIsPrepared = false;
                    if (mListener != null) {
                        mListener.onStreamAnalyzed(true);
                    }
                    break;
                case MSG_STREAM_INVALID:
                    mIsAnalyzed = true;
                    mIsValidated = false;
                    mIsPrepared = false;
                    if (mListener != null) {
                        mListener.onStreamAnalyzed(false);
                    }
                    break;
            }
        }
    };

    public PulseFftValidator() {
    }

    public void analyze(byte[] data) {
        if (mIsAnalyzed) {
            return;
        }

        if (!mIsPrepared) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_STREAM_INVALID),
                    VALIDATION_TIME_MILLIS);
            mIsPrepared = true;
        }

        if (isDataEmpty(data)) {
            mConsecutiveFrames = 0;
        } else {
            mConsecutiveFrames++;
        }

        if (mConsecutiveFrames == VALID_BYTES_THRESHOLD) {
            mHandler.removeMessages(MSG_STREAM_INVALID);
            mHandler.sendEmptyMessage(MSG_STREAM_VALID);
        }

    }

    public boolean isValidStream() {
        return mIsAnalyzed && mIsValidated;
    }

    public void reset() {
        mIsAnalyzed = false;
        mIsValidated = false;
        mIsPrepared = false;
        mConsecutiveFrames = 0;
    }

    private boolean isDataEmpty(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void addCallbacks(StreamValidator.Callbacks callbacks) {
        mListener = callbacks;
    }

    @Override
    public void removeCallbacks(StreamValidator.Callbacks callbacks) {
        mListener = null;
    }
}
