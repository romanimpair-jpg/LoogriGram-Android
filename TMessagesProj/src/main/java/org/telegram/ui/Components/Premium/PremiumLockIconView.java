package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class PremiumLockIconView extends ImageView {

    // LoogriGram: TYPE_REACTIONS (0), a gradient padlock with star particles
    // that nothing drew any more, and TYPE_REACTIONS_LOCK (2), the padlock on a
    // Premium effect in the emoji picker, are gone. What stays is the round
    // badge: the emoji tab's "add pack" mark and the gift card's lock and pin.
    public static int TYPE_STICKERS_PREMIUM_LOCKED = 1;
    public static int TYPE_GIFT_LOCK = 3;
    public static int TYPE_GIFT_PIN = 4;

    private float[] colorFloat = new float[3];
    private Theme.ResourcesProvider resourcesProvider;
    boolean attachedToWindow;

    public PremiumLockIconView(Context context, int type) {
        this(context, type, null);
    }

    public PremiumLockIconView(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setImageResource(R.drawable.msg_mini_premiumlock);
        if (type == TYPE_GIFT_LOCK) {
            setScaleType(ScaleType.CENTER);
            setImageResource(R.drawable.msg_archive_hide);
        } else if (type == TYPE_GIFT_PIN) {
            setScaleType(ScaleType.CENTER);
            setImageResource(R.drawable.msg_limit_pin);
        }
    }

    boolean colorRetrieved = false;
    public int currentColor = Color.WHITE;
    int color1, color2;
    Shader shader = null;

    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint oldShaderPaint;
    ImageReceiver imageReceiver;
    float shaderCrossfadeProgress = 1f;
    boolean waitingImage;
    boolean wasDrawn;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateGradient();
    }

    public void setColor(int color) {
        colorRetrieved = true;
        if (blendColor != null) {
            color = Theme.blendOver(color, blendColor);
        }
        if (currentColor != color) {
            currentColor = color;
            updateGradient();
            invalidate();
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (waitingImage) {
            if (imageReceiver != null && imageReceiver.getBitmap() != null) {
                waitingImage = false;
                setColor(AndroidUtilities.getDominantColor(imageReceiver.getBitmap()));
            } else {
                invalidate();
            }
        }
        if (paint != null) {
            float cx = getMeasuredWidth() / 2f;
            float cy = getMeasuredHeight() / 2f;
            if (oldShaderPaint == null) {
                shaderCrossfadeProgress = 1f;
            }
            if (shaderCrossfadeProgress != 1f) {
                paint.setAlpha((int) (255 * shaderCrossfadeProgress));
                canvas.drawCircle(cx, cy, cx, oldShaderPaint);
                canvas.drawCircle(cx, cy, cx, paint);
                shaderCrossfadeProgress += 16 / 150f;
                if (shaderCrossfadeProgress > 1f) {
                    shaderCrossfadeProgress = 1f;
                    oldShaderPaint = null;
                }
                invalidate();
                paint.setAlpha(255);
            } else {
                canvas.drawCircle(cx, cy, cx, paint);
            }
        }
        super.onDraw(canvas);
        wasDrawn = true;
    }

    public void setImageReceiver(ImageReceiver imageReceiver) {
        this.imageReceiver = imageReceiver;
        if (imageReceiver != null) {
            waitingImage = true;
            invalidate();
        }
    }

    private void updateGradient() {
        if (!attachedToWindow) {
            return;
        }
        if (getMeasuredHeight() != 0 && getMeasuredWidth() != 0) {
            int c1 = currentColor;
            int c2;
            Color.colorToHSV(c1, colorFloat);
            if (colorFloat[2] > 0.7f) {
                colorFloat[2] = 0.7f;
            }
            int baseColor = Color.HSVToColor(colorFloat);

            c2 = ColorUtils.blendARGB(baseColor, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0.5f);
            c1 = ColorUtils.blendARGB(baseColor, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0.4f);

            if (shader == null || color1 != c1 || color2 != c2) {
                if (wasDrawn) {
                    oldShaderPaint = paint;
                    oldShaderPaint.setAlpha(255);
                    shaderCrossfadeProgress = 0;
                }
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shader = new LinearGradient(0, getMeasuredHeight(), 0, 0, new int[]{color1 = c1, color2 = c2}, null, Shader.TileMode.CLAMP);
                paint.setShader(shader);
                invalidate();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        updateGradient();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        if (paint != null) {
            paint.setShader(null);
            paint = null;
        }
        shader = null;
        wasDrawn = false;
    }

    public void setWaitingImage() {
        waitingImage = true;
        wasDrawn = false;
        invalidate();
    }

    private Integer blendColor;
    public void setBlendWithColor(Integer color) {
        blendColor = color;
    }

    public boolean done() {
        return colorRetrieved;
    }

}
