package org.telegram.ui.Components;

import android.view.View;

/**
 * LoogriGram: moved out of org.telegram.ui.Stars, which goes with the money
 * screens. This is a ripple distortion effect with nothing money about it -
 * no TL type, no controller - and LaunchActivity is its only caller.
 */
public abstract class ISuperRipple {

    public final View view;

    public ISuperRipple(View view) {
        this.view = view;
    }

    public void animate(float cx, float cy, float intensity) {

    }

}
