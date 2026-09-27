/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

public class MessagesSearchAdapter extends RecyclerListView.SelectionAdapter {

    private final Context mContext;
    private final HashSet<Integer> messageIds = new HashSet<>();
    private final ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private final BaseFragment fragment;

    public int loadedCount;
    public int flickerCount;

    private int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private int searchType;

    private boolean isSavedMessages;

    public MessagesSearchAdapter(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, int searchType, boolean isSavedMessages) {
        this.resourcesProvider = resourcesProvider;
        mContext = context;
        this.fragment = fragment;
        this.searchType = searchType;
        this.isSavedMessages = isSavedMessages;
    }

    // LoogriGram: the public stories found for a hashtag were listed above the
    // messages (StoriesView, also used by the hashtag page and the chat list's
    // hashtag search). Stories are removed, as on desktop.
    @Override
    public void notifyDataSetChanged() {
        final int oldItemsCount = getItemCount();

        searchResultMessages.clear();
        messageIds.clear();
        ArrayList<MessageObject> searchResults = searchType == 0 ? MediaDataController.getInstance(currentAccount).getFoundMessageObjects() : HashtagSearchController.getInstance(currentAccount).getMessages(searchType);
        for (int i = 0; i < searchResults.size(); ++i) {
            MessageObject m = searchResults.get(i);
            if ((!m.hasValidGroupId() || m.isPrimaryGroupMessage) && !messageIds.contains(m.getId())) {
                searchResultMessages.add(m);
                messageIds.add(m.getId());
            }
        }

        final int oldLoadedCount = loadedCount;
        final int oldFlickerCount = flickerCount;

        loadedCount = searchResultMessages.size();
        if (searchType != 0) {
            boolean hasMore = !HashtagSearchController.getInstance(currentAccount).isEndReached(searchType);
            flickerCount = hasMore && loadedCount != 0 ? Utilities.clamp(HashtagSearchController.getInstance(currentAccount).getCount(searchType) - loadedCount, 3, 0) : 0;
        } else {
            boolean hasMore = !MediaDataController.getInstance(currentAccount).searchEndReached();
            flickerCount = hasMore && loadedCount != 0 ? Utilities.clamp(MediaDataController.getInstance(currentAccount).getSearchCount() - loadedCount, 3, 0) : 0;
        }

        final int newItemsCount = getItemCount();

        if (oldItemsCount < newItemsCount) {
            if (oldFlickerCount > 0) notifyItemRangeChanged(oldItemsCount - oldFlickerCount, oldFlickerCount);
            notifyItemRangeInserted(oldItemsCount, newItemsCount - oldItemsCount);
        } else {
            super.notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return searchResultMessages.size() + flickerCount;
    }

    public Object getItem(int i) {
        if (i < 0 || i >= searchResultMessages.size()) {
            return null;
        }
        return searchResultMessages.get(i);
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == 0;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case 0:
                view = new DialogCell(null, mContext, false, true, currentAccount, resourcesProvider);
                break;
            case 1:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext, resourcesProvider);
                flickerLoadingView.setIsSingleCell(true);
                flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
                view = flickerLoadingView;
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == 0) {
            DialogCell cell = (DialogCell) holder.itemView;
            cell.useSeparator = true;
            MessageObject messageObject = (MessageObject) getItem(position);
            int date;
            long did;
            boolean useMe = false;
            did = messageObject.getDialogId();
            date = messageObject.messageOwner.date;
            if (isSavedMessages) {
                cell.isSavedDialog = true;
                did = messageObject.getSavedDialogId();
                if (messageObject.messageOwner.fwd_from != null && (messageObject.messageOwner.fwd_from.date != 0 || messageObject.messageOwner.fwd_from.saved_date != 0)) {
                    date = messageObject.messageOwner.fwd_from.date;
                    if (date == 0) {
                        date = messageObject.messageOwner.fwd_from.saved_date;
                    }
                } else {
                    date = messageObject.messageOwner.date;
                }
            } else {
                if (messageObject.isOutOwner() || ChatObject.isMonoForum(currentAccount, did)) {
                    did = messageObject.getFromChatId();
                }
                useMe = true;
            }
            cell.setDialog(did, messageObject, date, useMe, false);
            cell.setDialogCellDelegate(new DialogCell.DialogCellDelegate() {
                @Override
                public void onButtonClicked(DialogCell dialogCell) {

                }

                @Override
                public void onButtonLongPress(DialogCell dialogCell) {

                }

                @Override
                public boolean canClickButtonInside() {
                    return false;
                }

                @Override
                public void showChatPreview(DialogCell dialogCell) {

                }

            });
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i < searchResultMessages.size()) {
            return 0;
        }
        return 1;
    }
}
