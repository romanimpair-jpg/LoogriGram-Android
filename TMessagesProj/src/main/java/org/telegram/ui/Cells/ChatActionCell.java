/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Gifts.GiftsController.findAttribute;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.LineHeightSpan;
import android.text.style.URLSpan;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotInlineKeyboard;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelAdminLogActivity;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.community.CommunitySheet;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CommunityAvatarDrawable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TopicSeparator;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Gifts.GiftViews;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stories.StoriesUtilities;
import org.telegram.ui.Stories.UploadingDotsSpannable;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.PreviewView;
import org.telegram.ui.community.CommunityUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.BitwiseUtils;

public class ChatActionCell extends BaseCell implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate, IMessageCell {
    private int backgroundRectHeight;
    private int backgroundButtonTop;
    private final ButtonBounce bounce = new ButtonBounce(this);
    private LoadingDrawable loadingDrawable;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.startSpoilers) {
            setSpoilersSuppressed(false);
        } else if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        } else if (id == NotificationCenter.stopSpoilers) {
            setSpoilersSuppressed(true);
        }
    }

    public void setSpoilersSuppressed(boolean s) {
        for (SpoilerEffect eff : spoilers) {
            eff.setSuppressUpdates(s);
        }
    }

    private boolean canDrawInParent;
    private View invalidateWithParent;

    public void setInvalidateWithParent(View viewToInvalidate) {
        invalidateWithParent = viewToInvalidate;
    }

    public boolean hasButton() {
        return currentMessageObject != null && isButtonLayout(currentMessageObject) && giftPremiumButtonLayout != null;
    }

    public interface ChatActionCellDelegate {
        default void didClickImage(ChatActionCell cell) {
        }

        default void didClickButton(ChatActionCell cell) {
        }

        default boolean didLongPress(ChatActionCell cell, float x, float y) {
            return false;
        }

        default void needOpenUserProfile(long uid) {
        }

        default void didPressBotButton(MessageObject messageObject, TL_keyboard.KeyboardButtonProto button) {
        }

        default void didPressReplyMessage(ChatActionCell cell, int id) {
        }

        default void didPressTaskLink(ChatActionCell cell, int id, int taskId) {
        }

        default void didPressReaction(ChatActionCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
        }

        default void needOpenInviteLink(TLRPC.TL_chatInviteExported invite) {
        }

        default void onTopicClick(ChatActionCell cell) {

        }

        default BaseFragment getBaseFragment() {
            return null;
        }

        default long getDialogId() {
            return 0;
        }

        default long getTopicId() {
            return 0;
        }

        default boolean canDrawOutboundsContent() {
            return true;
        }
    }

    public interface ThemeDelegate extends Theme.ResourcesProvider {

        int getCurrentColor();
    }

    private int TAG;

    private URLSpan pressedLink;
    private SpoilerEffect spoilerPressed;
    private boolean actionPressed;
    private boolean isSpoilerRevealing;
    private int currentAccount = UserConfig.selectedAccount;
    private ImageReceiver imageReceiver;
    private Drawable wallpaperPreviewDrawable;
    private Path clipPath;
    private AvatarDrawable avatarDrawable;
    private StaticLayout textLayout;
    private int textWidth;
    private int textHeight;
    private int textX;
    private int textY;
    private int textXLeft;
    private int previousWidth;
    private boolean imagePressed;
    private boolean giftButtonPressed;
    RadialProgressView progressView;
    float progressToProgress;
    StoriesUtilities.AvatarStoryParams avatarStoryParams = new StoriesUtilities.AvatarStoryParams(false);
    public boolean isAllChats;
    public boolean isForum;
    public boolean isMonoForum;
    public boolean isBotForum;
    public boolean isSideMenued;
    public boolean isSideMenuEnabled;
    public float sideMenuAlpha;
    public int sideMenuWidth;
    public boolean firstInChat;
    private int topicSeparatorTopPadding;
    public boolean showTopicSeparator = true;
    public TopicSeparator topicSeparator;

    public void setShowTopic(boolean show) {
        if (showTopicSeparator != show) {
            showTopicSeparator = show;
            invalidateOutbounds();
            invalidate();
        }
    }

    private RectF giftButtonRect = new RectF();

    public List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiStack;

    TextPaint textPaint;

    private float viewTop;
    private float viewTranslationX;
    private int backgroundHeight;
    private boolean visiblePartSet;

    private ImageLocation currentVideoLocation;

    private float lastTouchX;
    private float lastTouchY;

    private boolean wasLayout;

    private boolean hasReplyMessage;

    public final ReactionsLayoutInBubble reactionsLayoutInBubble = new ReactionsLayoutInBubble(this);

    private MessageObject currentMessageObject;
    private int customDate;
    private CharSequence customText;
    private GiftViews.CardBackground cardBackground;

    private int overrideBackground = -1;
    private int overrideText = -1;
    private Paint overrideBackgroundPaint;
    private TextPaint overrideTextPaint;
    private int overrideColor;
    private ArrayList<Integer> lineWidths = new ArrayList<>();
    private ArrayList<Integer> lineHeights = new ArrayList<>();
    private Path backgroundPath = new Path();
    private int backgroundLeft, backgroundRight;
    private RectF rect = new RectF();
    private boolean invalidatePath = true;
    private boolean invalidateColors = false;

    private ChatActionCellDelegate delegate;
    private Theme.ResourcesProvider themeDelegate;

    private int stickerSize;
    private int giftRectSize;
    private TextLayout giftPremiumText;
    
    class TextLayout {
        public float x, y;
        public int width;
        public StaticLayout layout;
        public TextPaint paint;
        public List<SpoilerEffect> spoilers = new ArrayList<>();
        public final AtomicReference<Layout> patchedLayout = new AtomicReference<>();
        public AnimatedEmojiSpan.EmojiGroupedSpans emoji;

        public void setText(CharSequence text, TextPaint textPaint, int width) {
            this.paint = textPaint;
            this.width = width;
            layout = new StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_CENTER, 1.1f, 0.0f, false);
            if (currentMessageObject == null || !currentMessageObject.isSpoilersRevealed) {
                SpoilerEffect.addSpoilers(ChatActionCell.this, layout, -1, width, null, spoilers);
            } else if (spoilers != null) {
                spoilers.clear();
            }
            attach();
        }

        public void attach() {
            emoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, ChatActionCell.this, false, emoji, layout);
        }

        public void detach() {
            AnimatedEmojiSpan.release(ChatActionCell.this, emoji);
        }
    }
    
    private StaticLayout giftPremiumButtonLayout;
    private boolean buttonClickableAsImage = true;
    TextPaint settingWallpaperPaint;
    private StaticLayout settingWallpaperLayout;
    private float settingWallpaperProgress;
    private StaticLayout settingWallpaperProgressTextLayout;
    private float giftPremiumButtonWidth;

    private TextPaint giftTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private RadialProgress2 radialProgress = new RadialProgress2(this);
    private RectF backgroundRect;

    private View rippleView;

    private ArrayList<BotButton> botButtons = new ArrayList<>();
    private BotInlineKeyboard.Source botInlineButtons;

    public ChatActionCell(Context context) {
        this(context, false, null);
    }

    public ChatActionCell(Context context, boolean canDrawInParent, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        avatarStoryParams.drawSegments = false;
        this.canDrawInParent = canDrawInParent;
        this.themeDelegate = resourcesProvider;
        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
        avatarDrawable = new AvatarDrawable();
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        giftTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15, getResources().getDisplayMetrics()));

        rippleView = new View(context);
        rippleView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Color.BLACK, .1f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(16)));
        rippleView.setVisibility(GONE);
        addView(rippleView);
    }

    public void setDelegate(ChatActionCellDelegate delegate) {
        this.delegate = delegate;
    }

    public ChatActionCellDelegate getDelegate() {
        return delegate;
    }

    public void setCustomDate(int date, boolean scheduled, boolean inLayout) {
        if (customDate == date || customDate / 3600 == date / 3600) {
            return;
        }
        CharSequence newText;
        if (scheduled) {
            if (date == 0x7ffffffe) {
                newText = getString("MessageScheduledUntilOnline", R.string.MessageScheduledUntilOnline);
            } else {
                newText = formatString("MessageScheduledOn", R.string.MessageScheduledOn, LocaleController.formatDateChat(date));
            }
        } else {
            newText = LocaleController.formatDateChat(date);
        }
        customDate = date;
        if (customText != null && TextUtils.equals(newText, customText)) {
            return;
        }
        customText = newText;
        accessibilityText = null;
        updateTextInternal(inLayout);
    }

    private void updateTextInternal(boolean inLayout) {
        if (getMeasuredWidth() != 0) {
            createLayout(customText, getMeasuredWidth());
            invalidate();
        }
        if (!wasLayout) {
            if (inLayout) {
                AndroidUtilities.runOnUIThread(this::requestLayout);
            } else {
                requestLayout();
            }
        } else {
            buildLayout();
        }
    }

    public void setCustomText(CharSequence text) {
        customText = text;
        if (customText != null) {
            updateTextInternal(false);
        }
    }

    public void setOverrideColor(int background, int text) {
        overrideBackground = background;
        overrideText = text;
    }

    public void setMessageObject(MessageObject messageObject) {
        setMessageObject(messageObject, false);
    }

    private boolean offerExpired;
    
    public void setMessageObject(MessageObject messageObject, boolean force) {
        if (messageObject == null) return;
        if (currentMessageObject == messageObject && (textLayout == null || TextUtils.equals(textLayout.getText(), messageObject.messageText)) && (hasReplyMessage || messageObject.replyMessageObject == null) && !force && messageObject.type != MessageObject.TYPE_SUGGEST_PHOTO && !messageObject.forceUpdate) {
            return;
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION && Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
            FileLog.e(new IllegalStateException("Wrong thread!!!"));
        }

        botButtons.clear();
        botInlineButtons = null;
        /*
        botButtonsByData.clear();
        botButtonsByPosition.clear();
        botButtonsLayout = null;
        */
        accessibilityText = null;
        boolean messageIdChanged = currentMessageObject == null || currentMessageObject.stableId != messageObject.stableId;
        currentMessageObject = messageObject;
        messageObject.forceUpdate = false;
        hasReplyMessage = messageObject.replyMessageObject != null;
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        previousWidth = 0;
        isSpoilerRevealing = false;
        if (giftPremiumText != null && messageIdChanged) {
            giftPremiumText.detach();
            giftPremiumText = null;
        }
        if (messageIdChanged || messageObject.reactionsChanged) {
            messageObject.reactionsChanged = false;
            if (messageObject.shouldDrawReactions()) {
                final boolean isSmall = !messageObject.shouldDrawReactionsInLayout();
                reactionsLayoutInBubble.setMessage(messageObject, isSmall, themeDelegate);
            } else {
                reactionsLayoutInBubble.setMessage(null, false, themeDelegate);
            }
        }
        imageReceiver.setAutoRepeatCount(0);
        imageReceiver.clearDecorators();
        if (messageObject.type != MessageObject.TYPE_ACTION_WALLPAPER) {
            wallpaperPreviewDrawable = null;
        }
        if (messageObject.actionDeleteGroupEventId != -1) {
            ScaleStateListAnimator.apply(this, .02f, 1.2f);
            overriddenMaxWidth = Math.max(dp(250), HintView2.cutInFancyHalf(messageObject.messageText, (TextPaint) getThemedPaint(Theme.key_paint_chatActionText)));
            ProfileActivity.ShowDrawable showDrawable = ChannelAdminLogActivity.findDrawable(messageObject.messageText);
            if (showDrawable != null) {
                showDrawable.setView(this);
            }
        } else {
            ScaleStateListAnimator.reset(this);
            overriddenMaxWidth = 0;
        }
        if (messageObject.isStoryMention()) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.media.user_id);
            avatarDrawable.setInfo(currentAccount, user);
            TL_stories.StoryItem storyItem = messageObject.messageOwner.media.storyItem;
            if (storyItem != null && storyItem.noforwards) {
                imageReceiver.setForUserOrChat(user, avatarDrawable, null, true, 0, true);
            } else {
                StoriesUtilities.setImage(imageReceiver, storyItem);
            }
            imageReceiver.setRoundRadius((int) (stickerSize / 2f));
        } else if (messageObject.type == MessageObject.TYPE_ACTION_WALLPAPER) {
            TLRPC.PhotoSize strippedPhotoSize = null;
            if (messageObject.strippedThumb == null) {
                for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                    TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                    if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                        strippedPhotoSize = photoSize;
                        break;
                    }
                }
            }
            TLRPC.WallPaper wallPaper = null;
            if (messageObject.currentEvent != null && messageObject.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionChangeWallpaper) {
                wallPaper = ((TLRPC.TL_channelAdminLogEventActionChangeWallpaper) messageObject.currentEvent.action).new_value;
            } else if (messageObject.messageOwner != null && messageObject.messageOwner.action != null) {
                TLRPC.MessageAction action = messageObject.messageOwner.action;
                wallPaper = action.wallpaper;
            }
            if (!TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(wallPaper))) {
                final boolean isDark = themeDelegate != null ? themeDelegate.isDark() : Theme.isCurrentThemeDark();
                imageReceiver.clearImage();
                wallpaperPreviewDrawable = PreviewView.getBackgroundDrawableFromTheme(currentAccount, ChatThemeController.getWallpaperEmoticon(wallPaper), isDark, false);
                if (wallpaperPreviewDrawable != null) {
                    wallpaperPreviewDrawable.setCallback(this);
                }
            } else if (wallPaper != null && wallPaper.uploadingImage != null) {
                imageReceiver.setImage(ImageLocation.getForPath(wallPaper.uploadingImage), "150_150_wallpaper" + wallPaper.id + ChatBackgroundDrawable.hash(wallPaper.settings), null, null, ChatBackgroundDrawable.createThumb(wallPaper), 0, null, wallPaper, 1);
                wallpaperPreviewDrawable = null;
            } else if (wallPaper != null) {
                TLRPC.Document document = null;
                if (messageObject.photoThumbsObject instanceof TLRPC.Document) {
                    document = (TLRPC.Document) messageObject.photoThumbsObject;
                } else if (wallPaper != null) {
                    document = wallPaper.document;
                }
                imageReceiver.setImage(ImageLocation.getForDocument(document), "150_150_wallpaper" + wallPaper.id + ChatBackgroundDrawable.hash(wallPaper.settings), null, null, ChatBackgroundDrawable.createThumb(wallPaper), 0, null, wallPaper, 1);
                wallpaperPreviewDrawable = null;
            } else {
                wallpaperPreviewDrawable = null;
            }
            imageReceiver.setRoundRadius((int) (stickerSize / 2f));

            float uploadingInfoProgress = getUploadingInfoProgress(messageObject);
            if (uploadingInfoProgress == 1f) {
                radialProgress.setProgress(1f, !messageIdChanged);
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, !messageIdChanged, !messageIdChanged);
            } else {
                radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, !messageIdChanged, !messageIdChanged);
            }
        } else if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
            imageReceiver.setRoundRadius((int) (stickerSize / 2f));
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            TLRPC.TL_messageActionSuggestProfilePhoto action = (TLRPC.TL_messageActionSuggestProfilePhoto) messageObject.messageOwner.action;

            TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(action.photo.video_sizes, 1000);
            ImageLocation videoLocation;
            if (action.photo.video_sizes != null && !action.photo.video_sizes.isEmpty()) {
                videoLocation = ImageLocation.getForPhoto(videoSize, action.photo);
            } else {
                videoLocation = null;
            }
            TLRPC.Photo photo = messageObject.messageOwner.action.photo;
            TLRPC.PhotoSize strippedPhotoSize = null;
            if (messageObject.strippedThumb == null) {
                for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                    TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                    if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                        strippedPhotoSize = photoSize;
                        break;
                    }
                }
            }
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 1000);
            if (photoSize != null) {
                if (videoSize != null) {
                    imageReceiver.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForPhoto(photoSize, photo), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 0);
                } else {
                    imageReceiver.setImage(ImageLocation.getForPhoto(photoSize, photo), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 0);
                }
            }

            imageReceiver.setAllowStartLottieAnimation(false);
            ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
            if (imageUpdater == null || imageUpdater.getCurrentImageProgress() == 1f) {
                radialProgress.setProgress(1f, !messageIdChanged);
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, !messageIdChanged, !messageIdChanged);
            } else {
                radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, !messageIdChanged, !messageIdChanged);
            }
        } else if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE || messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
            // LoogriGram: this also set up the sticker of every gift message -
            // Premium, Stars, TON and Star gifts, gift codes, prizes and offers
            // to buy a gift - with the gift packs it was looked up in, and the
            // animation and fireworks a gift played once. Those messages are
            // held unshown (LoogriGramHidden); a collectible's chat theme and a
            // sharing offer are what still come here.
            imageReceiver.setRoundRadius(0);

            TLRPC.Document document = null;

            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionNoForwardsRequest) {
                final TLRPC.TL_messageActionNoForwardsRequest action = (TLRPC.TL_messageActionNoForwardsRequest) messageObject.messageOwner.action;
                final long requestTtl = MessagesController.getInstance(currentAccount).config.noForwardsRequestExpirePeriod.get(TimeUnit.SECONDS);
                offerExpired = action.expired || messageObject.messageOwner.date + requestTtl < ConnectionsManager.getInstance(currentAccount).getCurrentTime();

                if (!messageObject.isOut() && !action.expired && (!offerExpired)) {
                    final BotInlineKeyboard.Builder b = new BotInlineKeyboard.Builder();
                    b.addSharingOfferKeyboard();
                    botInlineButtons = b.build();
                }
            } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) {
                final TLRPC.TL_messageActionSetChatTheme action = (TLRPC.TL_messageActionSetChatTheme) messageObject.messageOwner.action;
                final TLRPC.TL_chatThemeUniqueGift chatThemeUniqueGift = (TLRPC.TL_chatThemeUniqueGift) action.theme;
                final TL_stars.StarGift gift = chatThemeUniqueGift.gift;
                if (gift != null) {
                    document = TlUtils.getGiftDocument(gift);
                    if (cardBackground == null) {
                        cardBackground = new GiftViews.CardBackground(this, themeDelegate, false);
                    }
                    cardBackground.setBackdrop(findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
                    cardBackground.setPattern(findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class));
                }
            }

            if (botInlineButtons != null) {
                for (int row = 0, rows = botInlineButtons.getRowsCount(); row < rows; row++) {
                    for (int column = 0, columns = botInlineButtons.getColumnsCount(row); column < columns; column++) {
                        BotInlineKeyboard.Button inlineButton = botInlineButtons.getButton(row, column);
                        BotButton botButton = new BotButton(this::invalidateOutbounds);
                        botButton.buttonCustom = (BotInlineKeyboard.ButtonCustom) inlineButton;

                        final int iconRes = inlineButton.getIconRes();
                        if (iconRes != 0) {
                            botButton.iconDrawable = getResources().getDrawable(iconRes);
                            botButton.iconDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                        }

                        botButton.height = dp(40);
                        botButton.positionFlags |= MessageObject.POSITION_FLAG_BOTTOM;
                        botButton.positionFlags = BitwiseUtils.setFlag(botButton.positionFlags, MessageObject.POSITION_FLAG_LEFT, column == 0);
                        botButton.positionFlags = BitwiseUtils.setFlag(botButton.positionFlags, MessageObject.POSITION_FLAG_RIGHT, column == 1);

                        TextPaint botButtonPaint = (TextPaint) getThemedPaint(Theme.key_paint_chatBotButton);
                        botButton.title = new Text(inlineButton.getText(), botButtonPaint);
                        botButtons.add(botButton);
                    }
                }
            }

            if (document != null) {
                imageReceiver.setAllowStartLottieAnimation(true);
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);
                imageReceiver.setAutoRepeat(0);
                imageReceiver.setImage(ImageLocation.getForDocument(document), String.format(Locale.US, "%d_%d_nr_messageId=%d", 160, 160, messageObject.stableId), svgThumb, "tgs", null, 1);
            }
        } else if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
            final TLRPC.TL_messageActionChangeCommunity action = (TLRPC.TL_messageActionChangeCommunity) messageObject.messageOwner.action;
            final TLRPC.Chat community = MessagesController.getInstance(currentAccount).getChat(action.community_id);

            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            imageReceiver.setRoundRadius(dp(14));
            imageReceiver.setAutoRepeatCount(1);

            avatarDrawable.setInfo(community);
            imageReceiver.setForUserOrChat(community, new CommunityAvatarDrawable(getContext(), dp(14)), community);
        } else if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            imageReceiver.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
            imageReceiver.setAutoRepeatCount(1);
            long id = messageObject.getDialogId();
            avatarDrawable.setInfo(id, null, null);
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                imageReceiver.setImage(null, null, avatarDrawable, null, messageObject, 0);
            } else {
                TLRPC.PhotoSize strippedPhotoSize = null;
                if (messageObject.strippedThumb == null) {
                    for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                        TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                        if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                            strippedPhotoSize = photoSize;
                            break;
                        }
                    }
                }
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 640);
                if (photoSize != null) {
                    TLRPC.Photo photo = messageObject.messageOwner.action.photo;
                    TLRPC.VideoSize videoSize = null;
                    if (!photo.video_sizes.isEmpty() && SharedConfig.isAutoplayGifs()) {
                        videoSize = FileLoader.getClosestVideoSizeWithSize(photo.video_sizes, 1000);
                        if (!messageObject.mediaExists && !DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, videoSize.size)) {
                            currentVideoLocation = ImageLocation.getForPhoto(videoSize, photo);
                            String fileName = FileLoader.getAttachFileName(videoSize);
                            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, messageObject, this);
                            videoSize = null;
                        }
                    }
                    if (videoSize != null) {
                        imageReceiver.setImage(ImageLocation.getForPhoto(videoSize, photo), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 1);
                    } else {
                        imageReceiver.setImage(ImageLocation.getForObject(photoSize, messageObject.photoThumbsObject), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 1);
                    }
                } else {
                    imageReceiver.setImageBitmap(avatarDrawable);
                }
            }
            imageReceiver.setVisible(!PhotoViewer.isShowingImage(messageObject), false);
        } else {
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            imageReceiver.setImageBitmap((Bitmap) null);
        }
        if (firstInChat && isAllChats && isSideMenued && (isForum || isMonoForum || isBotForum)) {
            topicSeparatorTopPadding = dp(33);
            if (topicSeparator == null) {
                topicSeparator = new TopicSeparator(currentAccount, this, themeDelegate, true);
                topicSeparator.setOnClickListener(() -> {
                    if (delegate != null) {
                        delegate.onTopicClick(this);
                    }
                });
            }
            if (!topicSeparator.update(currentMessageObject)) {
                topicSeparator.detach();
                topicSeparator = null;
                topicSeparatorTopPadding = 0;
            } else if (attachedToWindow) {
                topicSeparator.attach();
            }
        } else {
            if (topicSeparator != null) {
                topicSeparator.detach();
                topicSeparator = null;
            }
            topicSeparatorTopPadding = 0;
        }
        if (getPaddingTop() != topicSeparatorTopPadding) {
            setPadding(0, topicSeparatorTopPadding, 0, 0);
        }
        rippleView.setVisibility(isButtonLayout(messageObject) ? VISIBLE : GONE);
        ForumUtilities.applyTopicToMessage(messageObject);
        requestLayout();
    }

    private float getUploadingInfoProgress(MessageObject messageObject) {
        try {
            if (messageObject != null && messageObject.type == MessageObject.TYPE_ACTION_WALLPAPER) {
                MessagesController messagesController = MessagesController.getInstance(currentAccount);
                if (messagesController.uploadingWallpaper != null && TextUtils.equals(messageObject.messageOwner.action.wallpaper.uploadingImage, messagesController.uploadingWallpaper)) {
                    return messagesController.uploadingWallpaperInfo.uploadingProgress;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 1;
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    @Override
    public ReactionsLayoutInBubble getReactionsLayout() {
        return reactionsLayoutInBubble;
    }

    @Override
    public void didPressReactionFromLayout(TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
        if (delegate != null) {
            delegate.didPressReaction(this, reaction, longpress, x, y);
        }
    }

    public ImageReceiver getPhotoImage() {
        return imageReceiver;
    }

    public void setVisiblePart(float visibleTop, int parentH) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
        viewTranslationX = 0;
    }

    private float dimAmount;
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public void setVisiblePart(float visibleTop, float tx, int parentH, float dimAmount) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
        viewTranslationX = tx;

        this.dimAmount = dimAmount;
        dimPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (0xFF * dimAmount)));
        invalidate();
    }

    @Override
    protected boolean onLongPress() {
        if (delegate != null) {
            return delegate.didLongPress(this, lastTouchX, lastTouchY);
        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        rippleView.layout((int) giftButtonRect.left, (int) giftButtonRect.top, (int) giftButtonRect.right, (int) giftButtonRect.bottom);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        imageReceiver.onDetachedFromWindow();
        wasLayout = false;
        AnimatedEmojiSpan.release(this, animatedEmojiStack);
        if (giftPremiumText != null) {
            giftPremiumText.detach();
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        avatarStoryParams.onDetachFromWindow();

        transitionParams.onDetach();
        reactionsLayoutInBubble.onDetachFromWindow();
        if (topicSeparator != null) {
            topicSeparator.detach();
        }
    }

    private boolean attachedToWindow;
    public boolean isCellAttachedToWindow() {
        return attachedToWindow;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        imageReceiver.onAttachedToWindow();

        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, canDrawInParent && (delegate != null && !delegate.canDrawOutboundsContent()), animatedEmojiStack, textLayout);
        if (giftPremiumText != null) {
            giftPremiumText.attach();
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        if (currentMessageObject != null && currentMessageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
            setMessageObject(currentMessageObject, true);
        }
        reactionsLayoutInBubble.onAttachToWindow();
        if (topicSeparator != null) {
            topicSeparator.attach();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        MessageObject messageObject = currentMessageObject;

        float x = lastTouchX = event.getX() - sideMenuWidth / 2f;
        float y = lastTouchY = event.getY() + getPaddingTop();
        boolean result = false;

        if (messageObject == null) {
            if (onActionClick != null) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (x >= backgroundLeft && x <= backgroundRight) {
                        actionPressed = true;
                        result = true;
                    }
                } else if (actionPressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        onActionClick.onClick(this);
                        actionPressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        actionPressed = false;
                    }
                }
            }
            if (result) return true;
            return super.onTouchEvent(event);
        }

        if (topicSeparator != null && topicSeparator.onTouchEvent(event, false)) {
            return true;
        }

        if (reactionsLayoutInBubble.checkTouchEvent(event)) {
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate != null) {
                if ((messageObject.type == MessageObject.TYPE_ACTION_PHOTO || isButtonLayout(messageObject)) && imageReceiver.isInsideImage(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (radialProgress.getIcon() == MediaActionDrawable.ICON_NONE && (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO || messageObject.type == MessageObject.TYPE_ACTION_WALLPAPER) && backgroundRect.contains(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (isButtonLayout(messageObject) && giftPremiumButtonLayout != null && (giftButtonRect.contains(x, y) || buttonClickableAsImage && backgroundRect.contains(x, y))) {
                    rippleView.setPressed(giftButtonPressed = true);
                    bounce.setPressed(true);
                    result = true;
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (actionPressed) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= backgroundLeft && x <= backgroundRight)) {
                        actionPressed = false;
                        result = false;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (onActionClick != null) {
                        onActionClick.onClick(this);
                    }
                    actionPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    actionPressed = false;
                }
            } else if (giftButtonPressed) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        imagePressed = false;
                        rippleView.setPressed(giftButtonPressed = false);
                        bounce.setPressed(false);
                        if (delegate != null) {
                            if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                                playSoundEffect(SoundEffectConstants.CLICK);
                                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                if (lastFragment != null) {
                                    new CommunitySheet(lastFragment, ((TLRPC.TL_messageActionChangeCommunity) messageObject.messageOwner.action).community_id)
                                        .show();
                                }
                            } else if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE) {
                                playSoundEffect(SoundEffectConstants.CLICK);
                                openThemeGift();
                            } else {
                                ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
                                if (imageUpdater == null) {
                                    if (buttonClickableAsImage) {
                                        delegate.didClickImage(this);
                                    } else {
                                        delegate.didClickButton(this);
                                    }
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        imagePressed = false;
                        rippleView.setPressed(giftButtonPressed = false);
                        bounce.setPressed(false);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!(isButtonLayout(messageObject) && (giftButtonRect.contains(x, y) || backgroundRect.contains(x, y)))) {
                            rippleView.setPressed(giftButtonPressed = false);
                            bounce.setPressed(false);
                        }
                        break;
                }
            } else if (imagePressed) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        imagePressed = false;
                        if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE) {
                            openThemeGift();
                        } else if (delegate != null) {
                            boolean consumed = false;
                            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                                ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
                                if (imageUpdater != null) {
                                    consumed = true;
                                    imageUpdater.cancel();
                                }
                            }
                            if (!consumed) {
                                delegate.didClickImage(this);
                                playSoundEffect(SoundEffectConstants.CLICK);
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        imagePressed = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isNewStyleButtonLayout()) {
                            if (!backgroundRect.contains(x, y)) {
                                imagePressed = false;
                            }
                        } else {
                            if (!imageReceiver.isInsideImage(x, y)) {
                                imagePressed = false;
                            }
                        }
                        break;
                }
            }
        }
        if (!result) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || (pressedLink != null || spoilerPressed != null) && event.getAction() == MotionEvent.ACTION_UP) {
                if (giftPremiumText != null && giftPremiumText.spoilers != null && !giftPremiumText.spoilers.isEmpty() && !isSpoilerRevealing) {
                    for (SpoilerEffect eff : giftPremiumText.spoilers) {
                        if (eff.getBounds().contains((int) (x - giftPremiumText.x), (int) (y - giftPremiumText.y))) {
                            pressedLink = null;
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                spoilerPressed = eff;
                                result = true;
                            } else {
                                if (eff == spoilerPressed) {
                                    isSpoilerRevealing = true;
                                    spoilerPressed.setOnRippleEndCallback(() -> post(() -> {
                                        isSpoilerRevealing = false;
                                        getMessageObject().isSpoilersRevealed = true;
                                        if (giftPremiumText.spoilers != null) {
                                            giftPremiumText.spoilers.clear();
                                        }
                                        invalidate();
                                    }));
                                    float width = giftPremiumText.layout.getWidth(), height = giftPremiumText.layout.getHeight();
                                    float rad = (float) Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2));
                                    spoilerPressed.startRipple((int) (x - giftPremiumText.x), (int) (y - giftPremiumText.y), rad);
                                    invalidate();
                                }
                                result = true;
                            }
                            break;
                        }
                    }
                }
                if (!result && textLayout != null && x >= textX && y >= textY && x <= textX + textWidth && y <= textY + textHeight) {
                    y -= textY;
                    x -= textXLeft;

                    if (!result) {
                        final int line = textLayout.getLineForVertical((int) y);
                        final int off = textLayout.getOffsetForHorizontal(line, x);
                        final float left = textLayout.getLineLeft(line);
                        if (left <= x && left + textLayout.getLineWidth(line) >= x && messageObject.messageText instanceof Spannable) {
                            Spannable buffer = (Spannable) messageObject.messageText;
                            URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);

                            if (link.length != 0) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    pressedLink = link[0];
                                    result = true;
                                } else {
                                    if (link[0] == pressedLink) {
                                        openLink(pressedLink);
                                        result = true;
                                    }
                                }
                            } else {
                                pressedLink = null;
                            }
                        } else {
                            pressedLink = null;
                        }
                    }
                } else {
                    pressedLink = null;
                }
            }
        }

        if (!result) {
            result = checkBotButtonMotionEvent(event);
        }

        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    // LoogriGram: openPremiumGiftChannel and openPremiumGiftPreview stood here,
    // with isSelfGiftCode and isGiftCode - a Premium gift or gift code's
    // preview, and a channel's gift code sheet with its "Use link". Those
    // messages are held unshown (LoogriGramHidden), so nothing reached them.

    private OnClickListener onActionClick;
    public void setOnActionClickListener(@Nullable OnClickListener l) {
        this.onActionClick = l;
    }

    // LoogriGram: this opened the sheet of every gift message too - a Stars,
    // TON or prize transaction, and a Star gift, burned or not. Those messages
    // are held unshown; a collectible's chat theme is what is left.
    private void openThemeGift() {
        if (currentMessageObject == null || currentMessageObject.messageOwner == null) return;
        if (currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) {
            TLRPC.TL_messageActionSetChatTheme action = (TLRPC.TL_messageActionSetChatTheme) currentMessageObject.messageOwner.action;
            if (action.theme instanceof TLRPC.TL_chatThemeUniqueGift) {
                TL_stars.StarGift gift = ((TLRPC.TL_chatThemeUniqueGift) action.theme).gift;
                if (gift instanceof TL_stars.TL_starGiftUnique) {
                    new StarGiftSheet(getContext(), currentAccount, currentMessageObject.getDialogId(), themeDelegate)
                        .set(gift.slug, (TL_stars.TL_starGiftUnique) gift, null)
                        .show();
                }
            }
        }
    }

    // LoogriGram: openStarsNeedSheet stood here. Tapping a "your balance was too low"
    // approval notice offered to buy the Stars it wanted; nothing here pays.

    private void openLink(CharacterStyle link) {
        if (delegate != null && link instanceof URLSpan) {
            String url = ((URLSpan) link).getURL();
            if (url.startsWith("task")) {
                final int taskId = Integer.parseInt(url.substring(5));
                delegate.didPressTaskLink(this, currentMessageObject.getReplyMsgId(), taskId);
            } else if (url.startsWith("topic") && pressedLink instanceof URLSpanNoUnderline) {
                URLSpanNoUnderline spanNoUnderline = (URLSpanNoUnderline) pressedLink;
                TLObject object = spanNoUnderline.getObject();
                if (object instanceof TLRPC.TL_forumTopic) {
                    TLRPC.TL_forumTopic forumTopic = (TLRPC.TL_forumTopic) object;
                    ForumUtilities.openTopic(delegate.getBaseFragment(), -delegate.getDialogId(), forumTopic, 0);
                }
            } else if (url.startsWith("invite") && pressedLink instanceof URLSpanNoUnderline) {
                URLSpanNoUnderline spanNoUnderline = (URLSpanNoUnderline) pressedLink;
                TLObject object = spanNoUnderline.getObject();
                if (object instanceof TLRPC.TL_chatInviteExported) {
                    TLRPC.TL_chatInviteExported invite = (TLRPC.TL_chatInviteExported) object;
                    delegate.needOpenInviteLink(invite);
                }
            } else if (url.startsWith("game")) {
                delegate.didPressReplyMessage(this, currentMessageObject.getReplyMsgId());
                /*TLRPC.KeyboardButton gameButton = null;
                MessageObject messageObject = currentMessageObject.replyMessageObject;
                if (messageObject != null && messageObject.messageOwner.reply_markup != null) {
                    for (int a = 0; a < messageObject.messageOwner.reply_markup.rows.size(); a++) {
                        TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                        for (int b = 0; b < row.buttons.size(); b++) {
                            TLRPC.KeyboardButton button = row.buttons.get(b);
                            if (button instanceof TLRPC.TL_keyboardButtonGame && button.game_id == currentMessageObject.messageOwner.action.game_id) {
                                gameButton = button;
                                break;
                            }
                        }
                        if (gameButton != null) {
                            break;
                        }
                    }
                }
                if (gameButton != null) {
                    delegate.didPressBotButton(messageObject, gameButton);
                }*/
            } else if (url.startsWith("http")) {
                Browser.openUrl(getContext(), url);
            } else {
                delegate.needOpenUserProfile(Long.parseLong(url));
            }
        }
    }

    private int overriddenMaxWidth;
    public void setOverrideTextMaxWidth(int width) {
        overriddenMaxWidth = width;
    }

    private void createLayout(CharSequence text, int width) {
        int maxWidth = width - dp(30);
        if (isSideMenued) {
            maxWidth -= dp(64);
        }
        if (maxWidth < 0) {
            return;
        }
        if (overriddenMaxWidth > 0) {
            maxWidth = Math.min(overriddenMaxWidth, maxWidth);
        }
        invalidatePath = true;
        TextPaint paint;
        if (currentMessageObject != null && currentMessageObject.type == MessageObject.TYPE_SHARING_OFFER) {
            paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText3);
        } else if (currentMessageObject != null && currentMessageObject.drawServiceWithDefaultTypeface) {
            paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText2);
        } else {
            paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        }
        paint.linkColor = paint.getColor();

        textLayout = new StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, canDrawInParent && (delegate != null && !delegate.canDrawOutboundsContent()), animatedEmojiStack, textLayout);

        textHeight = 0;
        textWidth = 0;

        if (currentMessageObject == null || !currentMessageObject.isRepostPreview) {
            try {
                int linesCount = textLayout.getLineCount();
                for (int a = 0; a < linesCount; a++) {
                    float lineWidth;
                    try {
                        lineWidth = textLayout.getLineWidth(a);
                        if (lineWidth > maxWidth) {
                            lineWidth = maxWidth;
                        }
                        textHeight = (int) Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
                    } catch (Exception e) {
                        FileLog.e(e);
                        return;
                    }
                    textWidth = (int) Math.max(textWidth, Math.ceil(lineWidth));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        textX = (width - textWidth) / 2;
        textY = dp(7);
        textXLeft = (width - textLayout.getWidth()) / 2;

        spoilersPool.addAll(spoilers);
        spoilers.clear();
        if (text instanceof Spannable) {
            SpoilerEffect.addSpoilers(this, textLayout, textX, textX + textWidth, (Spannable) text, spoilersPool, spoilers, null);
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        MessageObject messageObject = currentMessageObject;
        if (messageObject == null && customText == null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), topicSeparatorTopPadding + textHeight + dp(14));
            return;
        }
        if (isButtonLayout(messageObject)) {
            giftRectSize = Math.min((int) (AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() * 0.6f : AndroidUtilities.displaySize.x * 0.62f - dp(34)), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight - dp(64));
            if (messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
                giftRectSize = (int) (giftRectSize * 1.2f);
            }
            stickerSize = giftRectSize - dp(106);
            if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE) {
                giftRectSize = Math.min(giftRectSize, dp(192));
                stickerSize = dp(78);
            }
            if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                stickerSize = dp(52);
                imageReceiver.setRoundRadius(dp(14));
            } else {
                imageReceiver.setRoundRadius(stickerSize / 2);
            }
        }
        int width = Math.max(dp(30), MeasureSpec.getSize(widthMeasureSpec));
        if (previousWidth != width) {
            wasLayout = true;
            previousWidth = width;
            buildLayout();
        }
        int additionalHeight = 0;
        if (messageObject != null) {
            if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
                additionalHeight = AndroidUtilities.roundMessageSize + dp(10);
            }
        }

        int exactlyHeight = 0;
        if (isButtonLayout(messageObject)) {
            // LoogriGram: the older, taller card that gift messages were drawn
            // in was measured here as well. Those messages are held unshown
            // (LoogriGramHidden), so every card left is the newer style.
            int imageSize = getImageSize(messageObject);
            int giftTextHeight = giftPremiumText == null ? 0 : giftPremiumText.layout.getHeight();

            exactlyHeight = textY + textHeight + dp(4);
            backgroundRectHeight = 0;
            backgroundRectHeight += (imageSize > 0 ? (dp(16) * 2 + imageSize) : dp(16));
            backgroundRectHeight += giftTextHeight;
            float rectX = (previousWidth - giftPremiumButtonWidth) / 2f;
            if (giftPremiumButtonLayout != null) {
                backgroundButtonTop = exactlyHeight + backgroundRectHeight + dp(7);
                giftButtonRect.set(rectX - dp(18), backgroundButtonTop, rectX + giftPremiumButtonWidth + dp(18), backgroundButtonTop + giftPremiumButtonLayout.getHeight() + dp(8) * 2);
                backgroundRectHeight += dp(4) + giftButtonRect.height();
            } else if (messageObject.type != MessageObject.TYPE_SHARING_OFFER) {
                giftButtonRect.set(rectX - dp(18), backgroundButtonTop, rectX + giftPremiumButtonWidth + dp(18), backgroundButtonTop + dp(17) + dp(8) * 2);
                backgroundRectHeight += dp(17);
            }
            backgroundRectHeight += dp(15);
            exactlyHeight += backgroundRectHeight;
            exactlyHeight += dp(6);
            if (!reactionsLayoutInBubble.isEmpty) {
                reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + dp(8);
                exactlyHeight += reactionsLayoutInBubble.totalHeight;
            }
            if (botInlineButtons != null) {
                exactlyHeight += dp(44);
            }
            giftButtonRect.inset(dp(5), dp(1));
        }
        if (currentMessageObject != null && !reactionsLayoutInBubble.isEmpty) {
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + dp(8);
            additionalHeight += reactionsLayoutInBubble.totalHeight;
        }

        if (messageObject != null && isNewStyleButtonLayout()) {
            setMeasuredDimension(width, topicSeparatorTopPadding + exactlyHeight);
        } else {
            setMeasuredDimension(width, topicSeparatorTopPadding + textHeight + additionalHeight + dp(14));
        }
        reactionsLayoutInBubble.y = getMeasuredHeight() - getPaddingTop() - reactionsLayoutInBubble.totalHeight;
    }

    private boolean isNewStyleButtonLayout() {
        return currentMessageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE
            || currentMessageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED
            || currentMessageObject.type == MessageObject.TYPE_SHARING_OFFER
            || currentMessageObject.type == MessageObject.TYPE_SUGGEST_PHOTO
            || currentMessageObject.type == MessageObject.TYPE_ACTION_WALLPAPER
            || currentMessageObject.isStoryMention();
    }

    private int getImageSize(MessageObject messageObject) {
        if (messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
            return 0;
        } else if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
            return dp(52);
        }
        return dp(78);
    }

    private void buildLayout() {
        CharSequence text = null;
        MessageObject messageObject = currentMessageObject;
        if (messageObject != null) {
            if (messageObject.isExpiredStory()) {
                long dialogId = messageObject.messageOwner.media.user_id;
                if (dialogId != UserConfig.getInstance(currentAccount).getClientUserId()) {
                    text = StoriesUtilities.createExpiredStoryString(true, R.string.ExpiredStoryMention);
                } else {
                    text = StoriesUtilities.createExpiredStoryString(true, R.string.ExpiredStoryMentioned, MessagesController.getInstance(currentAccount).getUser(messageObject.getDialogId()).first_name);
                }
            } else if (delegate != null && delegate.getTopicId() == 0 && MessageObject.isTopicActionMessage(messageObject)) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-messageObject.getDialogId(), MessageObject.getTopicId(currentAccount, messageObject.messageOwner, true));
                text = ForumUtilities.createActionTextWithTopic(topic, messageObject);
            }
            if (text == null) {
                if (messageObject.messageOwner != null && messageObject.messageOwner.media != null && messageObject.messageOwner.media.ttl_seconds != 0) {
                    if (messageObject.messageOwner.media.photo != null) {
                        text = getString(R.string.AttachPhotoExpired);
                    } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_documentEmpty || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && messageObject.messageOwner.media.document == null) {
                        if (messageObject.messageOwner.media.voice) {
                            text = getString(R.string.AttachVoiceExpired);
                        } else if (messageObject.messageOwner.media.round) {
                            text = getString(R.string.AttachRoundExpired);
                        } else {
                            text = getString(R.string.AttachVideoExpired);
                        }
                    } else {
                        text = AnimatedEmojiSpan.cloneSpans(messageObject.messageText);
                    }
                } else {
                    text = AnimatedEmojiSpan.cloneSpans(messageObject.messageText);
                }
            }
        } else {
            text = customText;
        }
        if (currentMessageObject != null && currentMessageObject.isRepostPreview) {
            text = "";
        }
        if (currentMessageObject != null && currentMessageObject.messageOwner != null && currentMessageObject.messageOwner.action != null) {
            int iconResId = 0;
            if (currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionTodoAppendTasks) {
                iconResId = R.drawable.mini_checklist_add;
            } else if (currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionTodoCompletions) {
                final TLRPC.TL_messageActionTodoCompletions action = (TLRPC.TL_messageActionTodoCompletions) currentMessageObject.messageOwner.action;
                if (action.incompleted.size() > action.completed.size()) {
                    iconResId = R.drawable.mini_checklist_undone;
                } else {
                    iconResId = R.drawable.mini_checklist_done;
                }
            }
            if (iconResId != 0) {
                text = new SpannableStringBuilder(text);
                ((SpannableStringBuilder) text).insert(0, "i ");
                ((SpannableStringBuilder) text).setSpan(new ColoredImageSpan(iconResId), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        createLayout(text, previousWidth);
        if (messageObject != null) {
            // LoogriGram: the cards of the gift messages were laid out here as
            // well - a Premium gift or gift code, Stars, TON and Star gifts,
            // prizes, offers to buy a gift and their refusals - and the
            // notices of a suggested post's approval. Those messages are held
            // unshown (LoogriGramHidden).
            if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
                imageReceiver.setImageCoords((previousWidth - AndroidUtilities.roundMessageSize) / 2f, textHeight + dp(19), AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
            } else if (messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
                final TLRPC.TL_messageActionNoForwardsRequest action = (TLRPC.TL_messageActionNoForwardsRequest) messageObject.messageOwner.action;

                final SpannableStringBuilder ssb = new SpannableStringBuilder();
                final String userName = DialogObject.getShortName(MessagesController.getInstance(currentAccount).getUser(messageObject.getDialogId()));
                if (action.new_value) {
                    ssb.append(messageObject.isOut() ?
                        getString(R.string.SharingOfferDisableHeaderYou) :
                        replaceTags(formatString(R.string.SharingOfferDisableHeaderOther, userName)));
                } else {
                    ssb.append(messageObject.isOut() ?
                        getString(R.string.SharingOfferEnableHeaderYou) :
                        replaceTags(formatString(R.string.SharingOfferEnableHeaderOther, userName)));
                }

                if (action.new_value) {
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferDisable1), R.drawable.floating_check));
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferDisable2), R.drawable.floating_check));
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferDisable3), R.drawable.floating_check));
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferDisable4), R.drawable.floating_check));
                } else {
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferEnable1), R.drawable.floating_check));
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferEnable2), R.drawable.floating_check));
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferEnable3), R.drawable.floating_check));
                    ssb.append("\n\n");
                    ssb.append(createOption(getString(R.string.SharingOfferEnable4), R.drawable.floating_check));
                }

                createGiftPremiumLayouts(ssb, null, giftRectSize, false);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            } else if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE) {
                final TLRPC.TL_messageActionSetChatTheme action = (TLRPC.TL_messageActionSetChatTheme) messageObject.messageOwner.action;
                final TLRPC.TL_chatThemeUniqueGift chatThemeUniqueGift = (TLRPC.TL_chatThemeUniqueGift) action.theme;
                final TL_stars.StarGift gift = chatThemeUniqueGift.gift;
                final String giftTitle = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');

                final long fromDialogId = messageObject.getFromChatId();
                final boolean isUserSelf = UserConfig.getInstance(currentAccount).getClientUserId() == fromDialogId;

                final String t = isUserSelf ?
                    LocaleController.formatString(R.string.GiftThemesSetByYou, giftTitle):
                    LocaleController.formatString(R.string.GiftThemesSetByOther,
                        DialogObject.getShortName(currentAccount, fromDialogId), giftTitle);

                createGiftPremiumLayouts(AndroidUtilities.replaceTags(t), getString(R.string.GiftThemesSetActionView), giftRectSize, true);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            } else if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                final TLRPC.TL_messageActionChangeCommunity action = (TLRPC.TL_messageActionChangeCommunity) messageObject.messageOwner.action;
                final long peerId = DialogObject.getPeerDialogId(messageObject.messageOwner.peer_id);
                final boolean isChannel = ChatObject.isChannelAndNotMegaGroup(-peerId, currentAccount);
                final boolean isBot = peerId > 0;
                final String userName = DialogObject.getShortName(currentAccount, DialogObject.getPeerDialogId(messageObject.messageOwner.from_id));
                final String communityName = DialogObject.getShortName(currentAccount, -action.community_id);

                SpannableStringBuilder ssb = new SpannableStringBuilder();
                ssb.append(CommunityUtils.buildServiceMessageText(messageObject, communityName, userName, isChannel, isBot));

                createGiftPremiumLayouts(ssb, getString(R.string.GiftThemesSetActionView), giftRectSize, true);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            } else if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                TLRPC.TL_messageActionSuggestProfilePhoto actionSuggestProfilePhoto = (TLRPC.TL_messageActionSuggestProfilePhoto) messageObject.messageOwner.action;
                String description;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.isOutOwner() ? 0 : messageObject.getDialogId());
                boolean isVideo = actionSuggestProfilePhoto.video || (actionSuggestProfilePhoto.photo != null && actionSuggestProfilePhoto.photo.video_sizes != null && !actionSuggestProfilePhoto.photo.video_sizes.isEmpty());
                if (user.id == UserConfig.getInstance(currentAccount).clientUserId) {
                    TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(messageObject.getDialogId());
                    if (isVideo) {
                        description = formatString(R.string.ActionSuggestVideoFromYouDescription, user2.first_name);
                    } else {
                        description = formatString(R.string.ActionSuggestPhotoFromYouDescription, user2.first_name);
                    }
                } else {
                    if (isVideo) {
                        description = formatString(R.string.ActionSuggestVideoToYouDescription, user.first_name);
                    } else {
                        description = formatString(R.string.ActionSuggestPhotoToYouDescription, user.first_name);
                    }
                }
                String action;
                if (actionSuggestProfilePhoto.video || (actionSuggestProfilePhoto.photo.video_sizes != null && !actionSuggestProfilePhoto.photo.video_sizes.isEmpty())) {
                    action = getString(R.string.ViewVideoAction);
                } else {
                    action = getString(R.string.ViewPhotoAction);
                }
                createGiftPremiumLayouts(description, action, giftRectSize, true);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            } else if (messageObject.type == MessageObject.TYPE_ACTION_WALLPAPER) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.isOutOwner() ? 0 : messageObject.getDialogId());
                CharSequence description;
                String action = null;
                boolean actionClickableAsImage = true;
                if (messageObject.getDialogId() < 0) {
                    description = messageObject.messageText;
                } else if (!messageObject.isOutOwner() && messageObject.isWallpaperForBoth() && messageObject.isCurrentWallpaper()) {
                    description = messageObject.messageText;
                    action = getString(R.string.RemoveWallpaperAction);
                    actionClickableAsImage = false;
                } else if (user != null && user.id == UserConfig.getInstance(currentAccount).clientUserId) {
                    description = messageObject.messageText;
                } else {
                    description = messageObject.messageText;
                    action = getString(R.string.ViewWallpaperAction);
                }
                createGiftPremiumLayouts(description, action, giftRectSize, actionClickableAsImage);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            } else if (messageObject.isStoryMention()) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.media.user_id);
                CharSequence description;
                String action = null;

                if (user.self) {
                    TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(messageObject.getDialogId());
                    description = AndroidUtilities.replaceTags(formatString(R.string.StoryYouMentionedTitle, user2.first_name));
                } else {
                    description = AndroidUtilities.replaceTags(formatString(R.string.StoryMentionedTitle, user.first_name));
                }
                action = getString(R.string.StoryMentionedAction);

                createGiftPremiumLayouts(description, action, giftRectSize, true);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            }
        }
        reactionsLayoutInBubble.x = dp(12);
        reactionsLayoutInBubble.measure(previousWidth - dp(24), Gravity.CENTER_HORIZONTAL);
    }

    private CharSequence createOption(String text, @DrawableRes int iconRes) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(AndroidUtilities.replaceArrows(text, false, dp(6), -dp(1.3f), 0.8f, iconRes));
        ssb.insert(0, "*");
        ssb.setSpan(new DialogCell.FixedWidthSpan(dp(18)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ssb.setSpan(new LineHeightSpan.Standard(dp(12)), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    private void createGiftPremiumLayouts(CharSequence text, CharSequence button, int width, boolean buttonClickableAsImage) {
        // LoogriGram: a title, a subtitle, a "released by" line, a ribbon and
        // text that collapsed behind "more" were laid out here as well. Only
        // the cards of gift messages had them, and those are held unshown.
        width -= dp(16);
        if (currentMessageObject != null && currentMessageObject.type == MessageObject.TYPE_SHARING_OFFER) {
            giftTextPaint.setTextSize(dp(14.3f));
        } else {
            giftTextPaint.setTextSize(dp(13));
        }
        int textWidth = width - dp(12);
        if (currentMessageObject != null && (currentMessageObject.type == MessageObject.TYPE_ACTION_WALLPAPER && currentMessageObject.getDialogId() >= 0)) {
            final int recommendedWidthForTwoLines = HintView2.cutInFancyHalf(text, giftTextPaint);
            if (recommendedWidthForTwoLines < textWidth && recommendedWidthForTwoLines > textWidth / 5f) {
                textWidth = recommendedWidthForTwoLines;
            }
        }
        if (text == null) {
            if (giftPremiumText != null) {
                giftPremiumText.detach();
                giftPremiumText = null;
            }
        } else {
            if (giftPremiumText == null) {
                giftPremiumText = new TextLayout();
            }
            try {
                text = Emoji.replaceEmoji(text, giftTextPaint.getFontMetricsInt(), false);
            } catch (Exception ignore) {
            }
            giftPremiumText.setText(text, giftTextPaint, textWidth);
        }
        if (button != null) {
            SpannableStringBuilder buttonBuilder = SpannableStringBuilder.valueOf(button);
            buttonBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, buttonBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            giftPremiumButtonLayout = new StaticLayout(buttonBuilder, (TextPaint) getThemedPaint(Theme.key_paint_chatActionText), width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            this.buttonClickableAsImage = buttonClickableAsImage;
            giftPremiumButtonWidth = measureLayoutWidth(giftPremiumButtonLayout);
        } else {
            giftPremiumButtonLayout = null;
            this.buttonClickableAsImage = false;
            giftPremiumButtonWidth = 0;
        }
    }

    private float measureLayoutWidth(Layout layout) {
        float maxWidth = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            int lineWidth = (int) Math.ceil(layout.getLineWidth(i));
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth;
            }
        }
        return maxWidth;
    }

    public boolean showingCancelButton() {
        return radialProgress != null && radialProgress.getIcon() == MediaActionDrawable.ICON_CANCEL;
    }

    public int getCustomDate() {
        return customDate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(sideMenuWidth / 2f, getPaddingTop());

        MessageObject messageObject = currentMessageObject;
        int imageSize = stickerSize;
        if (isButtonLayout(messageObject)) {
            stickerSize = giftRectSize - dp(106);
            imageSize = getImageSize(messageObject);
            int top = textY + textHeight + dp(4) + dp(16);
            float x = (previousWidth - imageSize) / 2f;
            float y = top;
            if (messageObject.isStoryMention()) {
                avatarStoryParams.storyItem = messageObject.messageOwner.media.storyItem;
            }
            avatarStoryParams.originalAvatarRect.set(x, y, x + imageSize, y + imageSize);
            if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE || messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
                x += dp(10);
                y += dp(10);
                imageSize -= dp(20);
            }
            if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                x += dp(2);
            }

            imageReceiver.setImageCoords(x, y, Math.max(0, imageSize), Math.max(0, imageSize));
            if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE || messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
                imageSize += dp(20);
            }
            textPaint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
            if (textPaint != null) {
                if (giftTextPaint != null && giftTextPaint.getColor() != textPaint.getColor()) {
                    giftTextPaint.setColor(textPaint.getColor());
                    giftTextPaint.linkColor = textPaint.getColor();
                }
            }
        }

        drawBackground(canvas, false);

        if (isButtonLayout(messageObject) || (messageObject != null && messageObject.type == MessageObject.TYPE_ACTION_PHOTO)) {
            if (cardBackground != null && (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE || messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED)) {
                cardBackground.setBounds(
                    (int) (imageReceiver.getImageX() - dp(10 + GiftViews.CardBackground.PADDING_HORIZONTAL_DP)),
                    (int) (imageReceiver.getImageY() - dp(10 + GiftViews.CardBackground.PADDING_VERTICAL_DP)),
                    (int) (imageReceiver.getImageX() + imageReceiver.getImageWidth() + dp(10 + GiftViews.CardBackground.PADDING_HORIZONTAL_DP)),
                    (int) (imageReceiver.getImageY() + imageReceiver.getImageHeight() + dp(10 + GiftViews.CardBackground.PADDING_VERTICAL_DP))
                );
                cardBackground.draw(canvas);
            }

            if (wallpaperPreviewDrawable != null) {
                canvas.save();
                canvas.translate(imageReceiver.getImageX(), imageReceiver.getImageY());
                if (clipPath == null) {
                    clipPath = new Path();
                } else {
                    clipPath.rewind();
                }
                clipPath.addCircle(imageReceiver.getImageWidth() / 2f, imageReceiver.getImageHeight() / 2f, imageReceiver.getImageWidth() / 2f, Path.Direction.CW);
                canvas.clipPath(clipPath);
                wallpaperPreviewDrawable.setBounds(0, 0, (int) imageReceiver.getImageWidth(), (int) imageReceiver.getImageHeight());
                wallpaperPreviewDrawable.draw(canvas);
                canvas.restore();
            } else if (messageObject.isStoryMention()) {
                long dialogId = messageObject.messageOwner.media.user_id;
                avatarStoryParams.storyId = messageObject.messageOwner.media.id;
                StoriesUtilities.drawAvatarWithStory(dialogId, canvas, imageReceiver, avatarStoryParams);
             //   imageReceiver.draw(canvas);
            } else {
                imageReceiver.draw(canvas);
            }
            if (messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                DrawableUtils.drawCommunityCardDrawable(canvas, Theme.dialogs_communityCardsDrawable,
                        imageReceiver.getImageX() + dp(26), imageReceiver.getImageY() + dp(26), dp(52));
            }


            radialProgress.setProgressRect(
                    imageReceiver.getImageX(),
                    imageReceiver.getImageY(),
                    imageReceiver.getImageX() + imageReceiver.getImageWidth(),
                    imageReceiver.getImageY() + imageReceiver.getImageHeight()
            );
            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
                if (imageUpdater != null) {
                    radialProgress.setProgress(imageUpdater.getCurrentImageProgress(), true);
                    radialProgress.setCircleRadius((int) (imageReceiver.getImageWidth() * 0.5f) + 1);
                    radialProgress.setMaxIconSize(dp(24));
                    radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                    if (imageUpdater.getCurrentImageProgress() == 1f) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_NONE, true, true);
                    } else {
                        radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, true);
                    }
                }
                radialProgress.draw(canvas);
            } else if (messageObject.type == MessageObject.TYPE_ACTION_WALLPAPER) {
                float progress = getUploadingInfoProgress(messageObject);
                radialProgress.setProgress(progress, true);
                radialProgress.setCircleRadius(dp(26));
                radialProgress.setMaxIconSize(dp(24));
                radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                if (progress == 1f) {
                    radialProgress.setIcon(MediaActionDrawable.ICON_NONE, true, true);
                } else {
                    radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, true);
                }
                radialProgress.draw(canvas);
            }
        }

        if (textPaint != null && textLayout != null) {
            canvas.save();
            canvas.translate(textXLeft, textY);
            if (textLayout.getPaint() != textPaint) {
                buildLayout();
            }
            canvas.save();
            SpoilerEffect.clipOutCanvas(canvas, spoilers);
            SpoilerEffect.layoutDrawMaybe(textLayout, canvas);
            if (delegate == null || delegate.canDrawOutboundsContent()) {
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout, animatedEmojiStack, 0, spoilers, 0, 0, 0, 1f, textLayout == null ? null : getAdaptiveEmojiColorFilter(textLayout.getPaint().getColor()));
            }
            canvas.restore();

            for (SpoilerEffect eff : spoilers) {
                eff.setColor(textLayout.getPaint().getColor());
                eff.draw(canvas);
            }

            canvas.restore();
        }

        if (isButtonLayout(messageObject)) {
            float x = (previousWidth - giftRectSize) / 2f;
            if (messageObject.type != MessageObject.TYPE_ACTION_WALLPAPER) {
                x += dp(8);
            }
            float top = backgroundRect != null ? backgroundRect.top : (textY + textHeight + dp(4));
            float y = top + (imageSize > 0 ? (dp(16) * 2 + imageSize) : dp(16));
            if (messageObject.type == MessageObject.TYPE_GIFT_THEME_UPDATE || messageObject.type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                y -= dp(3.66f);
            }

            canvas.save();
            canvas.translate(x, y);
            if (messageObject.type == MessageObject.TYPE_ACTION_WALLPAPER) {
                if (radialProgress.getTransitionProgress() != 1f || radialProgress.getIcon() != MediaActionDrawable.ICON_NONE) {
                    if (settingWallpaperLayout == null) {
                        settingWallpaperPaint = new TextPaint();
                        settingWallpaperPaint.setTextSize(dp(13));
                        SpannableStringBuilder cs = new SpannableStringBuilder(getString(R.string.ActionSettingWallpaper));
                        int index = cs.toString().indexOf("..."), len = 3;
                        if (index < 0) {
                            index = cs.toString().indexOf("…");
                            len = 1;
                        }
                        if (index >= 0) {
                            SpannableString loading = new SpannableString("…");
                            UploadingDotsSpannable loadingDots = new UploadingDotsSpannable();
                            loadingDots.fixTop = true;
                            loadingDots.setParent(ChatActionCell.this, false);
                            loading.setSpan(loadingDots, 0, loading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            cs.replace(index, index + len, loading);
                        }
                        settingWallpaperLayout = new StaticLayout(cs, settingWallpaperPaint, giftPremiumText == null ? 1 : giftPremiumText.width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    }
                    float progressLocal = getUploadingInfoProgress(messageObject);
                    if (settingWallpaperProgressTextLayout == null || settingWallpaperProgress != progressLocal) {
                        settingWallpaperProgress = progressLocal;
                        settingWallpaperProgressTextLayout = new StaticLayout((int) (progressLocal * 100) + "%", giftTextPaint, giftPremiumText == null ? 1 : giftPremiumText.width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    }

                    settingWallpaperPaint.setColor(giftTextPaint.getColor());
                    if (radialProgress.getIcon() == MediaActionDrawable.ICON_NONE) {
                        float p = radialProgress.getTransitionProgress();
                        int oldColor = giftTextPaint.getColor();
                        settingWallpaperPaint.setAlpha((int) (Color.alpha(oldColor) * (1f - p)));
                        giftTextPaint.setAlpha((int) (Color.alpha(oldColor) * p));
                        giftTextPaint.linkColor = giftTextPaint.getColor();

                        float s;
                        if (giftPremiumText != null) {
                            s = 0.8f + 0.2f * p;
                            canvas.save();
                            canvas.scale(s, s, giftRectSize / 2f, giftPremiumText.layout.getHeight() / 2f);
                            canvas.translate((giftRectSize - giftPremiumText.layout.getWidth()) / 2f, 0);
                            giftPremiumText.x = x + (giftRectSize - giftPremiumText.layout.getWidth()) / 2f;
                            giftPremiumText.y = y;
                            SpoilerEffect.renderWithRipple(this, false, giftTextPaint.getColor(), 0, giftPremiumText.patchedLayout, 1, giftPremiumText.layout, giftPremiumText.spoilers, canvas, false);
                            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, giftPremiumText.layout, giftPremiumText.emoji, 0, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(giftTextPaint.getColor()));
                            canvas.restore();
                        }

                        giftTextPaint.setAlpha((int) (Color.alpha(oldColor) * (1f - p)));
                        giftTextPaint.linkColor = giftTextPaint.getColor();
                        s = 0.8f + 0.2f * (1f - p);
                        canvas.save();
                        canvas.scale(s, s, giftRectSize / 2f, settingWallpaperLayout.getHeight() / 2f);
                        canvas.translate((giftRectSize - settingWallpaperLayout.getWidth()) / 2f, 0);
                        SpoilerEffect.layoutDrawMaybe(settingWallpaperLayout, canvas);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(0, settingWallpaperLayout.getHeight() + dp(4));
                        canvas.scale(s, s, giftRectSize / 2f, settingWallpaperProgressTextLayout.getHeight() / 2f);
                        canvas.translate((giftRectSize - settingWallpaperProgressTextLayout.getWidth()) / 2f, 0);
                        SpoilerEffect.layoutDrawMaybe(settingWallpaperProgressTextLayout, canvas);
                        canvas.restore();


                        giftTextPaint.setColor(oldColor);
                        giftTextPaint.linkColor = oldColor;
                    } else {
                        canvas.save();
                        canvas.translate((giftRectSize - settingWallpaperLayout.getWidth()) / 2.f, 0.0f);
                        settingWallpaperLayout.draw(canvas);
                        canvas.restore();

                        canvas.save();
                        canvas.translate((giftRectSize - settingWallpaperProgressTextLayout.getWidth()) / 2.f, settingWallpaperLayout.getHeight() + dp(4));
                        SpoilerEffect.layoutDrawMaybe(settingWallpaperProgressTextLayout, canvas);
                        canvas.restore();
                    }
                } else if (giftPremiumText != null) {
                    canvas.save();
                    canvas.translate((giftRectSize - giftPremiumText.layout.getWidth()) / 2f, 0);
                    giftPremiumText.x = x + (giftRectSize - giftPremiumText.layout.getWidth()) / 2f;
                    giftPremiumText.y = y;
                    SpoilerEffect.renderWithRipple(this, false, giftTextPaint.getColor(), 0, giftPremiumText.patchedLayout, 1, giftPremiumText.layout, giftPremiumText.spoilers, canvas, false);
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, giftPremiumText.layout, giftPremiumText.emoji, 0, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(giftTextPaint.getColor()));
                    canvas.restore();
                }
            } else if (giftPremiumText != null) {
                canvas.save();
                canvas.translate((giftRectSize - dp(16) - giftPremiumText.layout.getWidth()) / 2f, 0);
                giftPremiumText.x = x + (giftRectSize - dp(16) - giftPremiumText.layout.getWidth()) / 2f;
                giftPremiumText.y = y;
                SpoilerEffect.renderWithRipple(this, false, giftPremiumText.paint.getColor(), 0, giftPremiumText.patchedLayout, 1, giftPremiumText.layout, giftPremiumText.spoilers, canvas, false);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, giftPremiumText.layout, giftPremiumText.emoji, 0, null, 0, 0, 0, 1f, getAdaptiveEmojiColorFilter(giftTextPaint.getColor()));
                canvas.restore();
            }
            canvas.restore();

            if (themeDelegate != null) {
                themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            }

            final float S = bounce.getScale(0.02f);
            canvas.save();
            canvas.scale(S, S, giftButtonRect.centerX(), giftButtonRect.centerY());

            if (giftPremiumButtonLayout != null) {
                Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackgroundSelected);
                canvas.drawRoundRect(giftButtonRect, dp(15), dp(15), backgroundPaint);
                if (hasGradientService()) {
                    canvas.drawRoundRect(giftButtonRect, dp(15), dp(15), getThemedPaint(Theme.key_paint_chatActionBackgroundDarken));
                }
                if (dimAmount > 0) {
                    canvas.drawRoundRect(giftButtonRect, dp(15), dp(15), dimPaint);
                }

                if (getMessageObject().type == MessageObject.TYPE_GIFT_THEME_UPDATE || getMessageObject().type == MessageObject.TYPE_COMMUNITY_CHANGED) {
                    final boolean isDark = themeDelegate != null ? themeDelegate.isDark() : Theme.isCurrentThemeDark();
                    int sC = dimPaint.getColor();
                    dimPaint.setColor(isDark ? 0x24FFFFFF : 0x10000000);
                    canvas.drawRoundRect(giftButtonRect, dp(15), dp(15), dimPaint);
                    dimPaint.setColor(sC);
                }

                // LoogriGram: a gift message's button also drew star particles
                // here; those messages are held unshown.
                invalidate();
            }

            if (messageObject.settingAvatar && progressToProgress != 1f) {
                progressToProgress += 16 / 150f;
            } else if (!messageObject.settingAvatar && progressToProgress != 0) {
                progressToProgress -= 16 / 150f;
            }
            progressToProgress = Utilities.clamp(progressToProgress, 1f, 0f);
            if (progressToProgress != 0) {
                if (progressView == null) {
                    progressView = new RadialProgressView(getContext());
                }
                int rad = dp(16);
                canvas.save();
                canvas.scale(progressToProgress, progressToProgress, giftButtonRect.centerX(), giftButtonRect.centerY());
                progressView.setSize(rad);
                progressView.setProgressColor(Theme.getColor(Theme.key_chat_serviceText));
                progressView.draw(canvas, giftButtonRect.centerX(), giftButtonRect.centerY());
                canvas.restore();
            }
            if (progressToProgress != 1f && giftPremiumButtonLayout != null) {
                canvas.save();
                float s = 1f - progressToProgress;
                canvas.scale(s, s, giftButtonRect.centerX(), giftButtonRect.centerY());
                canvas.translate(x, giftButtonRect.top + dp(7));
                canvas.translate((giftRectSize - dp(16) - giftPremiumButtonLayout.getWidth()) / 2f, 0);
                giftPremiumButtonLayout.draw(canvas);
                canvas.restore();
            }

            if (messageObject.flickerLoading) {
                if (loadingDrawable == null) {
                    loadingDrawable = new LoadingDrawable(themeDelegate);
                    loadingDrawable.setGradientScale(2f);
                    loadingDrawable.setAppearByGradient(true);
                    loadingDrawable.setColors(
                        Theme.multAlpha(Color.WHITE, .08f),
                        Theme.multAlpha(Color.WHITE, .2f),
                        Theme.multAlpha(Color.WHITE, .2f),
                        Theme.multAlpha(Color.WHITE, .7f)
                    );
                    loadingDrawable.strokePaint.setStrokeWidth(dp(1));
                }
                loadingDrawable.resetDisappear();
                loadingDrawable.setBounds(giftButtonRect);
                loadingDrawable.setRadiiDp(16);
                loadingDrawable.draw(canvas);
            } else if (loadingDrawable != null) {
                loadingDrawable.setBounds(giftButtonRect);
                loadingDrawable.setRadiiDp(16);
                loadingDrawable.disappear();
                loadingDrawable.draw(canvas);
                if (loadingDrawable.isDisappeared()) {
                    loadingDrawable.reset();
                }
            }

            canvas.restore();
        }

        drawReactions(canvas, false, null);

        transitionParams.recordDrawingState();
        canvas.restore();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == rippleView) {
            final float S = bounce.getScale(0.02f);
            canvas.save();
            canvas.scale(S, S, child.getX() + child.getMeasuredWidth() / 2f, child.getY() + child.getMeasuredHeight() / 2f);
            final boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void checkLeftRightBounds() {
        backgroundLeft = (int) Math.min(backgroundLeft, rect.left);
        backgroundRight = (int) Math.max(backgroundRight, rect.right);
    }

    public void drawBackground(Canvas canvas, boolean fromParent) {
        if (canDrawInParent) {
            if (hasGradientService() && !fromParent) {
                return;
            }
            if (!hasGradientService() && fromParent) {
                return;
            }
        }
        Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
        Paint darkenBackgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackgroundDarken);
        textPaint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        if (overrideBackground >= 0) {
            int color = getThemedColor(overrideBackground);
            if (overrideBackgroundPaint == null) {
                overrideBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overrideBackgroundPaint.setColor(color);
                overrideTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                overrideTextPaint.setTypeface(AndroidUtilities.bold());
                overrideTextPaint.setTextSize(dp(Math.max(16, SharedConfig.fontSize) - 2));
                overrideTextPaint.setColor(getThemedColor(overrideText));
            }
            backgroundPaint = overrideBackgroundPaint;
            textPaint = overrideTextPaint;
        }
        if (invalidatePath) {
            invalidatePath = false;
            backgroundLeft = getWidth();
            backgroundRight = 0;
            lineWidths.clear();
            final int count = textLayout == null ? 0 : textLayout.getLineCount();
            final int corner = dp(11);
            final int cornerIn = dp(8);

            int prevLineWidth = 0;
            for (int a = 0; a < count; a++) {
                int lineWidth = (int) Math.ceil(textLayout.getLineWidth(a));
                if (a != 0) {
                    int diff = prevLineWidth - lineWidth;
                    if (diff > 0 && diff <= 1.5f * corner + cornerIn) {
                        lineWidth = prevLineWidth;
                    }
                }
                lineWidths.add(lineWidth);
                prevLineWidth = lineWidth;
            }
            for (int a = count - 2; a >= 0; a--) {
                int lineWidth = lineWidths.get(a);
                int diff = prevLineWidth - lineWidth;
                if (diff > 0 && diff <= 1.5f * corner + cornerIn) {
                    lineWidth = prevLineWidth;
                }
                lineWidths.set(a, lineWidth);
                prevLineWidth = lineWidth;
            }

            int y = dp(4);
            int x = getMeasuredWidth() / 2;
            int previousLineBottom = 0;

            final int cornerOffset = dp(3);
            final int cornerInSmall = dp(6);
            final int cornerRest = corner - cornerOffset;

            lineHeights.clear();
            backgroundPath.reset();
            backgroundPath.moveTo(x, y);

            for (int a = 0; a < count; a++) {
                int lineWidth = lineWidths.get(a);
                int lineBottom = textLayout.getLineBottom(a);
                int nextLineWidth = a < count - 1 ? lineWidths.get(a + 1) : 0;

                int height = lineBottom - previousLineBottom;
                if (a == 0 || lineWidth > prevLineWidth) {
                    height += dp(3);
                }
                if (a == count - 1 || lineWidth > nextLineWidth) {
                    height += dp(3);
                }

                previousLineBottom = lineBottom;

                float startX = x + lineWidth / 2.0f;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerOffset - corner, y, startX + cornerRest, y + corner * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, -90, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX + cornerRest, y, startX + cornerRest + innerCornerRad * 2, y + innerCornerRad * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, -90, -90);
                }
                y += height;
                int yOffset = y;
                if (a != count - 1 && lineWidth < nextLineWidth) {
                    y -= dp(3);
                    height -= dp(3);
                }
                if (a != 0 && lineWidth < prevLineWidth) {
                    y -= dp(3);
                    height -= dp(3);
                }
                lineHeights.add(height);

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerOffset - corner, y - corner * 2, startX + cornerRest, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 0, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX + cornerRest, y - innerCornerRad * 2, startX + cornerRest + innerCornerRad * 2, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 180, -90);
                }

                prevLineWidth = lineWidth;
            }
            for (int a = count - 1; a >= 0; a--) {
                prevLineWidth = a != 0 ? lineWidths.get(a - 1) : 0;
                int lineWidth = lineWidths.get(a);
                int nextLineWidth = a != count - 1 ? lineWidths.get(a + 1) : 0;
                int lineBottom = textLayout.getLineBottom(a);
                float startX = x - lineWidth / 2;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerRest, y - corner * 2, startX + cornerOffset + corner, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 90, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y - innerCornerRad * 2, startX - cornerRest, y);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 90, -90);
                }

                y -= lineHeights.get(a);

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerRest, y, startX + cornerOffset + corner, y + corner * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 180, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y, startX - cornerRest, y + innerCornerRad * 2);
                    checkLeftRightBounds();
                    backgroundPath.arcTo(rect, 0, -90);
                }
            }
            backgroundPath.close();
        }
        if (!visiblePartSet) {
            ViewGroup parent = (ViewGroup) getParent();
            backgroundHeight = parent.getMeasuredHeight();
        }
        if (themeDelegate != null) {
            themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
        }

        int oldAlpha = -1;
        int oldAlpha2 = -1;
        if (fromParent && (getAlpha() != 1f || isFloating())) {
            oldAlpha = backgroundPaint.getAlpha();
            oldAlpha2 = darkenBackgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (oldAlpha * getAlpha() * (isFloating() ? .75f : 1f)));
            darkenBackgroundPaint.setAlpha((int) (oldAlpha2 * getAlpha() * (isFloating() ? .75f : 1f)));
        } else if (isFloating()) {
            oldAlpha = backgroundPaint.getAlpha();
            oldAlpha2 = darkenBackgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (oldAlpha * (isFloating() ? .75f : 1f)));
            darkenBackgroundPaint.setAlpha((int) (oldAlpha2 * (isFloating() ? .75f : 1f)));
        }
        if (currentMessageObject == null || !currentMessageObject.isRepostPreview) {
            canvas.drawPath(backgroundPath, backgroundPaint);
            if (hasGradientService() && darkenBackgroundPaint.getAlpha() > 0) {
                canvas.drawPath(backgroundPath, darkenBackgroundPaint);
            }
            if (dimAmount > 0) {
                int wasAlpha = dimPaint.getAlpha();
                if (fromParent) {
                    dimPaint.setAlpha((int) (wasAlpha * getAlpha()));
                }
                canvas.drawPath(backgroundPath, dimPaint);
                dimPaint.setAlpha(wasAlpha);
            }
        }

        MessageObject messageObject = currentMessageObject;
        if (isButtonLayout(messageObject)) {
            float x = (getWidth() - giftRectSize) / 2f;
            float y = textY + textHeight + dp(4);
            AndroidUtilities.rectTmp.set(x, y, x + giftRectSize, y + backgroundRectHeight);
            if (backgroundRect == null) {
                backgroundRect = new RectF();
            }
            backgroundRect.set(AndroidUtilities.rectTmp);

            boolean backgroundDrawn = false;
            if (messageObject != null && messageObject.type == MessageObject.TYPE_SHARING_OFFER) {
                if (botInlineButtons != null) {
                    Arrays.fill(radii, dp(16));
                    radii[4] = radii[5] = radii[6] = radii[7] = dp(6);
                    backgroundPath2.rewind();
                    backgroundPath2.addRoundRect(backgroundRect, radii, Path.Direction.CW);
                    canvas.drawPath(backgroundPath2, backgroundPaint);
                    if (hasGradientService()) {
                        canvas.drawPath(backgroundPath2, darkenBackgroundPaint);
                    }
                    backgroundDrawn = true;
                }
            }

            if (!backgroundDrawn) {
                canvas.drawRoundRect(backgroundRect, dp(20), dp(20), backgroundPaint);

                if (hasGradientService()) {
                    canvas.drawRoundRect(backgroundRect, dp(20), dp(20), darkenBackgroundPaint);
                }
            }
        }

        if (oldAlpha >= 0) {
            backgroundPaint.setAlpha(oldAlpha);
            darkenBackgroundPaint.setAlpha(oldAlpha2);
        }
    }

    private final Path backgroundPath2 = new Path();
    private final float[] radii = new float[8];

    private final float[] botButtonRadii = new float[8];
    private final Path botButtonPath = new Path();

    private void drawBotButtons(Canvas canvas, ArrayList<BotButton> botButtons) {
        if (botButtons == null || botButtons.isEmpty()) {
            return;
        }

        if (themeDelegate != null) {
            themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
        }

        float x1 = (getWidth() - giftRectSize) / 2f;
        float y = textY + textHeight + dp(4) + backgroundRectHeight + dp(4);
        final int widthForButtons = giftRectSize;
        float buttonWidth = (widthForButtons - dp(4)) / 2f;

        for (int a = 0; a < botButtons.size(); a++) {
            BotButton button = botButtons.get(a);
            float s = button.getPressScale();

            float left = x1 + (buttonWidth + dp(4)) * a;
            float right = left + buttonWidth;


            rect.set(left, y, right, y + button.height);
            canvas.save();
            if (s != 1) {
                canvas.scale(s, s, rect.centerX(), rect.centerY());
            }
            Arrays.fill(botButtonRadii, dp(Math.min(6.75f, SharedConfig.bubbleRadius)));
            if (button.hasPositionFlag(MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM)) {
                botButtonRadii[6] = botButtonRadii[7] = dp(SharedConfig.bubbleRadius);
            }
            if (button.hasPositionFlag(MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM)) {
                botButtonRadii[4] = botButtonRadii[5] = dp(SharedConfig.bubbleRadius);
            }

            botButtonPath.rewind();
            botButtonPath.addRoundRect(rect, botButtonRadii, Path.Direction.CW);

            canvas.drawPath(botButtonPath, getThemedPaint(Theme.key_paint_chatActionBackground));
            if (hasGradientService()) {
                canvas.drawPath(botButtonPath, Theme.chat_actionBackgroundGradientDarkenPaint);
            }

            canvas.save();
            canvas.clipPath(botButtonPath);

            if (button.selectorDrawable != null) {
                button.selectorDrawable.setBounds((int) left, (int) y, (int) right, (int) y + button.height);
                button.selectorDrawable.setAlpha(0xFF);
                button.selectorDrawable.draw(canvas);
            }
            canvas.restore();

            canvas.save();

            int iconWithMarginPx = button.iconDrawable != null ? dp(24 + 2) : 0;
            float titleX = left + (buttonWidth - (button.title.getWidth() + (button.iconDrawable != null ? dp(4): 0)) - iconWithMarginPx) / 2f;
            if (button.iconDrawable != null) {
                button.iconDrawable.setBounds(
                        (int) titleX,
                        (int) (y + (button.height - dp(24)) / 2f),
                        (int) (titleX) + dp(24),
                        (int) (y + (button.height - dp(24)) / 2f) + dp(24)
                );
                button.iconDrawable.setAlpha(button.isLocked ? 128 : 255);
                button.iconDrawable.draw(canvas);
                titleX += iconWithMarginPx;
            }
            button.title.ellipsize(Math.max(1, (int) (buttonWidth) - dp(15) - iconWithMarginPx));
            button.title.draw(canvas, titleX, y + dp(40) / 2f, button.isLocked ? 0.5f: 1f);
            canvas.restore();
            canvas.restore();
        }
    }


    private final int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private int pressedBotButton;

    private boolean checkBotButtonMotionEvent(MotionEvent event) {
        if (botButtons.isEmpty()) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        float x1 = (getWidth() - giftRectSize) / 2f;
        float y1 = textY + textHeight + dp(4) + backgroundRectHeight + dp(4);
        final int widthForButtons = giftRectSize;
        float buttonWidth = (widthForButtons - dp(4)) / 2f;

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedBotButton = -1;

            for (int a = 0; a < botButtons.size(); a++) {
                BotButton button = botButtons.get(a);

                float left = x1 + (buttonWidth + dp(4)) * a;
                float right = left + buttonWidth;
                AndroidUtilities.rectTmp.set(left, y1, right, y1 + button.height);
                if (AndroidUtilities.rectTmp.contains(x, y)) {
                    pressedBotButton = a;
                    invalidateOutbounds();
                    result = true;
                    if (button.selectorDrawable == null) {
                        button.selectorDrawable = Theme.createRadSelectorDrawable(getThemedColor(Theme.key_chat_serviceBackgroundSelector), 6, 6);
                        button.selectorDrawable.setCallback(this);
                    }
                    button.selectorDrawable.setHotspot(x, y);
                    button.selectorDrawable.setState(pressedState);
                    button.setPressed(!button.isLocked);
                    break;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedBotButton != -1) {
                playSoundEffect(SoundEffectConstants.CLICK);
                BotButton button = botButtons.get(pressedBotButton);
                if (button.selectorDrawable != null) {
                    button.selectorDrawable.setState(StateSet.NOTHING);
                }
                button.setPressed(false);
                if (delegate != null && !button.isLocked) {
                    if (button.buttonCustom != null) {
                        didPressCustomBotButton(button.buttonCustom);
                    }
                }
                pressedBotButton = -1;
                invalidateOutbounds();
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedBotButton != -1) {
                BotButton button = botButtons.get(pressedBotButton);
                if (button.selectorDrawable != null) {
                    button.selectorDrawable.setState(StateSet.NOTHING);
                }
                button.setPressed(false);
                pressedBotButton = -1;
                invalidateOutbounds();
            }
        }
        return result;
    }

    private void didPressCustomBotButton(BotInlineKeyboard.ButtonCustom button) {
        if (getMessageObject() == null) {
            return;
        }

        if (button.id == BotInlineKeyboard.ButtonCustom.SHARING_OFFER_DECLINE) {
            final BaseFragment fragment = delegate != null ? delegate.getBaseFragment() : null;
            if (fragment != null && currentMessageObject != null && currentMessageObject.messageOwner != null && currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionNoForwardsRequest) {
                final TLRPC.TL_messageActionNoForwardsRequest action = (TLRPC.TL_messageActionNoForwardsRequest) currentMessageObject.messageOwner.action;
                AlertsCreator.showSimpleConfirmAlert(fragment, getString(action.prev_value ? R.string.SharingOfferDisableCancelTitle : R.string.SharingOfferEnableCancelTitle),
                    getString(action.prev_value ? R.string.SharingOfferDisableCancelText : R.string.SharingOfferEnableCancelText),
                    getString(R.string.SharingOfferCancelYes), false,
                    () -> MessagesController.getInstance(currentAccount).toggleChatNoForwards(currentMessageObject.getDialogId(), currentMessageObject.getId(), action.prev_value, null));
            }
        } else if (button.id == BotInlineKeyboard.ButtonCustom.SHARING_OFFER_ACCEPT) {
            final BaseFragment fragment = delegate != null ? delegate.getBaseFragment() : null;
            if (fragment != null && currentMessageObject != null && currentMessageObject.messageOwner != null && currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionNoForwardsRequest) {
                final TLRPC.TL_messageActionNoForwardsRequest action = (TLRPC.TL_messageActionNoForwardsRequest) currentMessageObject.messageOwner.action;
                AlertsCreator.showSimpleConfirmAlert(fragment, getString(action.new_value ? R.string.SharingOfferDisableCancelTitle : R.string.SharingOfferEnableCancelTitle),
                    getString(action.new_value ? R.string.SharingOfferDisableConfirmText : R.string.SharingOfferEnableConfirmText),
                    getString(R.string.SharingOfferCancelYes), false,
                    () -> MessagesController.getInstance(currentAccount).toggleChatNoForwards(currentMessageObject.getDialogId(), currentMessageObject.getId(), action.new_value, null));
            }
        }
    }

    public void drawReactions(Canvas canvas, boolean fromParent, Integer only) {
        if (canDrawInParent) {
            if (hasGradientService() && !fromParent) {
                return;
            }
            if (!hasGradientService() && fromParent) {
                return;
            }
        }
        drawReactionsLayout(canvas, fromParent, only);
    }

    public void drawReactionsLayout(Canvas canvas, boolean fromParent, Integer only) {
        final float alpha = fromParent ? getAlpha() : 1.0f;
        if (alpha <= 0) {
            return;
        }
        if (themeDelegate != null) {
            themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
        }
        if (currentMessageObject != null && currentMessageObject.shouldDrawReactions() && (!reactionsLayoutInBubble.isSmall || transitionParams.animateChange && reactionsLayoutInBubble.animateHeight)) {
            reactionsLayoutInBubble.drawServiceShaderBackground = 1.0f;
            if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }
            reactionsLayoutInBubble.draw(canvas, transitionParams.animateChange ? transitionParams.animateChangeProgress : 1f, only);
            if (alpha < 1) {
                canvas.restore();
            }
        }
    }

    // LoogriGram: drawReactionsLayoutOverlay stood here, and nothing had called it
    // for a while. The only overlay a reaction row ever had was the paid one.

    @Override
    public int getBoundsLeft() {
        if (isButtonLayout(currentMessageObject)) {
            return sideMenuWidth / 2 + (getWidth() - giftRectSize) / 2;
        }
        int left = backgroundLeft;
        if (imageReceiver != null && imageReceiver.getVisible()) {
            left = Math.min((int) imageReceiver.getImageX(), left);
        }
        return sideMenuWidth / 2 + left;
    }

    @Override
    public int getBoundsRight() {
        if (isButtonLayout(currentMessageObject)) {
            return sideMenuWidth / 2 + (getWidth() + giftRectSize) / 2;
        }
        int right = backgroundRight;
        if (imageReceiver != null && imageReceiver.getVisible()) {
            right = Math.max((int) imageReceiver.getImageX2(), right);
        }
        return sideMenuWidth / 2 + right;
    }

    public boolean hasGradientService() {
        return overrideBackgroundPaint == null && (themeDelegate != null ? themeDelegate.hasGradientService() : Theme.hasGradientService());
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {
        MessageObject messageObject = currentMessageObject;
        if (messageObject != null && messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
            TLRPC.PhotoSize strippedPhotoSize = null;
            for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                    strippedPhotoSize = photoSize;
                    break;
                }
            }
            imageReceiver.setImage(currentVideoLocation, ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, messageObject, 1);
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }
    }

    @Override
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {

    }

    @Override
    public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    private SpannableStringBuilder accessibilityText;

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        MessageObject messageObject = currentMessageObject;
        if (TextUtils.isEmpty(customText) && messageObject == null) {
            return;
        }
        if (accessibilityText == null) {
            CharSequence text = !TextUtils.isEmpty(customText) ? customText : messageObject.messageText;
            SpannableStringBuilder sb = new SpannableStringBuilder(text);
            CharacterStyle[] links = sb.getSpans(0, sb.length(), ClickableSpan.class);
            for (CharacterStyle link : links) {
                int start = sb.getSpanStart(link);
                int end = sb.getSpanEnd(link);
                sb.removeSpan(link);

                ClickableSpan underlineSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        if (delegate != null) {
                            openLink(link);
                        }
                    }
                };
                sb.setSpan(underlineSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            accessibilityText = sb;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            info.setContentDescription(accessibilityText.toString());
        } else {
            info.setText(accessibilityText);
        }
        info.setEnabled(true);
    }

    public void setInvalidateColors(boolean invalidate) {
        if (invalidateColors == invalidate) {
            return;
        }
        invalidateColors = invalidate;
        invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, themeDelegate);
    }

    protected Paint getThemedPaint(String paintKey) {
        Paint paint = themeDelegate != null ? themeDelegate.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public void drawOutboundsContent(Canvas canvas) {
        canvas.save();
        canvas.translate(sideMenuWidth / 2f, getPaddingTop());

        canvas.save();
        canvas.translate(textXLeft, textY);
        AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout, animatedEmojiStack, 0, spoilers, 0, 0, 0, 1f, textLayout != null ? getAdaptiveEmojiColorFilter(textLayout.getPaint().getColor()) : null);
        canvas.restore();
        canvas.restore();

        if (topicSeparator != null) {
            final float alpha = getAlpha(); // transitionParams.ignoreAlpha ? timeAlpha : getAlpha();
            final float top = 0;//- topicSeparatorTopPadding + (getTopicSeparatorTopPadding() - topicSeparatorTopPadding);;
            if (themeDelegate != null) {
                themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + top);
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + top);
            }
            topicSeparator.draw(canvas, getWidth(), sideMenuWidth, top, 1.0f, alpha, showTopicSeparator);
        }

        drawBotButtons(canvas, botButtons);
    }

    private boolean isButtonLayout(MessageObject messageObject) {
        return messageObject != null && isNewStyleButtonLayout();
    }

    private boolean invalidatesParent;
    public void setInvalidatesParent(boolean value) {
        invalidatesParent = value;
    }

    private Runnable invalidateListener;
    public void setInvalidateListener(Runnable listener) {
        invalidateListener = listener;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (invalidateWithParent != null) {
            invalidateWithParent.invalidate();
        }
        if (invalidateListener != null) {
            invalidateListener.run();
        }
        if (invalidatesParent && getParent() != null) {
            View parent = (View) getParent();
            if (parent.getParent() != null) {
                parent.invalidate();
                parent = (View) parent.getParent();
                parent.invalidate();
            }
        }
    }

    public void invalidateOutbounds() {
        if (delegate == null || !delegate.canDrawOutboundsContent()) {
            if (getParent() instanceof View) {
                ((View) getParent()).invalidate();
            }
        } else {
            super.invalidate();
        }
    }

    @Override
    public void invalidate(Rect dirty) {
        super.invalidate(dirty);
        if (invalidateWithParent != null) {
            invalidateWithParent.invalidate();
        }
        if (invalidatesParent && getParent() != null) {
            View parent = (View) getParent();
            if (parent.getParent() != null) {
                parent.invalidate();
                parent = (View) parent.getParent();
                parent.invalidate();
            }
        }
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        super.invalidate(l, t, r, b);
        if (invalidateWithParent != null) {
            invalidateWithParent.invalidate();
        }
        if (invalidatesParent && getParent() != null) {
            View parent = (View) getParent();
            if (parent.getParent() != null) {
                parent.invalidate();
                parent = (View) parent.getParent();
                parent.invalidate();
            }
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == wallpaperPreviewDrawable || super.verifyDrawable(who);
    }

    public boolean isFloating() {
        return false;
    }

    private ColorFilter adaptiveEmojiColorFilter;
    private int adaptiveEmojiColor;
    private ColorFilter getAdaptiveEmojiColorFilter(int color) {
        if (color != adaptiveEmojiColor || adaptiveEmojiColorFilter == null) {
            adaptiveEmojiColorFilter = new PorterDuffColorFilter(adaptiveEmojiColor = color, PorterDuff.Mode.SRC_IN);
        }
        return adaptiveEmojiColorFilter;
    }

    public int measuredWidth() {
        return getMeasuredWidth();
    }

    public ReactionsLayoutInBubble.ReactionButton getReactionButton(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        return reactionsLayoutInBubble.getReactionButton(visibleReaction);
    }

    public final TransitionParams transitionParams = new TransitionParams();
    public class TransitionParams {

        public boolean wasDraw;

        public boolean animateChange;
        public float animateChangeProgress = 1f;

        public void recordDrawingState() {
            wasDraw = true;
            reactionsLayoutInBubble.recordDrawingState();
        }

        public boolean animateChange() {
            if (!wasDraw) {
                return false;
            }
            boolean changed = false;
            if (reactionsLayoutInBubble.animateChange()) {
                changed = true;
            }
            return changed;
        }

        public void onDetach() {
            wasDraw = false;
        }

        public boolean supportChangeAnimation() {
            return true;
        }

        public void resetAnimation() {
            animateChange = false;
            animateChangeProgress = 1f;
        }
    }

    public TransitionParams getTransitionParams() {
        return transitionParams;
    }

    public float getDeltaTop() { return 0; }
    public float getDeltaLeft() { return 0; }
    public float getDeltaRight() { return 0; }
    public float getDeltaBottom() { return 0; }

    public void setScrimReaction(Integer scrimViewReaction) {
        reactionsLayoutInBubble.setScrimReaction(scrimViewReaction);
    }

    public void drawScrimReaction(Canvas canvas, Integer scrimViewReaction, float progress, boolean direction) {
        if (!reactionsLayoutInBubble.isSmall) {
            if (themeDelegate != null) {
                themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            }
            reactionsLayoutInBubble.setScrimProgress(progress, direction);
            reactionsLayoutInBubble.draw(canvas, transitionParams.animateChangeProgress, scrimViewReaction);
        }
    }

    public void drawScrimReactionPreview(View view, Canvas canvas, int offset, Integer scrimViewReaction, float progress) {
        if (!reactionsLayoutInBubble.isSmall) {
            if (themeDelegate != null) {
                themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            }
            reactionsLayoutInBubble.setScrimProgress(progress);
            reactionsLayoutInBubble.drawPreview(view, canvas, offset, scrimViewReaction);
        }
    }

    public boolean checkUnreadReactions(float clipTop, int clipBottom) {
        if (!reactionsLayoutInBubble.hasUnreadReactions) {
            return false;
        }
        float y = getY() + reactionsLayoutInBubble.y;
        if (y > clipTop && y + reactionsLayoutInBubble.height - AndroidUtilities.dp(16) < clipBottom) {
            return true;
        }
        return false;
    }

    public void markReactionsAsRead() {
        reactionsLayoutInBubble.hasUnreadReactions = false;
        if (currentMessageObject == null) {
            return;
        }
        currentMessageObject.markReactionsAsRead();
    }
}
