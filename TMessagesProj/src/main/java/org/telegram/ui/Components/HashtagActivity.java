package org.telegram.ui.Components;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatActivityContainer;

public class HashtagActivity extends BaseFragment {

    private final String query;

    public HashtagActivity(String query) {
        this(query, null);
    }
    public HashtagActivity(String query, Theme.ResourcesProvider resourcesProvider) {
        super();
        setResourceProvider(resourcesProvider);

        if (query == null) {
            query = "";
        }
        query = query.trim();
        if (!query.startsWith("#") && !query.startsWith("$"))
            query = "#" + query;
        int atIndex = query.indexOf("@");
        final String hashtag, username;
        if (atIndex > 0) {
            hashtag = query.substring(0, atIndex);
            username = query.substring(atIndex + 1);
        } else {
            hashtag = query;
            username = null;
        }
        this.query = hashtag + (!TextUtils.isEmpty(username) ? "@" + username : "");
    }

    // LoogriGram: a bar above the channel posts offered the public stories
    // found for the hashtag and opened them as a grid over the posts.
    // Stories are removed, as on desktop; the channel posts are what is left.
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(query);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setCastShadows(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        HashtagSearchController.getInstance(currentAccount).clearSearchResults(ChatActivity.SEARCH_CHANNEL_POSTS);
        Bundle args = new Bundle();
        args.putInt("chatMode", ChatActivity.MODE_SEARCH);
        args.putInt("searchType", ChatActivity.SEARCH_CHANNEL_POSTS);
        args.putString("searchHashtag", query);
        ChatActivityContainer chatContainer = new ChatActivityContainer(context, getParentLayout(), args) {
            boolean activityCreated = false;
            @Override
            protected void initChatActivity() {
                if (!activityCreated) {
                    activityCreated = true;
                    super.initChatActivity();
                }
            }
        };

        frameLayout.addView(chatContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView;
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }
}
