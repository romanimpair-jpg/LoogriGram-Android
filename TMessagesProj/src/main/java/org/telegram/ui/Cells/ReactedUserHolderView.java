package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageSeenCheckDrawable;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.StatusBadgeComponent;

public class ReactedUserHolderView extends FrameLayout {
    public boolean drawDivider;
    int currentAccount;

    // LoogriGram: STYLE_STORY was a story's viewers list: a taller row with a
    // bigger avatar, the story's thumbnail, and a like, repost or forward in
    // place of the reaction. It went with stories; the message reactions list
    // is the one user left.
    public BackupImageView avatarView;
    SimpleTextView titleView;
    SimpleTextView subtitleView;
    BackupImageView reactView;
    AvatarDrawable avatarDrawable = new AvatarDrawable();
    View overlaySelectorView;
    StatusBadgeComponent statusBadgeComponent;
    public final static int ITEM_HEIGHT_DP = 50;
    Theme.ResourcesProvider resourcesProvider;
    public long dialogId;

    public static final MessageSeenCheckDrawable seenDrawable = new MessageSeenCheckDrawable(R.drawable.msg_mini_checks, Theme.key_windowBackgroundWhiteGrayText);
    public static final MessageSeenCheckDrawable reactDrawable = new MessageSeenCheckDrawable(R.drawable.msg_reactions, Theme.key_windowBackgroundWhiteGrayText, 16, 16, 5.66f);

    public ReactedUserHolderView(int currentAccount, @NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean useOverlaySelector, boolean showReactionPreview) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(ITEM_HEIGHT_DP)));

        int avatarSize = 34;
        // LoogriGram: in a story's viewers list the avatar drew the viewer's own
        // story ring, and a tap opened their stories. Stories are removed.
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(avatarSize));
        addView(avatarView, LayoutHelper.createFrameRelatively(avatarSize, avatarSize, Gravity.START | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        titleView = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value) {
                value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), false);
                return super.setText(value);
            }
        };
        NotificationCenter.listenEmojiLoading(titleView);
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        titleView.setEllipsizeByGradient(true);
        titleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleView.setRightPadding(AndroidUtilities.dp(30));
        titleView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        titleView.setRightDrawableOutside(true);
        float topMargin = 5.33f;
        float leftMargin = 55;
        addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, leftMargin, topMargin, 12, 0));

        statusBadgeComponent = new StatusBadgeComponent(this);
        titleView.setDrawablePadding(AndroidUtilities.dp(3));
        titleView.setRightDrawable(statusBadgeComponent.getDrawable());

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        subtitleView.setEllipsizeByGradient(true);
        subtitleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitleView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        topMargin = 19f;
        addView(subtitleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, leftMargin, topMargin , 20, 0));

        if (showReactionPreview) {
            reactView = new BackupImageView(context);
            addView(reactView, LayoutHelper.createFrameRelatively(24, 24, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        }

        if (useOverlaySelector) {
            overlaySelectorView = new View(context);
            overlaySelectorView.setBackground(Theme.getSelectorDrawable(false));
            addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
    }

    private void setUserReaction(TLRPC.User user, TLRPC.Chat chat, TLRPC.Reaction reaction, long date, boolean dateIsSeen) {
        TLObject u = user;
        if (u == null) {
            u = chat;
        }
        if (u == null) {
            return;
        }

        int colorFilter = Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider);
        statusBadgeComponent.updateDrawable(user, chat, colorFilter, false);

        avatarDrawable.setInfo(currentAccount, u);
        if (user != null) {
            dialogId = user.id;
            titleView.setText(UserObject.getUserName(user));
        } else {
            dialogId = -chat.id;
            titleView.setText(chat.title);
        }

        Drawable thumb = avatarDrawable;
        if (user != null) {
            if (user.photo != null && user.photo.strippedBitmap != null) {
                thumb = user.photo.strippedBitmap;
            }
        } else {
            if (chat.photo != null && chat.photo.strippedBitmap != null) {
                thumb = chat.photo.strippedBitmap;
            }
        }
        avatarView.setImage(ImageLocation.getForUserOrChat(currentAccount, u, ImageLocation.TYPE_SMALL), "50_50", thumb, u);

        String contentDescription = "";
        boolean hasReactImage = false;
        if (reaction != null) {
            ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(reaction);
            if (visibleReaction.emojicon != null) {
                if (reactView != null) {
                    reactView.setAnimatedEmojiDrawable(null);
                }
                TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(visibleReaction.emojicon);
                if (reactView != null) {
                    if (r != null) {
                        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon.thumbs, Theme.key_windowBackgroundGray, 1.0f);
                        reactView.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", "webp", svgThumb, r);
                        hasReactImage = true;
                    } else {
                        reactView.setImageDrawable(null);
                    }
                    reactView.setColorFilter(null);
                }
            } else {
                AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, currentAccount, visibleReaction.documentId);
                drawable.setColorFilter(Theme.getAnimatedEmojiColorFilter(resourcesProvider));
                if (reactView != null) {
                    reactView.setAnimatedEmojiDrawable(drawable);
                }
                hasReactImage = true;
            }
            contentDescription = LocaleController.formatString("AccDescrReactedWith", R.string.AccDescrReactedWith, titleView.getText(), visibleReaction.emojicon != null ? visibleReaction.emojicon : reaction);
        } else {
            if (reactView != null) {
                reactView.setAnimatedEmojiDrawable(null);
                reactView.setImageDrawable(null);
            }
            contentDescription = LocaleController.formatString("AccDescrPersonHasSeen", R.string.AccDescrPersonHasSeen, titleView.getText());
        }

        if (date != 0) {
            contentDescription += " " + LocaleController.formatSeenDate(date);
        }
        setContentDescription(contentDescription);

        if (date != 0) {
            subtitleView.setVisibility(View.VISIBLE);
            MessageSeenCheckDrawable drawable = dateIsSeen ? seenDrawable : reactDrawable;
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(drawable.getSpanned(getContext(), resourcesProvider));
            ssb.append(LocaleController.formatSeenDate(date));
            subtitleView.setText(ssb);
            subtitleView.setTranslationY(!dateIsSeen ? AndroidUtilities.dp(-1) : 0);
            titleView.setTranslationY(0);
        } else {
            subtitleView.setVisibility(View.GONE);
            titleView.setTranslationY(AndroidUtilities.dp(9));
        }

        titleView.setRightPadding(AndroidUtilities.dp(hasReactImage ? 30 : 0));
        titleView.setTranslationX(hasReactImage && LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        ((MarginLayoutParams) subtitleView.getLayoutParams()).rightMargin = AndroidUtilities.dp(hasReactImage && !LocaleController.isRTL ? 12 + 24 : 12);
        subtitleView.setTranslationX(hasReactImage && LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
    }

    public void setUserReaction(TLRPC.MessagePeerReaction reaction) {
        if (reaction == null) {
            return;
        }
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        long dialogId = MessageObject.getPeerId(reaction.peer_id);
        if (dialogId > 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        }
        setUserReaction(user, chat, reaction.reaction, reaction.date, reaction.dateIsSeen);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(ITEM_HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        statusBadgeComponent.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        statusBadgeComponent.onDetachedFromWindow();
    }

    public void setObject(TLRPC.User user, long date, boolean b) {

    }

    private float alphaInternal = 1f;
    private ValueAnimator alphaAnimator;
    public void animateAlpha(float alpha, boolean animated) {
        if (alphaAnimator != null) {
            alphaAnimator.cancel();
            alphaAnimator = null;
        }
        if (animated) {
            alphaAnimator = ValueAnimator.ofFloat(alphaInternal, alpha);
            alphaAnimator.addUpdateListener(anm -> {
                alphaInternal = (float) anm.getAnimatedValue();
                invalidate();
            });
            alphaAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    alphaInternal = alpha;
                    invalidate();
                }
            });
            alphaAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            alphaAnimator.setDuration(420);
            alphaAnimator.start();
        } else {
            alphaInternal = alpha;
            invalidate();
        }
    }

    public float getAlphaInternal() {
        return alphaInternal;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean restore = false;
        if (alphaInternal < 1) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alphaInternal), Canvas.ALL_SAVE_FLAG);
            restore = true;
        }
        super.dispatchDraw(canvas);
        if (drawDivider) {
            float leftMargin = AndroidUtilities.dp(55);
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - leftMargin, getMeasuredHeight() - 1, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
            } else {
                canvas.drawLine(leftMargin, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1,  Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
            }
        }
        if (restore) {
            canvas.restore();
        }
    }

    public Theme.ResourcesProvider getResourcesProvider() {
        return resourcesProvider;
    }
}