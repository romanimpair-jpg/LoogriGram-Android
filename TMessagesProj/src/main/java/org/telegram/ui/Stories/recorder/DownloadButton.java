package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

// LoogriGram: this was the story editor's save-to-gallery button. Stories
// are not posted here; what is left is the progress toast StickerMakerView
// shows.
public class DownloadButton {

    public static class PreparingVideoToast extends View {

        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint greyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ButtonBounce cancelButton = new ButtonBounce(this);

        private RLottieDrawable lottieDrawable;

        private final StaticLayout preparingLayout;
        private final float preparingLayoutWidth, preparingLayoutLeft;

        private StaticLayout doneLayout;
        private float doneLayoutWidth, doneLayoutLeft;

        public PreparingVideoToast(Context context) {
            this(context, LocaleController.getString(R.string.PreparingVideo));
        }

        public PreparingVideoToast(Context context, String text) {
            super(context);

            dimPaint.setColor(0x5a000000);
            textPaint.setColor(0xffffffff);
            textPaint2.setColor(0xffffffff);
            backgroundPaint.setColor(0xcc282828);
            whitePaint.setColor(0xffffffff);
            greyPaint.setColor(0x33ffffff);

            whitePaint.setStyle(Paint.Style.STROKE);
            whitePaint.setStrokeCap(Paint.Cap.ROUND);
            whitePaint.setStrokeWidth(dp(4));
            greyPaint.setStyle(Paint.Style.STROKE);
            greyPaint.setStrokeCap(Paint.Cap.ROUND);
            greyPaint.setStrokeWidth(dp(4));

            textPaint.setTextSize(dp(14));
            textPaint2.setTextSize(dpf2(14.66f));

            preparingLayout = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
            preparingLayoutWidth = preparingLayout.getLineCount() > 0 ? preparingLayout.getLineWidth(0) : 0;
            preparingLayoutLeft = preparingLayout.getLineCount() > 0 ? preparingLayout.getLineLeft(0) : 0;

            show();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == lottieDrawable || super.verifyDrawable(who);
        }

        private boolean shown = false;
        private final AnimatedFloat showT = new AnimatedFloat(0, this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean preparing = true;
        private float progress = 0;
        private final AnimatedFloat t = new AnimatedFloat(this);
        private final AnimatedFloat progressT = new AnimatedFloat(this);

        private final RectF prepareRect = new RectF();
        private final RectF toastRect = new RectF();
        private final RectF currentRect = new RectF();
        private final RectF hiddenRect = new RectF();

        private boolean deleted;

        @Override
        protected void onDraw(Canvas canvas) {
            final int restore = canvas.getSaveCount();
            final float showT = this.showT.set(shown ? 1 : 0);
            final float t = this.t.set(preparing ? 0 : 1);

            dimPaint.setAlpha((int) (0x5a * (1f - t) * showT));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

            final float prepareWidth = Math.max(preparingLayoutWidth, dp(54)) + dp(21 + 21);
            final float prepareHeight = dp(21 + 54 + 18 + 18) + preparingLayout.getHeight();
            prepareRect.set(
                (getWidth() - prepareWidth) / 2f,
                (getHeight() - prepareHeight) / 2f,
                (getWidth() + prepareWidth) / 2f,
                (getHeight() + prepareHeight) / 2f
            );

            final float toastWidth = dp(9 + 36 + 7 + 22) + doneLayoutWidth;
            final float toastHeight = dp(6 + 36 + 6);
            toastRect.set(
                (getWidth() - toastWidth) / 2f,
                (getHeight() - toastHeight) / 2f,
                (getWidth() + toastWidth) / 2f,
                (getHeight() + toastHeight) / 2f
            );

            AndroidUtilities.lerp(prepareRect, toastRect, t, currentRect);
            if (showT < 1 && preparing) {
                hiddenRect.set(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, getHeight() / 2f);
                AndroidUtilities.lerp(hiddenRect, currentRect, showT, currentRect);
            }
            if (showT < 1 && !preparing) {
                canvas.scale(lerp(.8f, 1f, showT), lerp(.8f, 1f, showT), currentRect.centerX(), currentRect.centerY());
            }
            backgroundPaint.setAlpha((int) (0xcc * showT));
            canvas.drawRoundRect(currentRect, dp(10), dp(10), backgroundPaint);
            canvas.save();
            canvas.clipRect(currentRect);
            if (t < 1) {
                drawPreparing(canvas, showT * (1f - t));
            }
            if (t > 0) {
                drawToast(canvas, showT * t);
            }
            canvas.restoreToCount(restore);

            if (showT <= 0 && !shown && !deleted) {
                deleted = true;
                post(() -> {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(this);
                    }
                });
            }
        }

        private void drawPreparing(Canvas canvas, float alpha) {
            final float progress = this.progressT.set(this.progress);

            final float cx = prepareRect.centerX();
            final float cy = prepareRect.top + dp(21 + 27);
            final float r = dp(25);

            greyPaint.setAlpha((int) (0x33 * alpha));
            canvas.drawCircle(cx, cy, r, greyPaint);
            AndroidUtilities.rectTmp.set(cx - r, cy - r, cx + r, cy + r);
            whitePaint.setAlpha((int) (0xFF * alpha));
            whitePaint.setStrokeWidth(dp(4));
            canvas.drawArc(AndroidUtilities.rectTmp, -90, progress * 360, false, whitePaint);

            final float cancelButtonScale = cancelButton.getScale(.15f);
            canvas.save();
            canvas.scale(cancelButtonScale, cancelButtonScale, cx, cy);
            whitePaint.setStrokeWidth(dp(3.4f));
            canvas.drawLine(cx - dp(7), cy - dp(7), cx + dp(7), cy + dp(7), whitePaint);
            canvas.drawLine(cx - dp(7), cy + dp(7), cx + dp(7), cy - dp(7), whitePaint);
            canvas.restore();

            canvas.save();
            canvas.translate(
                prepareRect.left + dp(21) - preparingLayoutLeft,
                prepareRect.bottom - dp(18) - preparingLayout.getHeight()
            );
            textPaint.setAlpha((int) (0xFF * alpha));
            preparingLayout.draw(canvas);
            canvas.restore();
        }

        private void drawToast(Canvas canvas, float alpha) {
            if (lottieDrawable != null) {
                lottieDrawable.setAlpha((int) (0xFF * alpha));
                lottieDrawable.setBounds(
                    (int) (toastRect.left + dp(9)),
                    (int) (toastRect.top + dp(6)),
                    (int) (toastRect.left + dp(9 + 36)),
                    (int) (toastRect.top + dp(6 + 36))
                );
                lottieDrawable.draw(canvas);
            }

            if (doneLayout != null) {
                canvas.save();
                canvas.translate(toastRect.left + dp(9 + 36 + 7) - doneLayoutLeft, toastRect.centerY() - doneLayout.getHeight() / 2f);
                textPaint2.setAlpha((int) (0xFF * alpha));
                doneLayout.draw(canvas);
                canvas.restore();
            }
        }

        public void setProgress(float progress) {
            this.progress = progress;
            invalidate();
        }

        public void setDone(int resId, CharSequence text, int delay) {
            if (lottieDrawable != null) {
                lottieDrawable.setCallback(null);
                lottieDrawable.recycle(true);
            }

            lottieDrawable = new RLottieDrawable(resId, "" + resId, dp(36), dp(36));
            lottieDrawable.setCallback(this);
            lottieDrawable.start();

            doneLayout = new StaticLayout(text, textPaint2, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
            doneLayoutWidth = doneLayout.getLineCount() > 0 ? doneLayout.getLineWidth(0) : 0;
            doneLayoutLeft = doneLayout.getLineCount() > 0 ? doneLayout.getLineLeft(0) : 0;

            preparing = false;
            invalidate();
            if (hideRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            }
            AndroidUtilities.runOnUIThread(hideRunnable = this::hide, delay);
        }

        private Runnable hideRunnable;
        public void hide() {
            if (hideRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
                hideRunnable = null;
            }
            this.shown = false;
            invalidate();
        }

        public void show() {
            this.shown = true;
            invalidate();
        }

        private Runnable onCancel;
        public void setOnCancelListener(Runnable onCancel) {
            this.onCancel = onCancel;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean hit = currentRect.contains(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN && (preparing || hit)) {
                cancelButton.setPressed(hit);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (cancelButton.isPressed()) {
                    if (hit) {
                        if (preparing) {
                            if (onCancel != null) {
                                onCancel.run();
                            }
                        } else {
                            hide();
                        }
                    }
                    cancelButton.setPressed(false);
                    return true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                cancelButton.setPressed(false);
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
