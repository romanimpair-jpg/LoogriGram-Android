package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

// LoogriGram: moved out of PeerColorActivity, which is deleted with our own
// name and profile colour. FilterCreateActivity picks a folder colour with it.
public class PeerColorGrid extends View {
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    { backgroundPaint.setStyle(Paint.Style.STROKE); }

    public static final int TYPE_FOLDER_TAG = 2;

    public class ColorButton {
        private final Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path circlePath = new Path();
        private final Path color2Path = new Path();
        private boolean hasColor2, hasColor3;
        private boolean hasClose;

        private Path closePath;
        private Paint closePaint;

        private final ButtonBounce bounce = new ButtonBounce(PeerColorGrid.this);

        public ColorButton() {}

        public void set(int color) {
            hasColor2 = hasColor3 = false;
            paint1.setColor(color);
        }

        public void set(int color1, int color2) {
            hasColor2 = true;
            hasColor3 = false;
            paint1.setColor(color1);
            paint2.setColor(color2);
        }

        public void setClose(boolean close) {
            hasClose = close;
        }

        public void set(MessagesController.PeerColor color) {
            if (color == null) {
                return;
            }
            final boolean dark = resourcesProvider == null ? Theme.isCurrentThemeDark() : resourcesProvider.isDark();
            if (type == PAGE_NAME) {
                if (dark && color.hasColor2() && !color.hasColor3()) {
                    paint1.setColor(color.getColor(1, resourcesProvider));
                    paint2.setColor(color.getColor(0, resourcesProvider));
                } else {
                    paint1.setColor(color.getColor(0, resourcesProvider));
                    paint2.setColor(color.getColor(1, resourcesProvider));
                }
                paint3.setColor(color.getColor(2, resourcesProvider));
                hasColor2 = color.hasColor2(dark);
                hasColor3 = color.hasColor3(dark);
            } else {
                paint1.setColor(color.getColor(0, resourcesProvider));
                paint2.setColor(color.hasColor6(dark) ? color.getColor(1, resourcesProvider) : color.getColor(0, resourcesProvider));
                hasColor2 = color.hasColor6(dark);
                hasColor3 = false;
            }
        }

        private boolean selected;
        private final AnimatedFloat selectedT = new AnimatedFloat(PeerColorGrid.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public void setSelected(boolean selected, boolean animated) {
            this.selected = selected;
            if (!animated) {
                selectedT.set(selected, true);
            }
            invalidate();
        }

        public int id;
        private final RectF bounds = new RectF();
        public final RectF clickBounds = new RectF();
        public void layout(RectF bounds) {
            this.bounds.set(bounds);
        }
        public void layoutClickBounds(RectF bounds) {
            this.clickBounds.set(bounds);
        }

        protected void draw(Canvas canvas) {
            canvas.save();
            final float s = bounce.getScale(.05f);
            canvas.scale(s, s, bounds.centerX(), bounds.centerY());

            canvas.save();
            circlePath.rewind();
            circlePath.addCircle(bounds.centerX(), bounds.centerY(), Math.min(bounds.height() / 2f, bounds.width() / 2f), Path.Direction.CW);
            canvas.clipPath(circlePath);
            canvas.drawPaint(paint1);
            if (hasColor2) {
                color2Path.rewind();
                color2Path.moveTo(bounds.right, bounds.top);
                color2Path.lineTo(bounds.right, bounds.bottom);
                color2Path.lineTo(bounds.left, bounds.bottom);
                color2Path.close();
                canvas.drawPath(color2Path, paint2);
            }
            canvas.restore();

            if (hasColor3) {
                canvas.save();
                final float color3Size = (bounds.width() * .315f);
                AndroidUtilities.rectTmp.set(
                    bounds.centerX() - color3Size / 2f,
                    bounds.centerY() - color3Size / 2f,
                    bounds.centerX() + color3Size / 2f,
                    bounds.centerY() + color3Size / 2f
                );
                canvas.rotate(45f, bounds.centerX(), bounds.centerY());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), paint3);
                canvas.restore();
            }

            final float selectT = selectedT.set(selected);

            if (selectT > 0) {
                backgroundPaint.setStrokeWidth(dpf2(2));
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                canvas.drawCircle(
                    bounds.centerX(), bounds.centerY(),
                    Math.min(bounds.height() / 2f, bounds.width() / 2f) + backgroundPaint.getStrokeWidth() * lerp(.5f, -2f, selectT),
                    backgroundPaint
                );
            }

            if (hasClose) {
                if (closePath == null) {
                    closePath = new Path();
                }
                if (closePaint == null) {
                    closePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    closePaint.setColor(0xffffffff);
                    closePaint.setStyle(Paint.Style.STROKE);
                    closePaint.setStrokeCap(Paint.Cap.ROUND);
                }
                closePaint.setStrokeWidth(dp(2));
                closePath.rewind();
                final float r = lerp(dp(5), dp(4), selectT);
                closePath.moveTo(bounds.centerX() - r, bounds.centerY() - r);
                closePath.lineTo(bounds.centerX() + r, bounds.centerY() + r);
                closePath.moveTo(bounds.centerX() + r, bounds.centerY() - r);
                closePath.lineTo(bounds.centerX() - r, bounds.centerY() + r);
                canvas.drawPath(closePath, closePaint);
            }

            canvas.restore();
        }

        private boolean pressed;
        public boolean isPressed() {
            return pressed;
        }

        public void setPressed(boolean pressed) {
            bounce.setPressed(this.pressed = pressed);
        }
    }

    private final int type;
    private final int currentAccount;

    private ColorButton[] buttons;

    public PeerColorGrid(Context context, int type, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.type = type;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
    }

    public void updateColors() {
        if (buttons == null) return;
        final MessagesController mc = MessagesController.getInstance(currentAccount);
        final MessagesController.PeerColors peerColors = type == PAGE_NAME ? mc.peerColors : mc.profilePeerColors;
        for (int i = 0; i < buttons.length; ++i) {
            if (type == TYPE_FOLDER_TAG) {
                buttons[i].id = order[i];
                buttons[i].setClose(buttons[i].id < 0);
                buttons[i].set(Theme.getColor(order[i] < 0 ? Theme.key_avatar_backgroundGray : Theme.keys_avatar_nameInMessage[order[i] % Theme.keys_avatar_nameInMessage.length], resourcesProvider));
            } else if (i < 7 && type == PAGE_NAME) {
                buttons[i].id = order[i];
                buttons[i].set(Theme.getColor(Theme.keys_avatar_nameInMessage[order[i]], resourcesProvider));
            } else {
                final int id = i;
                if (peerColors != null && id >= 0 && id < peerColors.colors.size()) {
                    buttons[i].id = peerColors.colors.get(id).id;
                    buttons[i].set(peerColors.colors.get(id));
                }
            }
        }
        invalidate();
    }
    final int[] order = new int[] { 5, 3, 1, 0, 2, 4, 6, -1 };

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);

        final MessagesController mc = MessagesController.getInstance(currentAccount);
        final MessagesController.PeerColors peerColors = type == PAGE_NAME ? mc.peerColors : mc.profilePeerColors;
        int colorsCount = peerColors == null ? 0 : peerColors.colors.size();
        if (type == TYPE_FOLDER_TAG) {
            colorsCount = 8;
        }
        final int columns;
        if (type == TYPE_FOLDER_TAG) {
            columns = 8;
        } else if (type == PAGE_NAME) {
            columns = 7;
        } else {
            columns = 8;
        }

        final float iconSize = Math.min(dp(38 + 16), width / (columns + (columns + 1) * .28947f));
        final float horizontalSeparator = Math.min(iconSize * .28947f, dp(8));
        final float verticalSeparator = Math.min(iconSize * .315789474f, dp(11.33f));

        final int rows = colorsCount / columns;
        final int height = (int) (iconSize * rows + verticalSeparator * (rows + 1));

        setMeasuredDimension(width, height);

        if (buttons == null || buttons.length != colorsCount) {
            buttons = new ColorButton[colorsCount];
            for (int i = 0; i < colorsCount; ++i) {
                buttons[i] = new ColorButton();
                if (type == TYPE_FOLDER_TAG) {
                    buttons[i].id = order[i];
                    buttons[i].setClose(buttons[i].id < 0);
                    buttons[i].set(Theme.getColor(order[i] < 0 ? Theme.key_avatar_backgroundGray : Theme.keys_avatar_nameInMessage[order[i] % Theme.keys_avatar_nameInMessage.length], resourcesProvider));
                } else if (peerColors != null && i >= 0 && i < peerColors.colors.size()) {
                    buttons[i].id = peerColors.colors.get(i).id;
                    buttons[i].set(peerColors.colors.get(i));
                }
            }
        }
        final float itemsWidth = iconSize * columns + horizontalSeparator * (columns + 1);
        final float startX = (width - itemsWidth) / 2f + horizontalSeparator;
        if (buttons != null) {
            float x = startX, y = verticalSeparator;
            for (int i = 0; i < buttons.length; ++i) {
                AndroidUtilities.rectTmp.set(x, y, x + iconSize, y + iconSize);
                buttons[i].layout(AndroidUtilities.rectTmp);
                AndroidUtilities.rectTmp.inset(-horizontalSeparator / 2, -verticalSeparator / 2);
                buttons[i].layoutClickBounds(AndroidUtilities.rectTmp);
                buttons[i].setSelected(buttons[i].id == selectedColorId, false);

                if (i % columns == (columns - 1)) {
                    x = startX;
                    y += iconSize + verticalSeparator;
                } else {
                    x += iconSize + horizontalSeparator;
                }
            }
        }
    }

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean needDivider = true;
    public void setDivider(boolean needDivider) {
        this.needDivider = needDivider;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (buttons != null) {
            for (int i = 0; i < buttons.length; ++i) {
                buttons[i].draw(canvas);
            }
        }
        if (needDivider) {
            dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            canvas.drawRect(dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - dp(21), getMeasuredHeight(), dividerPaint);
        }
    }

    private int selectedColorId = 0;
    public void setSelected(int colorId, boolean animated) {
        selectedColorId = colorId;
        if (buttons != null) {
            for (int i = 0; i < buttons.length; ++i) {
                buttons[i].setSelected(buttons[i].id == colorId, animated);
            }
        }
    }
    public int getColorId() {
        return selectedColorId;
    }

    private Utilities.Callback<Integer> onColorClick;
    public void setOnColorClick(Utilities.Callback<Integer> onColorClick) {
        this.onColorClick = onColorClick;
    }

    private ColorButton pressedButton;
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        ColorButton button = null;
        if (buttons != null) {
            for (int i = 0; i < buttons.length; ++i) {
                if (buttons[i].clickBounds.contains(event.getX(), event.getY())) {
                    button = buttons[i];
                    break;
                }
            }
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedButton = button;
            if (button != null) {
                button.setPressed(true);
            }
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedButton != button) {
                if (pressedButton != null) {
                    pressedButton.setPressed(false);
                }
                if (button != null) {
                    button.setPressed(true);
                }
                if (pressedButton != null && button != null) {
                    if (onColorClick != null) {
                        onColorClick.run(button.id);
                    }
                }
                pressedButton = button;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (event.getAction() == MotionEvent.ACTION_UP && pressedButton != null) {
                if (onColorClick != null) {
                    onColorClick.run(pressedButton.id);
                }
            }
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].setPressed(false);
                }
            }
            pressedButton = null;
        }
        return true;
    }
}
