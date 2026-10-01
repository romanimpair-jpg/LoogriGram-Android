package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.HintView2;

// LoogriGram: the limit sheet's badge - an icon and the count on a pill,
// centred. This view was also a bar from the free limit to Premium's (the
// sheet always hid it, with setPremiumLocked), the channel boost level bar
// (setStatus, increaseCurrentValue) and the Star rating bar (setStarRating,
// animateStarRating), with the arrow, gradient and rolling digits those drew.
// Premium, boosts and the Star rating are gone; the badge is what is left.
public class LimitPreviewView extends LinearLayout {

    private final int icon;
    private final CounterView limitIcon;
    private boolean wasAnimation;
    private boolean animationCanPlay = true;

    public LimitPreviewView(@NonNull Context context, int icon, int currentValue) {
        super(context);
        this.icon = icon;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        if (icon != 0) {
            setPadding(0, dp(16), 0, 0);
            limitIcon = new CounterView(context);
            setIconValue(currentValue);
            limitIcon.setPadding(dp(24), dp(3), dp(24), dp(3));
            addView(limitIcon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT));
        } else {
            limitIcon = null;
        }
    }

    public void setIconValue(int currentValue) {
        if (limitIcon == null) {
            return;
        }
        final ColoredImageSpan span;
        if (currentValue < 0) {
            span = new ColoredImageSpan(R.drawable.warning_sign);
        } else {
            span = new ColoredImageSpan(icon);
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("d").setSpan(span, 0, 1, 0);
        spannableStringBuilder.append(" ").setSpan(new RelativeSizeSpan(0.8f), 1, 2, 0);
        spannableStringBuilder.append(LocaleController.formatNumber(currentValue, ','));
        limitIcon.setText(spannableStringBuilder);
        limitIcon.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (limitIcon == null) {
            return;
        }
        final int padding = dp(14);
        final float toX = padding + (getMeasuredWidth() - padding * 2) * 0.5f - limitIcon.getMeasuredWidth() / 2f;
        if (!wasAnimation && animationCanPlay) {
            wasAnimation = true;
            limitIcon.animate().alpha(1).scaleX(1).scaleY(1).setDuration(200).setInterpolator(new OvershootInterpolator()).start();
        } else if (!wasAnimation) {
            limitIcon.setAlpha(0);
            limitIcon.setScaleX(0);
            limitIcon.setScaleY(0);
        } else {
            limitIcon.setAlpha(1f);
            limitIcon.setScaleX(1f);
            limitIcon.setScaleY(1f);
        }
        limitIcon.setTranslationX(toX);
    }

    public void setType(int type) {
        if (limitIcon == null) {
            return;
        }
        if (type == LimitReachedBottomSheet.TYPE_LARGE_FILE) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder.append("d ").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
            // LoogriGram: "4 GB" for a Premium account. The account is never Premium.
            spannableStringBuilder.append("2 GB");
            limitIcon.setText(spannableStringBuilder);
        } else if (type == LimitReachedBottomSheet.TYPE_ADD_MEMBERS_RESTRICTED) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder.append("d").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
            limitIcon.setText(spannableStringBuilder);
        }
    }

    public void setDelayedAnimation() {
        animationCanPlay = false;
    }

    public void startDelayedAnimation() {
        animationCanPlay = true;
        requestLayout();
    }

    private static class CounterView extends View {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private StaticLayout textLayout;
        private float textWidth;
        private CharSequence text;

        CounterView(Context context) {
            super(context);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(dp(22));
            textPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            textWidth = HintView2.measureCorrectly(text, textPaint);
            textLayout = new StaticLayout(text, textPaint, (int) textWidth + dp(12), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            textWidth = 0;
            for (int i = 0; i < textLayout.getLineCount(); ++i) {
                textWidth = Math.max(textWidth, textLayout.getLineWidth(i));
            }
            setMeasuredDimension((int) (textWidth + getPaddingRight() + getPaddingLeft()), dp(44) + dp(8));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int h = getMeasuredHeight();
            AndroidUtilities.rectTmp.set(0, dp(3), getMeasuredWidth(), h - dp(3));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, PremiumGradient.getInstance().getPremiumLocakedPaint());
            if (textLayout != null) {
                canvas.save();
                canvas.translate((getMeasuredWidth() - textWidth) / 2f, (h - textLayout.getHeight()) / 2f);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        void setText(CharSequence text) {
            this.text = text;
        }
    }
}
