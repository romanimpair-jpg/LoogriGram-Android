package org.telegram.messenger.wallpaper;

import android.graphics.Bitmap;



public class WallpaperBitmapHolder {
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_PATTERN = 1;

    public final int mode;
    public final Bitmap bitmap;

    // LoogriGram: a pattern also carried where a collectible gift's symbol
    // went in it (giftPatternPositions). Those themes are not applied.
    public WallpaperBitmapHolder(Bitmap bitmap, int mode) {
        this.bitmap = bitmap;
        this.mode = mode;
    }
}
