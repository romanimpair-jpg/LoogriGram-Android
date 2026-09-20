package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * LoogriGram: an icon with a title and a subtitle under it.
 *
 * This was ExplainStarsSheet.FeatureCell, renamed on the way out because four
 * other classes in the tree are already called FeatureCell and a top-level one
 * of that name would clash with each of them the moment it was imported.
 *
 * It was an inner class of ExplainStarsSheet - a "what are Stars" screen,
 * which goes with the rest of the money removal. The row itself says nothing
 * about money: it is an icon, a title and a link-aware subtitle, and the
 * places outside the money screens that build lists of them - the login
 * screen, the passkeys screen and the frozen-account alert - have no other
 * use for that sheet. So it moves here and the sheet can go.
 *
 * Upstream's code, unchanged apart from being de-nested.
 */
public class FeatureRow extends LinearLayout {

    public static final int STYLE_SHEET = 1;

    public final ImageView imageView;
    public final LinearLayout textLayout;
    public final TextView titleView;
    public final LinkSpanDrawable.LinksTextView subtitleView;

    public FeatureRow(Context context, int style, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        setOrientation(HORIZONTAL);

        setPadding(dp(style == STYLE_SHEET ? 11 : 32), 0, dp(style == STYLE_SHEET ? 11 : 32), dp(style == STYLE_SHEET ? 8 : 12));

        imageView = new ImageView(context);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createLinear(24, 24, Gravity.TOP | Gravity.LEFT, 0, 6, 16, 0));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(VERTICAL);

        titleView = new LinkSpanDrawable.LinksTextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 3));

        subtitleView = new LinkSpanDrawable.LinksTextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL));

        addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
    }

    public void set(int iconResId, CharSequence title, CharSequence text) {
        imageView.setImageResource(iconResId);
        titleView.setText(title);
        subtitleView.setText(text);
    }

    public void setTitle(CharSequence text) {
        titleView.setText(text);
    }

    public void setSubtitle(CharSequence text) {
        subtitleView.setText(text);
    }

    public static class Factory extends UItem.UItemFactory<FeatureRow> {
        static { setup(new Factory()); }

        @Override
        public FeatureRow createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new FeatureRow(context, 0, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((FeatureRow) view).set(
                item.intValue, item.text, item.subtext
            );
        }

        public static UItem of(int iconResId, CharSequence title, CharSequence text) {
            UItem item = UItem.ofFactory(Factory.class);
            item.selectable = false;
            item.intValue = iconResId;
            item.text = title;
            item.subtext = text;
            return item;
        }

    }
}
