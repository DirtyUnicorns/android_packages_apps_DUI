
package com.android.internal.navigation.pulse;

import android.graphics.Canvas;
import android.graphics.Rect;

public interface Renderer {
    public void render(Canvas canvas, byte[] bytes, Rect rect);
}
