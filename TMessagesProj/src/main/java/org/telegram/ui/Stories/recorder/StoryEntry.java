package org.telegram.ui.Stories.recorder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaFormat;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;

// LoogriGram: this was the story being made - its media, crop, filters,
// stickers, caption, privacy and draft state, and its conversion to a
// video for upload. Stories are not posted here; what is left are the
// bitmap, cache-file and HDR helpers chat media editing and sending use.
public class StoryEntry {

    public static void drawBackgroundDrawable(Canvas canvas, Drawable drawable, int w, int h) {
        if (drawable == null) {
            return;
        }
        Rect rect = new Rect(drawable.getBounds());
        Drawable.Callback callback = drawable.getCallback();
        drawable.setCallback(null);
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) drawable;
            int bw = bd.getBitmap().getWidth();
            int bh = bd.getBitmap().getHeight();
            final float scale = Math.max(w / (float) bw, h / (float) bh);
            drawable.setBounds(0, 0, (int) (bw * scale), (int) (bh * scale));
            drawable.draw(canvas);
        } else {
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);
        }
        drawable.setBounds(rect);
        drawable.setCallback(callback);
    }

    public static interface DecodeBitmap {
        public Bitmap decode(BitmapFactory.Options options);
    }

    public static Bitmap getScaledBitmap(DecodeBitmap decode, int maxWidth, int maxHeight, boolean allowBlur, boolean scale) {
        return getScaledBitmap(decode, maxWidth, maxHeight, 0, allowBlur, scale);
    }

    public static Bitmap getScaledBitmap(DecodeBitmap decode, int maxWidth, int maxHeight, int orientation, boolean allowBlur, boolean scale) {
        if (orientation == 90 || orientation == 270) {
            int s = maxWidth;
            maxWidth = maxHeight;
            maxHeight = s;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        decode.decode(opts);

        opts.inJustDecodeBounds = false;
        opts.inScaled = false;

        final Runtime runtime = Runtime.getRuntime();
        final long availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        final boolean enoughMemory = (opts.outWidth * opts.outHeight * 4L + maxWidth * maxHeight * 4L) * 1.1 <= availableMemory;

        if (opts.outWidth <= maxWidth && opts.outHeight <= maxHeight) {
            return decode.decode(opts);
        }

        if (scale && enoughMemory && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            Bitmap bitmap = decode.decode(opts);

            final float scaleX = maxWidth / (float) bitmap.getWidth(), scaleY = maxHeight / (float) bitmap.getHeight();
            float s = Math.max(scaleX, scaleY);
//            if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
//                scale = Math.min(scale * 2, 1);
//            }
            final int w = (int) (bitmap.getWidth() * s), h = (int) (bitmap.getHeight() * s);

            Bitmap scaledBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

            Utilities.libyuvARGBSaleBitmap(bitmap, scaledBitmap, Utilities.libyuv_ScaleFilter.Box);

            int blurRadius = Utilities.clamp(Math.round(1f / s), 8, 0);
//            if (allowBlur && blurRadius > 0) {
//                Utilities.stackBlurBitmap(scaledBitmap, blurRadius);
//            }

            return scaledBitmap;
        } else {
            opts.inScaled = true;
            final float scaleX = maxWidth / (float) opts.outWidth;
            final float scaleY = maxHeight / (float) opts.outHeight;
            if (scaleX > scaleY) {
                opts.inDensity = opts.outWidth;
                opts.inTargetDensity = maxWidth;
            } else {
                opts.inDensity = opts.outHeight;
                opts.inTargetDensity = maxHeight;
            }
            return decode.decode(opts);
        }
    }

    public static void setupScale(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long availableMemory = maxMemory - usedMemory;
        final boolean enoughMemory = options.outWidth * options.outHeight * 4L * 2L <= availableMemory;
        if (!enoughMemory || Math.max(options.outWidth, options.outHeight) > 4200 || SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW) {
//            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inScaled = true;
            options.inDensity = options.outWidth;
            options.inTargetDensity = reqWidth;
        }
    }

    public static File makeCacheFile(final int account, boolean video) {
        return makeCacheFile(account, video ? "mp4" : "jpg");
    }

    public static File makeCacheFile(final int account, String ext) {
        TLRPC.TL_fileLocationToBeDeprecated location = new TLRPC.TL_fileLocationToBeDeprecated();
        location.volume_id = Integer.MIN_VALUE;
        location.dc_id = Integer.MIN_VALUE;
        location.local_id = SharedConfig.getLastLocalId();
        location.file_reference = new byte[0];

        TLObject object;
        if ("mp4".equals(ext) || "webm".equals(ext)) {
            TLRPC.VideoSize videoSize = new TLRPC.TL_videoSize_layer127();
            videoSize.location = location;
            object = videoSize;
        } else {
            TLRPC.PhotoSize photoSize = new TLRPC.TL_photoSize_layer127();
            photoSize.location = location;
            object = photoSize;
        }

        return FileLoader.getInstance(account).getPathToAttach(object, ext, true);
    }

    public static class HDRInfo {

        public int colorStandard;
        public int colorRange;
        public int colorTransfer;

        public float maxlum;
        public float minlum;

        public int getHDRType() {
//            if (maxlum <= 0 && minlum <= 0) {
//                return 0;
//            } else
            if (colorStandard == MediaFormat.COLOR_STANDARD_BT2020) {
                if (colorTransfer == MediaFormat.COLOR_TRANSFER_HLG) {
                    return 1;
                } else if (colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084) {
                    return 2;
                }
            }
            return 0;
        }
    }
}
