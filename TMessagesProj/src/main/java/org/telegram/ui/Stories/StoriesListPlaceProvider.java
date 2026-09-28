package org.telegram.ui.Stories;


import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ReactedUserHolderView;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.RecyclerListView;

public class StoriesListPlaceProvider implements StoryViewer.PlaceProvider {

    private final RecyclerListView recyclerListView;
    int[] clipPoint = new int[2];
    private boolean isHiddenArchive;
    LoadNextInterface loadNextInterface;
    public boolean hiddedStories;
    public boolean onlyUnreadStories;
    public boolean onlySelfStories;
    public boolean hasPaginationParams;
    public int addBottomClip;

    public StoriesListPlaceProvider addBottomClip(int x) {
        addBottomClip += x;
        return this;
    }


    public static StoriesListPlaceProvider of(RecyclerListView recyclerListView) {
        return of(recyclerListView, false);
    }

    public static StoriesListPlaceProvider of(RecyclerListView recyclerListView, boolean hiddenArchive) {
        return new StoriesListPlaceProvider(recyclerListView, hiddenArchive);
    }

    public StoriesListPlaceProvider with(LoadNextInterface loadNextInterface) {
        this.loadNextInterface = loadNextInterface;
        return this;
    }

    public StoriesListPlaceProvider(RecyclerListView recyclerListView, boolean hiddenArchive) {
        this.recyclerListView = recyclerListView;
        this.isHiddenArchive = hiddenArchive;
    }

    @Override
    public void preLayout(long currentDialogId, int messageId, Runnable r) {
        if (recyclerListView != null && recyclerListView.getParent() instanceof SelfStoryViewsPage) {
            SelfStoryViewsPage page = (SelfStoryViewsPage) recyclerListView.getParent();
            if (page.scrollToRepostCell(currentDialogId, messageId)) {
                recyclerListView.post(r);
            } else {
                r.run();
            }
        } else {
            if (isHiddenArchive) {
                MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController().sortHiddenStories();
            }
            r.run();
        }
    }

    public boolean findView(long dialogId, int messageId, int storyId, int type, StoryViewer.TransitionViewHolder holder) {
        holder.view = null;
        holder.avatarImage = null;
        holder.storyImage = null;
        holder.drawAbove = null;

        ViewGroup listView = recyclerListView;
        if (listView == null) return false;

        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);

            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                if (cell.getMessageObject().getId() == messageId) {
                    holder.view = child;
                    if (type == 1 || type == 2) {
                        holder.storyImage = cell.getPhotoImage();
                    } else {
                        holder.storyImage = cell.replyImageReceiver;
                    }
                    holder.clipParent = (View) cell.getParent();
                    holder.alpha = 1;
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) child;
                if (cell.getMessageObject().getId() == messageId) {
                    holder.view = child;
                    TL_stories.StoryItem storyItem = cell.getMessageObject().messageOwner.media.storyItem;
                    if (storyItem.noforwards) {
                        holder.avatarImage = cell.getPhotoImage();
                    } else {
                        holder.storyImage = cell.getPhotoImage();
                    }
                    holder.clipParent = (View) cell.getParent();
                    holder.alpha = 1;
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof ReactedUserHolderView) {
                ReactedUserHolderView cell = (ReactedUserHolderView) child;
                if (cell.dialogId == dialogId) {
                    final boolean hasStoryPreview = cell.storyPreviewView != null && cell.storyPreviewView.getImageReceiver() != null && cell.storyPreviewView.getImageReceiver().getImageDrawable() != null;
                    if (cell.storyId == storyId && hasStoryPreview) {
                        holder.view = cell.storyPreviewView;
                        holder.storyImage = cell.storyPreviewView.getImageReceiver();
                        holder.clipParent = (View) cell.getParent();
                        holder.alpha = cell.getAlpha() * cell.getAlphaInternal();
                        if (holder.alpha < 1) {
                            holder.bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            holder.bgPaint.setColor(Theme.getColor(Theme.key_dialogBackground, cell.getResourcesProvider()));
                        }
                        updateClip(holder);
                        return true;
                    }
                }
            }
            // LoogriGram: a chat row's, a member's or a search result's avatar
            // was a transition source here too; avatars no longer open stories.
            // LoogriGram: a statistics row (a recent story, a repost as a story)
            // was a transition source here. Statistics list no stories any more.
        }
        return false;
    }

    private void updateClip(StoryViewer.TransitionViewHolder holder) {
        if (holder.clipParent == null) {
            return;
        }
        if (holder.clipParent instanceof ClippedView) {
            ((ClippedView) holder.clipParent).updateClip(clipPoint);
            holder.clipTop = clipPoint[0];
            holder.clipBottom = clipPoint[1] - addBottomClip;
        } else if (holder.clipParent instanceof BlurredRecyclerView) {
            holder.clipTop = ((BlurredRecyclerView) holder.clipParent).blurTopPadding;
            holder.clipBottom = holder.clipParent.getMeasuredHeight() - holder.clipParent.getPaddingBottom() - addBottomClip;
        } else {
            holder.clipTop = holder.clipParent.getPaddingTop();
            holder.clipBottom = holder.clipParent.getMeasuredHeight() - holder.clipParent.getPaddingBottom() - addBottomClip;
        }
    }

    @Override
    public void loadNext(boolean forward) {
        if (loadNextInterface != null) {
            loadNextInterface.loadNext(forward);
        }
    }

    public StoryViewer.PlaceProvider setPaginationParaments(boolean hiddedStories, boolean onlyUnreadStories, boolean onlySelfStories) {
        this.hiddedStories = hiddedStories;
        this.onlyUnreadStories = onlyUnreadStories;
        this.onlySelfStories = onlySelfStories;
        hasPaginationParams = true;
        return this;
    }

    public interface ClippedView {
        void updateClip(int[] clip);
    }

    public interface AvatarOverlaysView {
        boolean drawAvatarOverlays(Canvas canvas);
    }

    public interface LoadNextInterface {
        void loadNext(boolean forward);
    }
}
