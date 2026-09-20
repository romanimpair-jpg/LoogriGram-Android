package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * LoogriGram: a coloured icon with a title, a subtitle and an optional badge.
 *
 * This was an inner class of AffiliateProgramFragment - a screen for earning
 * commission, which goes with the rest of the money removal. The cell is a
 * plain list row and says nothing about money by itself; it was simply the
 * only place upstream defined one of this shape, so four other screens reach
 * into that fragment for it. Moved here so they no longer have to.
 *
 * Upstream's code, unchanged apart from being de-nested.
 */
public class ColorfulTextCell extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;

    private final ImageView imageView;
    private final FrameLayout.LayoutParams imageViewLayoutParams;
    private final LinearLayout textLayout;
    private final FrameLayout.LayoutParams textLayoutLayoutParams;
    private final TextView titleView;
    private final TextView textView;
    private final ImageView arrowView;
    private final TextView percentView;

    public ColorfulTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        imageView = new ImageView(context);
        imageView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, imageViewLayoutParams = LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP, 17, 14.33f, 0, 0));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        addView(textLayout, textLayoutLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 62, 10, 40, 8.66f));

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 3, 0, 0));

        arrowView = new ImageView(context);
        arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrack, resourcesProvider), PorterDuff.Mode.SRC_IN));
        arrowView.setImageResource(R.drawable.msg_arrowright);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        addView(arrowView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        percentView = new TextView(context);
        percentView.setTextColor(0xFFFFFFFF);
        percentView.setBackground(Theme.createRoundRectDrawable(dp(4), Theme.getColor(Theme.key_color_green, resourcesProvider)));
        percentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        percentView.setTypeface(AndroidUtilities.bold());
        percentView.setPadding(dp(5), 0, dp(4), 0);
        percentView.setGravity(Gravity.CENTER);
        percentView.setVisibility(View.GONE);
        addView(percentView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 18, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 35.33f, 0));
    }

    public void set(int color, int iconResId, CharSequence title, CharSequence text) {
        imageView.setImageResource(iconResId);
        imageView.setBackground(Theme.createRoundRectDrawable(dp(9), color));

        titleView.setText(title);
        if (TextUtils.isEmpty(text)) {
            imageViewLayoutParams.topMargin = dp(10);
            imageViewLayoutParams.bottomMargin = dp(10);
            titleView.setTypeface(null);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textLayoutLayoutParams.topMargin = 0;
            textLayoutLayoutParams.bottomMargin = 0;
            textLayoutLayoutParams.gravity = Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL;
            textView.setVisibility(View.GONE);
        } else {
            imageViewLayoutParams.topMargin = dp(14.33f);
            imageViewLayoutParams.bottomMargin = dp(10);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textLayoutLayoutParams.topMargin = dp(10);
            textLayoutLayoutParams.bottomMargin = dp(8.66f);
            textLayoutLayoutParams.gravity = Gravity.FILL_HORIZONTAL | Gravity.TOP;
            textView.setText(text);
            textView.setVisibility(View.VISIBLE);
        }
    }

    public void setPercent(CharSequence percent) {
        if (TextUtils.isEmpty(percent)) {
            percentView.setVisibility(View.GONE);
        } else {
            percentView.setVisibility(View.VISIBLE);
            percentView.setText(percent);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public static class Factory extends UItem.UItemFactory<ColorfulTextCell> {
        static { setup(new Factory()); }

        @Override
        public ColorfulTextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new ColorfulTextCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((ColorfulTextCell) view).set(
                item.intValue, item.iconResId,
                item.text, item.subtext
            );
        }

        public static UItem as(int id, int color, int iconResId, CharSequence title, CharSequence text) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.intValue = color;
            item.iconResId = iconResId;
            item.text = title;
            item.subtext = text;
            return item;
        }
    }

}
