/*
 * Copyright (C) 2012 The Android Open Source Project
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
 */

package dev.fourwags.sumi.inputmethod.keyboard.internal;

import dev.fourwags.sumi.inputmethod.annotations.UsedForTesting;
import dev.fourwags.sumi.inputmethod.latin.define.DebugFlags;

public final class TouchPositionCorrection {
    private static final int TOUCH_POSITION_CORRECTION_RECORD_SIZE = 3;

    private boolean mEnabled;
    private float[] mXs = new float[0];
    private float[] mYs = new float[0];
    private float[] mRadii = new float[0];

    public void load(final String[] data) {
        // A failed reload must never leave previous calibration active.
        mEnabled = false;
        mXs = new float[0];
        mYs = new float[0];
        mRadii = new float[0];
        final int dataLength = data.length;
        if (dataLength % TOUCH_POSITION_CORRECTION_RECORD_SIZE != 0) {
            if (DebugFlags.DEBUG_ENABLED) {
                throw new RuntimeException(
                        "the size of touch position correction data is invalid");
            }
            return;
        }

        final int length = dataLength / TOUCH_POSITION_CORRECTION_RECORD_SIZE;
        mXs = new float[length];
        mYs = new float[length];
        mRadii = new float[length];
        try {
            for (int i = 0; i < dataLength; ++i) {
                final int type = i % TOUCH_POSITION_CORRECTION_RECORD_SIZE;
                final int index = i / TOUCH_POSITION_CORRECTION_RECORD_SIZE;
                final float value = Float.parseFloat(data[i]);
                if (Float.isNaN(value) || Float.isInfinite(value) || (type == 2 && value < 0)) {
                    throw new NumberFormatException("Invalid touch calibration");
                }
                if (type == 0) {
                    mXs[index] = value;
                } else if (type == 1) {
                    mYs[index] = value;
                } else {
                    mRadii[index] = value;
                }
            }
            mEnabled = dataLength > 0;
        } catch (NumberFormatException e) {
            if (DebugFlags.DEBUG_ENABLED) {
                throw new RuntimeException(
                        "the number format for touch position correction data is invalid");
            }
            mEnabled = false;
            mXs = new float[0];
            mYs = new float[0];
            mRadii = new float[0];
        }
    }

    @UsedForTesting
    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isValid() {
        return mEnabled;
    }

    public int getRows() {
        return mRadii.length;
    }

    @SuppressWarnings({ "static-method", "unused" })
    public float getX(final int row) {
        return 0.0f;
        // Touch position correction data for X coordinate is obsolete.
        // return mXs[row];
    }

    public float getY(final int row) {
        return mYs[row];
    }

    public float getRadius(final int row) {
        return mRadii[row];
    }
}
