package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.concurrent.TimeUnit;

// LoogriGram: the offer is a publishing time and nothing else. The price field,
// the Stars/TON tabs, the dollar equivalent, the balance shown over the sheet and
// the server's minimum and maximum are gone, and so is the top-up prompt the
// button opened when the balance was short. Every offer is made for free, which
// is upstream's own case.
public class MessageSuggestionOfferSheet extends BottomSheet {
    private final int mode;

    private final EditTextBoldCursor publishingTimeField;
    private final ButtonWithCounterView buttonView;

    private long selectedTime = -1;

    public static final int MODE_INPUT = 0;
    public static final int MODE_EDIT = 1;

    public MessageSuggestionOfferSheet(
        Context context,
        int currentAccount,
        long dialogId,
        MessageSuggestionParams startParams,
        ChatActivity chatActivity,
        Theme.ResourcesProvider resourcesProvider,
        int mode,
        Utilities.Callback<MessageSuggestionParams> callback
    ) {
        super(context, true, resourcesProvider);
        this.mode = mode;

        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);


        /* Header */

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(headerLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleView.setText(getString(mode == MODE_INPUT ?
            R.string.PostSuggestionsOfferTitle:
            R.string.PostSuggestionsOfferChangeTitle
        ));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1f, Gravity.FILL, 18 + 4, 0, 18 + 4, 0));

        ImageView closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeView);
        closeView.setOnClickListener(v -> dismiss());
        headerLayout.addView(closeView, LayoutHelper.createLinear(48, 48, 0, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 6, 0));

        /* Body */

        LinearLayout bodyLayout = new LinearLayout(context);
        bodyLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(bodyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        {
            publishingTimeField = new EditTextBoldCursor(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    return false;
                }
            };
            publishingTimeField.setCursorSize(dp(20));
            publishingTimeField.setCursorWidth(1.5f);
            publishingTimeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            publishingTimeField.setMaxLines(1);
            publishingTimeField.setBackground(null);
            publishingTimeField.setPadding(dp(16), dp(16), dp(16), dp(16));
            publishingTimeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            publishingTimeField.setFocusable(false);
            publishingTimeField.setClickable(false);
            publishingTimeField.setEnabled(false);

            OutlineTextContainerView publishingTimeOutline = new OutlineTextContainerView(context);
            publishingTimeOutline.setText(getString(R.string.PostSuggestionsOfferTitleTime));
            publishingTimeOutline.attachEditText(publishingTimeField);
            publishingTimeOutline.addView(publishingTimeField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 48, 0));
            ScaleStateListAnimator.apply(publishingTimeOutline, .02f, 1.2f);
            publishingTimeOutline.setOnClickListener(v -> AlertsCreator.createSuggestedMessageDatePickerDialog(context, selectedTime, (notify, scheduleDate, scheduleRepeatPeriod) -> {
                if (notify) {
                    setSelectedTime(scheduleDate, true);
                }
            }, resourcesProvider, AlertsCreator.SUGGEST_DATE_PICKER_MODE_EDIT).show());
            bodyLayout.addView(publishingTimeOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 18, 24, 18, 0));

            ImageView iconArrow = new ImageView(context);
            iconArrow.setImageResource(R.drawable.arrow_more);
            iconArrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
            publishingTimeOutline.addView(iconArrow, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

            TextView publishingTimeHint = new TextView(context);
            publishingTimeHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            publishingTimeHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PostSuggestionsAddTimeHint)));
            ssb.append(' ');
            ssb.append(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.PostSuggestionsAddTimeHint2, MessagesController.getInstance(currentAccount).config.starsSuggestedPostAgeMin.get(TimeUnit.HOURS))));
            publishingTimeHint.setText(ssb);

            bodyLayout.addView(publishingTimeHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 33, 4, 33, 24));
        }



        /* Footer */

        LinearLayout footerLayout = new LinearLayout(context);
        footerLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(footerLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setOnClickListener(v -> {
            if (chatActivity == null || !buttonView.isEnabled()) {
                return;
            }
            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                AccountFrozenAlert.show(currentAccount);
                return;
            }

            callback.run(MessageSuggestionParams.ofTime(selectedTime));
            dismiss();
        });
        if (mode == MODE_EDIT) {
            buttonView.setText(getString(R.string.PostSuggestionsOfferChangeUpdateTerms), false);
        }
        footerLayout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 18, 0, 18, 8));

        checkButtonOfferText(false);
        setSelectedTime(startParams.time, false);
        setCustomView(layout);

    }

    private void setSelectedTime(long selectedTime, boolean animated) {
        if (this.selectedTime != selectedTime) {
            this.selectedTime = selectedTime;
            this.publishingTimeField.setText(formatDateTime(selectedTime));
        }
    }

    private void checkButtonOfferText(boolean animated) {
        buttonView.setText(getString(mode == MODE_INPUT ?
            R.string.PostSuggestionsOfferForFree :
            R.string.PostSuggestionsOfferChangeUpdateTerms
        ), animated);
    }

    // LoogriGram: checkButtonEnabled stood here. It greyed the button out while the
    // price was empty or out of range; an offer with no time at all is upstream's
    // own "anytime, for free", so the button is never disabled.

    // LoogriGram: show() raised the keyboard for the price field, which is gone.
    // The date picker is a dialog of its own, so the sheet opens with no keyboard.

    public static String formatDateTime(long time) {
        if (time <= 0) {
            return LocaleController.getString(R.string.PostSuggestionsAnytime);
        } else {
            final String s = LocaleController.formatDateTime(time, true);
            if (!s.isEmpty())
                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
            return s;
        }
    }
}
