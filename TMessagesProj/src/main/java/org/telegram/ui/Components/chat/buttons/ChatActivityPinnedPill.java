package org.telegram.ui.Components.chat.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

// LoogriGram: upstream's pinned bar - the pinned message's text across the
// top of the chat, with a list or close button - is gone. In its place this
// pill floats at the top right of the messages, level with the floating date
// and drawn like it: a pin and how many messages are pinned. ChatActivity
// gives it its click (the next pinned message up) and its long press (a menu
// that hides it or opens the pinned messages list). Desktop has the same
// pill.
@SuppressLint("ViewConstructor")
public class ChatActivityPinnedPill extends View {

    private final View backgroundContainer;
    private final Theme.ResourcesProvider resourcesProvider;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;
    private final RectF rect = new RectF();
    private final int textHeight;
    private final float textBaseline;

    private String text = "";
    private float textWidth;
    private int count = -1;
    private boolean shown;

    // The background container is the view that draws the wallpaper, so the
    // service background lines up with it as the floating date's does.
    public ChatActivityPinnedPill(Context context, View backgroundContainer, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.backgroundContainer = backgroundContainer;
        this.resourcesProvider = resourcesProvider;

        // The floating date's text: ChatActionCell's size, bold.
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(Math.max(16, SharedConfig.fontSize) - 2));
        final Paint.FontMetrics metrics = textPaint.getFontMetrics();
        textHeight = (int) Math.ceil(metrics.descent - metrics.ascent);
        textBaseline = dp(3) - metrics.ascent;

        // The pin a pinned message wears beside its time.
        icon = ContextCompat.getDrawable(context, R.drawable.msg_pin_mini).mutate();

        updateColors();
        setVisibility(GONE);
        setAlpha(0f);
    }

    public void updateColors() {
        final int color = Theme.getColor(Theme.key_chat_serviceText, resourcesProvider);
        textPaint.setColor(color);
        icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        invalidate();
    }

    public void setCount(int count) {
        if (this.count == count) {
            return;
        }
        this.count = count;
        text = String.valueOf(count);
        textWidth = textPaint.measureText(text);
        setContentDescription(LocaleController.getString(R.string.LoogriGramPinnedMessages) + ", " + text);
        requestLayout();
        invalidate();
    }

    public void setShown(boolean show, boolean animated) {
        if (shown == show) {
            return;
        }
        shown = show;
        animate().cancel();
        if (animated) {
            if (show) {
                setVisibility(VISIBLE);
            }
            animate()
                .alpha(show ? 1f : 0f)
                .scaleX(show ? 1f : 0.7f)
                .scaleY(show ? 1f : 0.7f)
                .setDuration(180)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .withEndAction(() -> {
                    if (!shown) {
                        setVisibility(GONE);
                    }
                })
                .start();
        } else {
            setAlpha(show ? 1f : 0f);
            setScaleX(show ? 1f : 0.7f);
            setScaleY(show ? 1f : 0.7f);
            setVisibility(show ? VISIBLE : GONE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = dp(7) + icon.getIntrinsicWidth() + dp(3) + (int) Math.ceil(textWidth) + dp(9);
        setMeasuredDimension(width, textHeight + dp(6));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Grows from its right end, which stays put by the screen edge.
        setPivotX(w);
        setPivotY(h / 2f);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float radius = getHeight() / 2f;
        rect.set(0, 0, getWidth(), getHeight());
        Theme.applyServiceShaderMatrixForView(this, backgroundContainer, resourcesProvider);
        canvas.drawRoundRect(rect, radius, radius, Theme.getThemePaint(isPressed()
            ? Theme.key_paint_chatActionBackgroundSelected
            : Theme.key_paint_chatActionBackground, resourcesProvider));
        if (resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService()) {
            canvas.drawRoundRect(rect, radius, radius, Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider));
        }

        final int iconLeft = dp(7);
        final int iconTop = (getHeight() - icon.getIntrinsicHeight()) / 2;
        icon.setBounds(iconLeft, iconTop, iconLeft + icon.getIntrinsicWidth(), iconTop + icon.getIntrinsicHeight());
        icon.draw(canvas);
        canvas.drawText(text, iconLeft + icon.getIntrinsicWidth() + dp(3), textBaseline, textPaint);
    }
}
