package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.HintView2;

// LoogriGram: the empty state of a group's welcome messages, taken out of
// Business's QuickRepliesEmptyView, which drew it along with the empty states
// of a quick reply and of the greeting and away messages. Those are gone.
public class WelcomeMessagesEmptyView extends LinearLayout {

    private final TextView titleView;
    private final TextView descriptionView;
    private final RLottieImageView imageView;

    public WelcomeMessagesEmptyView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        titleView.setLineSpacing(dp(1.66f), 1);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(LocaleController.getString(R.string.WelcomeMessageEmptyTitle));

        descriptionView = new TextView(context);
        descriptionView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        descriptionView.setLineSpacing(dp(2), 1);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionView.setText(LocaleController.getString(R.string.WelcomeMessageEmptySubtitle));
        descriptionView.setMaxWidth(Math.min(dp(160), HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint())));

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setImageResource(R.drawable.large_greeting);

        addView(imageView, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 17, 20, 9));
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 0, 20, 6));
        addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 0, 22, 19));

        titleView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, resourcesProvider));
        descriptionView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, resourcesProvider));
    }
}
