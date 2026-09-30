package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

// LoogriGram: the Premium screens' feature row. They are deleted; what stays is
// the plain layout - icon, title, description, arrow - that the Star rating
// sheet builds its three rows from. Binding a Premium feature to it (setData,
// the emoji status on the right, the list factory) went with the screens.
public class PremiumFeatureCell extends FrameLayout {

    public final SimpleTextView title;
    public final TextView description;
    public ImageView imageView;
    public final ImageView nextIcon;

    public PremiumFeatureCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        setClipChildren(false);
        linearLayout.setClipChildren(false);
        title = new SimpleTextView(context);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(15);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        description = new TextView(context);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        description.setLineSpacing(AndroidUtilities.dp(2), 1f);
        linearLayout.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1, 0, 0));

        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 62, 8, 48, 9));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(imageView, LayoutHelper.createFrame(28, 28, 0, 18, 12, 0, 0));

        nextIcon = new ImageView(context);
        nextIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        nextIcon.setImageResource(R.drawable.msg_arrowright);
        nextIcon.setColorFilter(Theme.getColor(Theme.key_switchTrack, resourcesProvider));
        addView(nextIcon, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 18, 0));
    }
}
