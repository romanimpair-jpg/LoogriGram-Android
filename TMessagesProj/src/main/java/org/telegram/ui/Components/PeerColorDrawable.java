package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

// LoogriGram: moved out of PeerColorActivity, which is deleted with our own name
// and profile colour. The admin log still shows a channel's colour changes with
// it (PeerColorSpan). The collectible colours, the emoji drawn over them and the
// stroke went with the screen, their only user.
public class PeerColorDrawable extends Drawable {

    public static PeerColorDrawable from(int currentAccount, int colorId) {
        if (colorId < 7) {
            return new PeerColorDrawable(Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]));
        }
        MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
        MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
        return from(peerColor, false);
    }

    public static PeerColorDrawable fromProfile(int currentAccount, int colorId) {
        MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
        MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
        return from(peerColor, true);
    }

    private static PeerColorDrawable from(MessagesController.PeerColor peerColor, boolean fromProfile) {
        if (peerColor == null) {
            return new PeerColorDrawable(0, 0, 0);
        }
        return new PeerColorDrawable(peerColor.getColor1(), !fromProfile || peerColor.hasColor6(Theme.isCurrentThemeDark()) ? peerColor.getColor2() : peerColor.getColor1(), fromProfile ? peerColor.getColor1() : peerColor.getColor3());
    }

    private float radius = dpf2(21.333f / 2f);

    public PeerColorDrawable setRadius(float r) {
        this.radius = r;
        initPath();
        return this;
    }

    private final boolean hasColor3;
    private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint color2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint color3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path color2Path = new Path();
    private final Path clipCirclePath = new Path();

    public PeerColorDrawable(int color1, int color2, int color3) {
        hasColor3 = color3 != color1;
        color1Paint.setColor(color1);
        color2Paint.setColor(color2);
        color3Paint.setColor(color3);

        initPath();
    }

    private void initPath() {
        clipCirclePath.rewind();
        clipCirclePath.addCircle(radius, radius, radius, Path.Direction.CW);
        color2Path.rewind();
        color2Path.moveTo(radius * 2, 0);
        color2Path.lineTo(radius * 2, radius * 2);
        color2Path.lineTo(0, radius * 2);
        color2Path.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().centerX() - radius, getBounds().centerY() - radius);
        canvas.clipPath(clipCirclePath);
        canvas.drawPaint(color1Paint);
        canvas.drawPath(color2Path, color2Paint);
        if (hasColor3) {
            AndroidUtilities.rectTmp.set(radius - dp(3.66f), radius - dp(3.66f), radius + dp(3.66f), radius + dp(3.66f));
            canvas.rotate(45, radius, radius);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), color3Paint);
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) (radius * 2);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (radius * 2);
    }
}
