package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.PointF;


import org.telegram.ui.Components.Size;


public class PhotoFace {

    private float width;
    private float angle;

    private PointF foreheadPoint;

    private PointF eyesCenterPoint;
    private float eyesDistance;

    private PointF mouthPoint;
    private PointF chinPoint;

    // LoogriGram: the constructor took a Google Mobile Vision Face and turned
    // its eye, mouth and chin landmarks into the geometry below. Nothing
    // detects faces any more, so nothing constructs one of these - but the
    // class stays, because the mask placement code holds an
    // ArrayList<PhotoFace> and reads these getters. That list is now always
    // empty.
    //
    // The geometry itself was not Google's and is kept as upstream wrote it,
    // so restoring detection later means restoring one constructor.
    private PhotoFace() {
    }

    public boolean isSufficient() {
        return eyesCenterPoint != null;
    }

    private PointF transposePoint(PointF point, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        float bitmapW = sideward ? sourceBitmap.getHeight() : sourceBitmap.getWidth();
        float bitmapH = sideward ? sourceBitmap.getWidth() : sourceBitmap.getHeight();
        float x = targetSize.width * point.x / bitmapW;
        float y = targetSize.height * point.y / bitmapH;
        return new PointF(x, y);
    }

    public PointF getPointForAnchor(int anchor) {
        switch (anchor) {
            case 0: {
                return foreheadPoint;
            }

            case 1: {
                return eyesCenterPoint;
            }

            case 2: {
                return mouthPoint;
            }

            case 3: {
                return chinPoint;
            }

            default: {
                return null;
            }
        }
    }

    public float getWidthForAnchor(int anchor) {
        if (anchor == 1) {
            return eyesDistance;
        }
        return width;
    }

    public float getAngle() {
        return angle;
    }
 }
