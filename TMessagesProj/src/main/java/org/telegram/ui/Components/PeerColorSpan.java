package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// LoogriGram: moved out of PeerColorActivity with PeerColorDrawable.
public class PeerColorSpan extends ReplacementSpan {
    private int size = dp(21);
    public PeerColorDrawable drawable;

    public PeerColorSpan(boolean profile, int currentAccount, int colorId) {
        drawable = profile ? PeerColorDrawable.fromProfile(currentAccount, colorId) : PeerColorDrawable.from(currentAccount, colorId);
    }

    public PeerColorSpan setSize(int sz) {
        if (drawable != null) {
            drawable.setRadius(sz / 2f);
            size = sz;
        }
        return this;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return dp(3) + size + dp(3);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        if (drawable != null) {
            int cy = (top + bottom) / 2;
            drawable.setBounds((int) (x + dp(3)), cy - size, (int) (x + dp(5) + size), cy + size);
            drawable.draw(canvas);
        }
    }
}
