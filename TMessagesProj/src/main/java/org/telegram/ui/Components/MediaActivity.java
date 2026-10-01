package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugProvider;
import org.telegram.ui.Components.Paint.ShapeDetector;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MediaActivity extends BaseFragment implements SharedMediaLayout.SharedMediaPreloaderDelegate, FloatingDebugProvider, NotificationCenter.NotificationCenterDelegate {

    // LoogriGram: this screen had a "type": our stories and their archive (1),
    // a channel's archived stories (2) and the public stories found for a
    // hashtag (3), besides a chat's shared media (0). Stories are removed, as
    // on desktop, and shared media is all it shows.

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private TLRPC.ChatFull currentChatInfo;
    private TLRPC.UserFull currentUserInfo;
    private long dialogId;
    private long topicId;
    private FrameLayout titlesContainer;
    private FrameLayout[] titles = new FrameLayout[1];
    private SimpleTextView[] nameTextView = new SimpleTextView[1];
    private AnimatedTextView[] subtitleTextView = new AnimatedTextView[1];
    ProfileActivity.AvatarImageView avatarImageView;
    private BackDrawable backDrawable;
    private AnimatedTextView selectedTextView;

    SharedMediaLayout sharedMediaLayout;
    private int initialTab;

    public MediaActivity(Bundle args, SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader) {
        super(args);
        this.sharedMediaPreloader = sharedMediaPreloader;
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        topicId = getArguments().getLong("topic_id", 0);
        initialTab = getArguments().getInt("start_from", SharedMediaLayout.TAB_PHOTOVIDEO);
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        if (DialogObject.isUserDialog(dialogId) && topicId == 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (UserObject.isUserSelf(user)) {
                getMessagesController().loadUserInfo(user, false, this.classGuid);
                currentUserInfo = getMessagesController().getUserFull(dialogId);
            }
        }
        if (this.sharedMediaPreloader == null) {
            this.sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(this);
        }
        this.sharedMediaPreloader.addDelegate(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            long uid = (long) args[0];
            if (uid == dialogId) {
                currentUserInfo = (TLRPC.UserFull) args[1];
                if (sharedMediaLayout != null) {
                    sharedMediaLayout.setUserInfo(currentUserInfo);
                }
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));
        backDrawable.setAnimationTime(240);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (sharedMediaLayout.closeActionMode(true)) {
                        return;
                    }
                    finishFragment();
                } else if (id == 11) {
                    sharedMediaLayout.closeActionMode(true);
                    sharedMediaLayout.getSearchItem().openSearch(false);
                }
            }
        });
        FrameLayout avatarContainer = new FrameLayout(context);
        SizeNotifierFrameLayout fragmentView = new SizeNotifierFrameLayout(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                FrameLayout.LayoutParams lp = (LayoutParams) sharedMediaLayout.getLayoutParams();
                lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);

                lp = (LayoutParams) avatarContainer.getLayoutParams();
                lp.topMargin = actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
                lp.height = ActionBar.getCurrentActionBarHeight();

                int textTop;
                for (int i = 0; i < nameTextView.length; ++i) {
                    if (nameTextView[i] != null) {
                        textTop = (ActionBar.getCurrentActionBarHeight() / 2 - dp(22)) / 2 + dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 5);
                        lp = (LayoutParams) nameTextView[i].getLayoutParams();
                        lp.topMargin = textTop;
                    }

                    if (subtitleTextView[i] != null) {
                        textTop = ActionBar.getCurrentActionBarHeight() / 2 + (ActionBar.getCurrentActionBarHeight() / 2 - dp(19)) / 2 - dp(3 + 4);
                        lp = (LayoutParams) subtitleTextView[i].getLayoutParams();
                        lp.topMargin = textTop;
                    }
                }

                lp = (LayoutParams) avatarImageView.getLayoutParams();
                lp.topMargin = (ActionBar.getCurrentActionBarHeight() - dp(42)) / 2;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (sharedMediaLayout != null && sharedMediaLayout.isInFastScroll()) {
                    return sharedMediaLayout.dispatchFastScrollEvent(ev);
                }
                if (sharedMediaLayout != null && sharedMediaLayout.checkPinchToZoom(ev)) {
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
                sharedMediaLayout.drawListForBlur(blurCanvas, views);
            }
        };
        fragmentView.needBlur = true;
        this.fragmentView = fragmentView;
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        actionBar.createMenu();
        // LoogriGram: the stories screens had their own menu here (delete, zoom,
        // calendar, show photos / videos).

        titlesContainer = new FrameLayout(context);
        avatarContainer.addView(titlesContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        for (int i = 0; i < titles.length; ++i) {
            titles[i] = new FrameLayout(context);
            titlesContainer.addView(titles[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            nameTextView[i] = new SimpleTextView(context);
            nameTextView[i].setPivotX(0);
            nameTextView[i].setPivotY(dp(9));

            nameTextView[i].setTextSize(18);
            nameTextView[i].setGravity(Gravity.LEFT);
            nameTextView[i].setTypeface(AndroidUtilities.bold());
            nameTextView[i].setLeftDrawableTopPadding(-dp(1.3f));
            nameTextView[i].setScrollNonFitText(true);
            nameTextView[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            titles[i].addView(nameTextView[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 0, 56, 0));

            subtitleTextView[i] = new AnimatedTextView(context, true, true, true);
            subtitleTextView[i].setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            subtitleTextView[i].setTextSize(AndroidUtilities.dp(14));
            subtitleTextView[i].setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
            titles[i].addView(subtitleTextView[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 0, 56, 0));
        }

        avatarImageView = new ProfileActivity.AvatarImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (getImageReceiver().hasNotThumb()) {
                    info.setText(LocaleController.getString(R.string.AccDescrProfilePicture));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString(R.string.Open)));
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, LocaleController.getString(R.string.AccDescrOpenInPhotoViewer)));
                    }
                } else {
                    info.setVisibleToUser(false);
                }
            }
        };
        avatarImageView.getImageReceiver().setAllowDecodeSingleFrame(true);
        avatarImageView.setRoundRadius(dp(getDialogId() == getUserConfig().getClientUserId() && topicId == 0 && getMessagesController().savedViewAsChats ? 13 : 21));
        avatarImageView.setPivotX(0);
        avatarImageView.setPivotY(0);
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setProfile(true);

        avatarImageView.setImageDrawable(avatarDrawable);
        avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.LEFT, 64, 0, 0, 0));

        selectedTextView = new AnimatedTextView(context, true, true, true);
        selectedTextView.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        selectedTextView.setTextSize(dp(20));
        selectedTextView.setGravity(Gravity.LEFT);
        selectedTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        selectedTextView.setTypeface(AndroidUtilities.bold());
        avatarContainer.addView(selectedTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 72 + 48, -2, 72, 0));

        if (dialogId == getUserConfig().getClientUserId() && topicId == 0 && !getMessagesController().getSavedMessagesController().unsupported && getMessagesController().getSavedMessagesController().hasDialogs()) {
            initialTab = SharedMediaLayout.TAB_SAVED_DIALOGS;
        }
        sharedMediaLayout = new SharedMediaLayout(context, dialogId, sharedMediaPreloader, 0, null, currentChatInfo, currentUserInfo, initialTab, this, new SharedMediaLayout.Delegate() {
            @Override
            public void scrollToSharedMedia() {

            }

            @Override
            public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View vi) {
                return false;
            }

            @Override
            public TLRPC.Chat getCurrentChat() {
                return null;
            }

            @Override
            public boolean isFragmentOpened() {
                return true;
            }

            @Override
            public RecyclerListView getListView() {
                return null;
            }

            @Override
            public boolean canSearchMembers() {
                return false;
            }

            @Override
            public void updateSelectedMediaTabText() {
                updateMediaCount();
            }

        }, SharedMediaLayout.VIEW_TYPE_MEDIA_ACTIVITY, getResourceProvider()) {
            @Override
            protected void onSelectedTabChanged() {
                super.onSelectedTabChanged();
                updateMediaCount();
            }

            @Override
            protected void onSearchStateChanged(boolean expanded) {
                AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
                AndroidUtilities.updateViewVisibilityAnimated(avatarContainer, !expanded, 0.95f, true);
            }

            @Override
            protected void drawBackgroundWithBlur(Canvas canvas, float y, Rect rectTmp2, Paint backgroundPaint) {
                fragmentView.drawBlurRect(canvas, getY() + y, rectTmp2, backgroundPaint, true);
            }

            @Override
            protected void invalidateBlur() {
                fragmentView.invalidateBlur();
            }

            @Override
            protected boolean includeSavedDialogs() {
                return dialogId == getUserConfig().getClientUserId() && topicId == 0;
            }
        };
        sharedMediaLayout.scrollSlidingTextTabStrip.setOpen(true);
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.getSearchOptionsItem().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        }
        sharedMediaLayout.setPinnedToTop(true);
        sharedMediaLayout.getSearchItem().setTranslationY(0);
        sharedMediaLayout.photoVideoOptionsItem.setTranslationY(0);
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.getSearchOptionsItem().setTranslationY(0);
        }

        fragmentView.addView(sharedMediaLayout);
        fragmentView.addView(actionBar);
        fragmentView.addView(avatarContainer);
        fragmentView.blurBehindViews.add(sharedMediaLayout);

        long avatarDialogId = dialogId;
        if (topicId != 0 && dialogId == getUserConfig().getClientUserId()) {
            avatarDialogId = topicId;
        }
        TLObject avatarObject = null;
        if (avatarDialogId == UserObject.ANONYMOUS) {
            nameTextView[0].setText(LocaleController.getString(R.string.AnonymousForward));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
            avatarDrawable.setScaleSize(.75f);
        } else if (topicId != 0 && avatarDialogId == getUserConfig().getClientUserId()) {
            nameTextView[0].setText(LocaleController.getString(R.string.MyNotes));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_MY_NOTES);
            avatarDrawable.setScaleSize(.75f);
        } else if (DialogObject.isEncryptedDialog(avatarDialogId)) {
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(avatarDialogId));
            if (encryptedChat != null) {
                TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                if (user != null) {
                    nameTextView[0].setText(ContactsController.formatName(user.first_name, user.last_name));
                    avatarDrawable.setInfo(currentAccount, user);
                    avatarObject = user;
                }
            }
        } else if (DialogObject.isUserDialog(avatarDialogId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(avatarDialogId);
            if (user != null) {
                if (user.self) {
                    nameTextView[0].setText(LocaleController.getString(R.string.SavedMessages));
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    avatarDrawable.setScaleSize(.8f);
                } else {
                    nameTextView[0].setText(ContactsController.formatName(user.first_name, user.last_name));
                    avatarDrawable.setInfo(currentAccount, user);
                    avatarObject = user;
                }
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-avatarDialogId);
            if (chat != null) {
                nameTextView[0].setText(chat.title);
                avatarDrawable.setInfo(currentAccount, chat);
                avatarObject = chat;
            }
        }

        final ImageLocation thumbLocation = ImageLocation.getForUserOrChat(currentAccount, avatarObject, ImageLocation.TYPE_SMALL);
        avatarImageView.setImage(thumbLocation, "50_50", avatarDrawable, avatarObject);

        if (nameTextView[0] != null && TextUtils.isEmpty(nameTextView[0].getText())) {
            nameTextView[0].setText(LocaleController.getString(R.string.SharedContentTitle));
        }

        if (sharedMediaLayout.isSearchItemVisible()) {
            sharedMediaLayout.getSearchItem().setVisibility(View.VISIBLE);
        }
        if (sharedMediaLayout.searchItemIcon != null && initialTab != SharedMediaLayout.TAB_SAVED_DIALOGS) {
            sharedMediaLayout.searchItemIcon.setVisibility(View.GONE);
        }
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.animateSearchToOptions(!sharedMediaLayout.isSearchItemVisible(), false);
            sharedMediaLayout.getSearchOptionsItem().setVisibility(View.VISIBLE);
        }
        if (sharedMediaLayout.isCalendarItemVisible()) {
            sharedMediaLayout.photoVideoOptionsItem.setVisibility(View.VISIBLE);
        } else {
            sharedMediaLayout.photoVideoOptionsItem.setVisibility(View.INVISIBLE);
        }

        actionBar.setDrawBlurBackground(fragmentView);
        AndroidUtilities.updateViewVisibilityAnimated(avatarContainer, true, 1, false);
        updateMediaCount();
        updateColors();
        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasShownSheet()) {
            if (invoked) closeSheet();
            return false;
        }
        if (sharedMediaLayout.isActionModeShown()) {
            if (invoked) sharedMediaLayout.closeActionMode(false);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (!sharedMediaLayout.isSwipeBackEnabled()) {
            return false;
        }
        return sharedMediaLayout.isCurrentTabFirst();
    }

    @Override
    public boolean canBeginSlide() {
        if (!sharedMediaLayout.isSwipeBackEnabled()) {
            return false;
        }
        return super.canBeginSlide();
    }

    private void updateMediaCount() {
        if (sharedMediaLayout == null || subtitleTextView[0] == null) {
            return;
        }
        int id = sharedMediaLayout.getClosestTab();
        int[] mediaCount = sharedMediaPreloader.getLastMediaCount();
        final boolean animated = !LocaleController.isRTL;
        final int i = 0;
        if (id == SharedMediaLayout.TAB_SAVED_DIALOGS) {
            showSubtitle(i, true, true);
            int count = getMessagesController().getSavedMessagesController().getAllCount();
            subtitleTextView[i].setText(LocaleController.formatPluralString("SavedDialogsTabCount", count), animated);
            return;
        }
        if (id < 0 || id < mediaCount.length && mediaCount[id] < 0) {
            return;
        }
        if (id == SharedMediaLayout.TAB_PHOTOVIDEO) {
            showSubtitle(i, true, true);
            if (sharedMediaLayout.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
                subtitleTextView[i].setText(LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]), animated);
            } else if (sharedMediaLayout.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
                subtitleTextView[i].setText(LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]), animated);
            } else {
                subtitleTextView[i].setText(LocaleController.formatPluralString("Media", mediaCount[MediaDataController.MEDIA_PHOTOVIDEO]), animated);
            }
        } else if (id == SharedMediaLayout.TAB_FILES) {
            showSubtitle(i, true, true);
            subtitleTextView[i].setText(LocaleController.formatPluralString("Files", mediaCount[MediaDataController.MEDIA_FILE]), animated);
        } else if (id == SharedMediaLayout.TAB_VOICE) {
            showSubtitle(i, true, true);
            subtitleTextView[i].setText(LocaleController.formatPluralString("Voice", mediaCount[MediaDataController.MEDIA_AUDIO]), animated);
        } else if (id == SharedMediaLayout.TAB_LINKS) {
            showSubtitle(i, true, true);
            subtitleTextView[i].setText(LocaleController.formatPluralString("Links", mediaCount[MediaDataController.MEDIA_URL]), animated);
        } else if (id == SharedMediaLayout.TAB_AUDIO) {
            showSubtitle(i, true, true);
            subtitleTextView[i].setText(LocaleController.formatPluralString("MusicFiles", mediaCount[MediaDataController.MEDIA_MUSIC]), animated);
        } else if (id == SharedMediaLayout.TAB_GIF) {
            showSubtitle(i, true, true);
            subtitleTextView[i].setText(LocaleController.formatPluralString("GIFs", mediaCount[MediaDataController.MEDIA_GIF]), animated);
        } else if (id == SharedMediaLayout.TAB_RECOMMENDED_CHANNELS) {
            showSubtitle(i, true, true);
            MessagesController.ChannelRecommendations rec = MessagesController.getInstance(currentAccount).getChannelRecommendations(dialogId);
            subtitleTextView[i].setText(LocaleController.formatPluralString("Channels", rec == null ? 0 : rec.chats.size()), animated);
        }
    }

    public void setChatInfo(TLRPC.ChatFull currentChatInfo) {
        this.currentChatInfo = currentChatInfo;
    }

    public long getDialogId() {
        return dialogId;
    }

    private final boolean[] subtitleShown = new boolean[1];
    private final float[] subtitleT = new float[1];
    private final boolean[] firstSubtitleCheck = new boolean[] { true };
    private final ValueAnimator[] subtitleAnimator = new ValueAnimator[1];
    private void showSubtitle(int i, boolean show, boolean animated) {
        if (subtitleShown[i] == show && !firstSubtitleCheck[i]) {
            return;
        }
        animated = !firstSubtitleCheck[i] && animated;
        firstSubtitleCheck[i] = false;
        subtitleShown[i] = show;
        if (subtitleAnimator[i] != null) {
            subtitleAnimator[i].cancel();
            subtitleAnimator[i] = null;
        }
        if (animated) {
            subtitleTextView[i].setVisibility(View.VISIBLE);
            subtitleAnimator[i] = ValueAnimator.ofFloat(subtitleT[i], show ? 1f : 0f);
            subtitleAnimator[i].addUpdateListener(anm -> {
                subtitleT[i] = (float) anm.getAnimatedValue();
                nameTextView[i].setScaleX(lerp(1.111f, 1f, subtitleT[i]));
                nameTextView[i].setScaleY(lerp(1.111f, 1f, subtitleT[i]));
                nameTextView[i].setTranslationY(lerp(dp(8), 0, subtitleT[i]));
                subtitleTextView[i].setAlpha(subtitleT[i]);
            });
            subtitleAnimator[i].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    subtitleT[i] = show ? 1f : 0f;
                    nameTextView[i].setScaleX(show ? 1f : 1.111f);
                    nameTextView[i].setScaleY(show ? 1f : 1.111f);
                    nameTextView[i].setTranslationY(show ? 0 : dp(8));
                    subtitleTextView[i].setAlpha(show ? 1f : 0f);

                    if (!show) {
                        subtitleTextView[i].setVisibility(View.GONE);
                    }
                }
            });
            subtitleAnimator[i].setDuration(320);
            subtitleAnimator[i].setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            subtitleAnimator[i].start();
        } else {
            subtitleT[i] = show ? 1f : 0f;
            nameTextView[i].setScaleX(show ? 1f : 1.111f);
            nameTextView[i].setScaleY(show ? 1f : 1.111f);
            nameTextView[i].setTranslationY(show ? 0 : dp(8));
            subtitleTextView[i].setAlpha(show ? 1f : 0f);
            subtitleTextView[i].setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void mediaCountUpdated() {
        if (sharedMediaLayout != null && sharedMediaPreloader != null) {
            sharedMediaLayout.setNewMediaCounts(sharedMediaPreloader.getLastMediaCount());
        }
        updateMediaCount();
    }


    private void updateColors() {
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.getSearchOptionsItem().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        }
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (nameTextView[0] != null) {
            nameTextView[0].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate themeDelegate = this::updateColors;
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_actionBarActionModeDefaultSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.addAll(sharedMediaLayout.getThemeDescriptions());
        return arrayList;
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (actionBar.isActionModeShowed()) {
            color = Theme.getColor(Theme.key_actionBarActionModeDefault);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    @Override
    public List<FloatingDebugController.DebugItem> onGetDebugItems() {
        return Arrays.asList(
            new FloatingDebugController.DebugItem(
                (ShapeDetector.isLearning(getContext()) ? "Disable" : "Enable") + " shape detector learning debug",
                () -> {
                    ShapeDetector.setLearning(getContext(), !ShapeDetector.isLearning(getContext()));
                }
            )
        );
    }

    @Override
    public int getNavigationBarColor() {
        return getThemedColor(Theme.key_windowBackgroundWhite);
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (sharedMediaLayout != null) {
            sharedMediaLayout.setPagesPaddingBottom(bottom);
        }
    }
}
