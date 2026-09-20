package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * LoogriGram: an icon beside a title and a line of explanatory text.
 *
 * This was FeatureIconCell - a nested class of the
 * commission screen, which goes with the rest of the money removal. The row
 * itself is generic: the Premium feature sheet builds three of them, and so
 * does StarGiftSheet, which has to keep working because it is what draws a
 * gift somebody was given.
 *
 * Renamed on the way out. Four classes in the tree already declare a nested
 * FeatureCell, and ui.Components.FeatureRow already holds the one extracted
 * from ExplainStarsSheet, so a second top-level FeatureCell would collide
 * with all of them the moment it was imported.
 *
 * Upstream's code, unchanged apart from the rename and being de-nested.
 */
public class FeatureIconCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private ImageView imageView;
    private LinearLayout textLayout;
    private TextView titleView;
    private TextView textView;

    public FeatureIconCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, false, resourcesProvider);
    }

    public FeatureIconCell(Context context, boolean compact, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 20, 2 + 9.46f, 0, 0));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 64, compact ? 2 : 2 + 7.8f, 24, compact ? 4 : 2 + 7.8f));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 1));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
    }

    public void set(int iconResId, CharSequence title, CharSequence text) {
        imageView.setImageResource(iconResId);
        titleView.setText(title);
        textView.setText(text);
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public static class Factory extends UItem.UItemFactory<FeatureIconCell> {
        static { setup(new Factory()); }

        @Override
        public FeatureIconCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new FeatureIconCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((FeatureIconCell) view).set(item.iconResId, item.text, item.subtext);
        }

        public static UItem as(int iconResId, CharSequence title, CharSequence text) {
            UItem item = UItem.ofFactory(Factory.class);
            item.iconResId = iconResId;
            item.text = title;
            item.subtext = text;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
