package org.telegram.ui.Gifts;

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsController.findAttribute;
import static org.telegram.ui.Stars.StarsIntroActivity.StarsTransactionView.getPlatformDrawable;
import static org.telegram.messenger.AndroidUtilities.percents;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.StarsFormat;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BatchParticlesDrawHelper;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CompatDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EffectsTextView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Particles;
import org.telegram.ui.Components.Premium.GiftPremiumBottomSheet;
import org.telegram.ui.Components.Premium.PremiumLockIconView;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Shaker;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.utils.NinePatchBuilder;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Components.StarGiftPatterns;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GiftSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private UniversalAdapter adapter;
    private List<TLRPC.TL_premiumGiftCodeOption> options;
    private final Utilities.Callback<Boolean> closeParentSheet;
    private TLRPC.DisallowedGiftsSettings userSettings;

    private final long dialogId;
    private final boolean self;
    private final String name;

    private final StarsIntroActivity.StarsBalanceView balanceView;
    private final FrameLayout topView;
    private final FrameLayout premiumHeaderView;
    private final LinearLayout starsHeaderView;
    private final ExtendedGridLayoutManager layoutManager;
    private final DefaultItemAnimator itemAnimator;

    private final LinkSpanDrawable.LinksTextView subtitleStarsView;
    private final LinkSpanDrawable.LinksTextView subtitleCollectiblesStarsView;

    private final ArrayList<GiftPremiumBottomSheet.GiftTier> premiumTiers = new ArrayList<>();

    private final StarsController.GiftsList myGifts;
    private int TAB_ALL = -1;
    private int TAB_MY_GIFTS = -1;
    private int TAB_LIMITED = -1;
    private int TAB_IN_STOCK = -1;
    private int TAB_RESALE = -1;
    private int TAB_COLLECTIBLES = -1;
    private final ArrayList<CharSequence> tabs = new ArrayList<>();
    private int selectedTab;

    private boolean birthday;

    public GiftSheet(Context context, int currentAccount, long userId, Utilities.Callback<Boolean> closeParentSheet) {
        this(context, currentAccount, userId, null, closeParentSheet);
    }

    public GiftSheet(Context context, int currentAccount, long dialogId, List<TLRPC.TL_premiumGiftCodeOption> options, Utilities.Callback<Boolean> closeParentSheet) {
        super(context, null, false, false, false, null);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.self = UserConfig.getInstance(currentAccount).getClientUserId() == dialogId;
        this.options = options;
        this.closeParentSheet = closeParentSheet;
        setBackgroundColor(Theme.getColor(Theme.key_dialogGiftsBackground));
        fixNavigationBar(Theme.getColor(Theme.key_dialogGiftsBackground));
        myGifts = StarsController.getInstance(currentAccount).getProfileGiftsList(UserConfig.getInstance(currentAccount).getClientUserId());

        StarsController.getInstance(currentAccount).loadStarGifts();

        final BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        final AvatarDrawable avatarDrawable = new AvatarDrawable();

        if (dialogId > 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            this.name = UserObject.getForcedFirstName(user);
            avatarDrawable.setInfo(user);
            avatarImageView.setForUserOrChat(user, avatarDrawable);

            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            userSettings = dialogId != UserConfig.getInstance(currentAccount).getClientUserId() && userFull != null ? userFull.disallowed_stargifts : null;
            if (userFull == null) {
                MessagesController.getInstance(currentAccount).loadFullUser(user, 0, true);
            }
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            this.name = chat == null ? "" : chat.title;
            avatarDrawable.setInfo(chat);
            avatarImageView.setForUserOrChat(chat, avatarDrawable);
        }
        topPadding = 0.10f;

        balanceView = new StarsIntroActivity.StarsBalanceView(context, currentAccount, resourcesProvider);
        ScaleStateListAnimator.apply(balanceView);
        balanceView.setOnClickListener(v -> {
            if (balanceView.lastBalance <= 0) return;
            final BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null) {
                final BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                bottomSheetParams.transitionFromLeft = true;
                bottomSheetParams.allowNestedScroll = false;
                lastFragment.showAsSheet(new StarsIntroActivity(), bottomSheetParams);
            }
        });

        // Gift Premium header
        premiumHeaderView = new FrameLayout(context);

        topView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(120), MeasureSpec.EXACTLY)
                );
            }
        };
        topView.setClipChildren(false);
        topView.setClipToPadding(false);

        final StarParticlesView particlesView = StarsIntroActivity.makeParticlesView(context, 70, 0);
        topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        avatarImageView.setRoundRadius(dp(42));
        topView.addView(avatarImageView, LayoutHelper.createFrame(84, 84, Gravity.CENTER, 0, 15, 0, 17));
        ScaleStateListAnimator.apply(avatarImageView);
        avatarImageView.setOnClickListener(v -> {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            dismiss();
            lastFragment.presentFragment(ProfileActivity.of(dialogId));
        });
        topView.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, -3, -10, 0));

        final LinearLayout bottomView = new LinearLayout(context);
        bottomView.setOrientation(LinearLayout.VERTICAL);

        premiumHeaderView.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setGravity(Gravity.CENTER);
        bottomView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));
        titleView.setMaxWidth(HintView2.cutInFancyHalf(titleView.getText(), titleView.getPaint()));

        final LinkSpanDrawable.LinksTextView subtitleView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLineSpacing(dp(2.33f), 1.0f);
        bottomView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 4, 4, 12));

        titleView.setText(getString(R.string.Gift2Premium));
        subtitleView.setText(TextUtils.concat(
            AndroidUtilities.replaceTags(formatString(R.string.Gift2PremiumInfo, name)),
            " ",
            AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(getString(R.string.Gift2PremiumInfoLink), () -> {
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment == null) {
                    return;
                }
                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                params.transitionFromLeft = true;
                params.allowNestedScroll = false;
                lastFragment.showAsSheet(new PremiumPreviewFragment("gifts"), params);
            }), true)
        ));
        subtitleView.setMaxWidth(HintView2.cutInFancyHalf(subtitleView.getText(), subtitleView.getPaint()));

        // Gift Stars header
        starsHeaderView = new LinearLayout(context);
        starsHeaderView.setOrientation(LinearLayout.VERTICAL);

        final TextView titleStarsView = new TextView(context);
        titleStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleStarsView.setTypeface(AndroidUtilities.bold());
        titleStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleStarsView.setGravity(Gravity.CENTER);
        starsHeaderView.addView(titleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));

        subtitleStarsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (getAlpha() < 0.95f) return false;
                return super.dispatchTouchEvent(event);
            }
        };
        subtitleStarsView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleStarsView.setGravity(Gravity.CENTER);

        subtitleCollectiblesStarsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (getAlpha() < 0.95f) return false;
                return super.dispatchTouchEvent(event);
            }
        };
        subtitleCollectiblesStarsView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleCollectiblesStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleCollectiblesStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleCollectiblesStarsView.setGravity(Gravity.CENTER);
        subtitleCollectiblesStarsView.setAlpha(0.0f);
        subtitleCollectiblesStarsView.setScaleX(0.85f);
        subtitleCollectiblesStarsView.setScaleY(0.85f);

        FrameLayout subtitleLayout = new FrameLayout(context);
        subtitleLayout.addView(subtitleStarsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 26, 0, 26, 0));
        subtitleLayout.addView(subtitleCollectiblesStarsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 26, 0, 26, 0));

        titleStarsView.setText(getString(dialogId < 0 ? R.string.Gift2StarsChannel : self ? R.string.Gift2StarsSelf : R.string.Gift2Stars));
        if (self) {
            starsHeaderView.addView(subtitleLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 4));

            final LinkSpanDrawable.LinksTextView subtitleStarsView2 = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            subtitleStarsView2.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            subtitleStarsView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleStarsView2.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            subtitleStarsView2.setGravity(Gravity.CENTER);
            starsHeaderView.addView(subtitleStarsView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 26, 4, 26, 6));

            subtitleStarsView.setText(getString(R.string.Gift2StarsSelfInfo1));
            subtitleStarsView2.setText(getString(R.string.Gift2StarsSelfInfo2));
        } else if (dialogId < 0) {
            starsHeaderView.addView(subtitleLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 4));
            NotificationCenter.listenEmojiLoading(subtitleStarsView);
            subtitleStarsView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(formatString(R.string.Gift2StarsChannelInfo, name)), subtitleStarsView.getPaint().getFontMetricsInt(), false));
        } else {
            starsHeaderView.addView(subtitleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 6));

            final StarsController.GiftsList list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
            final Runnable setSubtitle = () -> {
                for (int a = 0; a < 2; ++a) {
                    final SpannableStringBuilder subtitle = new SpannableStringBuilder();
                    subtitle.append(AndroidUtilities.replaceTags(a == 1 ? getString(R.string.Gift2StarsCollectibleInfo) : formatString(R.string.Gift2StarsInfo, name)));
                    subtitle.append(" ");
                    final HashSet<Long> emojiDocumentIds = new HashSet<>();
                    final HashSet<TLRPC.Document> emojiDocuments = new HashSet<>();
                    for (int i = 0; i < list.gifts.size() && emojiDocumentIds.size() < 3; ++i) {
                        final TL_stars.SavedStarGift savedStarGift = list.gifts.get(i);
                        if (savedStarGift != null && savedStarGift.gift != null) {
                            final TLRPC.Document document = savedStarGift.gift.getDocument();
                            if (document != null && !emojiDocumentIds.contains(document.id)) {
                                emojiDocuments.add(document);
                                emojiDocumentIds.add(document.id);
                            }
                        }
                    }
                    if (emojiDocuments.size() > 0) {
                        final SpannableStringBuilder link = new SpannableStringBuilder();
                        link.append(formatString(R.string.Gift2StarsInfoProfileLink, DialogObject.getShortName(dialogId)).replaceAll(" ", " "));
                        link.append(" ");
                        for (final TLRPC.Document document : emojiDocuments) {
                            link.append("\u2060e");
                            link.setSpan(new AnimatedEmojiSpan(document, subtitleStarsView.getPaint().getFontMetricsInt()), link.length() - 1, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        link.append(" >");
                        subtitle.append(AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(link, () -> {
                            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            dismiss();
                            if (closeParentSheet != null) {
                                closeParentSheet.run(false);
                            }
                            final Bundle args = new Bundle();
                            args.putLong("user_id", dialogId);
                            args.putBoolean("open_gifts", true);
                            lastFragment.presentFragment(new ProfileActivity(args));
                        }), true));
                    } else {
                        subtitle.append(AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(getString(R.string.Gift2StarsInfoLink), () -> {
                            new ExplainStarsSheet(context).show();
                        }), true));
                    }

                    final LinkSpanDrawable.LinksTextView textView = a == 0 ? subtitleStarsView : subtitleCollectiblesStarsView;
                    textView.setText(subtitle);
                    textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
                }
            };
            setSubtitle.run();
            subtitleStarsView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    setSubtitle.run();
                }
                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {}
            });

            if (list.gifts.size() < 3) {
                list.load();
            }
            NotificationCenter.getInstance(currentAccount).listen(subtitleStarsView, NotificationCenter.starUserGiftsLoaded, args -> {
                if (args[1] == list) {
                    setSubtitle.run();
                }
            });
        }

        layoutManager = new ExtendedGridLayoutManager(context, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter == null || position == 0)
                    return layoutManager.getSpanCount();
                final UItem item = adapter.getItem(position - 1);
                if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                    return layoutManager.getSpanCount();
                return item.spanCount;
            }
        });
        recyclerListView.setPadding(dp(16), 0, dp(16), 0);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setClipChildren(false);
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setSelectorType(9);
        recyclerListView.setSelectorDrawableColor(0);
        itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return .3f;
            }
        };
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayIncrement(40);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(position - 1);
            if (item == null) return;

            if (item.instanceOf(GiftViews.GiftCell.Factory.class)) {
                if (item.object instanceof GiftPremiumBottomSheet.GiftTier) {
                    final GiftPremiumBottomSheet.GiftTier premiumTier = (GiftPremiumBottomSheet.GiftTier) item.object;
                    new SendGiftSheet(context, currentAccount, premiumTier, this.dialogId, () -> {
                        if (closeParentSheet != null) {
                            closeParentSheet.run(false);
                        }
                        dismiss();
                    }) {
                        @Override
                        protected BulletinFactory getParentBulletinFactory() {
                            return BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider);
                        }
                    }.show();
                    return;
                } else if (item.object instanceof TL_stars.StarGift) {
                    final TL_stars.StarGift gift = (TL_stars.StarGift) item.object;
                    if (myGifts != null && selectedTab == TAB_MY_GIFTS) {
                        TL_stars.SavedStarGift savedStarGift = null;
                        for (TL_stars.SavedStarGift g : myGifts.gifts) {
                            if (g.gift.id == gift.id) {
                                savedStarGift = g;
                                break;
                            }
                        }
                        if (savedStarGift == null) {
                            return;
                        }
                        StarGiftSheet sheet = new StarGiftSheet(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), resourcesProvider) {
                            @Override
                            public BulletinFactory getBulletinFactory() {
                                return BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider);
                            }
                        }.set(savedStarGift, null);
                        sheet.openTransferAlert(dialogId, progress -> {
                            progress.init();
                            sheet.doTransfer(dialogId, err -> {
                                progress.end();
                                if (closeParentSheet != null) {
                                    closeParentSheet.run(false);
                                }
                                GiftSheet.this.dismiss();
                                if (err != null) {
                                    AndroidUtilities.runOnUIThread(() -> sheet.getBulletinFactory().showForError(err));
                                    return;
                                }
                                dismiss();
                            });
                        });
                        return;
                    }
                    if (item.accent && gift.availability_resale > 0) {
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        final BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                        bottomSheetParams.transitionFromLeft = true;
                        bottomSheetParams.allowNestedScroll = false;
                        bottomSheetParams.occupyNavigationBar = true;

                        ViewTreeObserver observer = container.getViewTreeObserver();
                        ViewTreeObserver.OnPreDrawListener onPreDrawListener = () -> false;

                        final ResaleGiftsFragment fragment = new ResaleGiftsFragment(dialogId, gift.title, gift.id, resourcesProvider) {
                            @Override
                            public void onPause() {
                                super.onPause();
                                observer.removeOnPreDrawListener(onPreDrawListener);
                            }

                            @Override
                            public void onResume() {
                                super.onResume();
                                observer.addOnPreDrawListener(onPreDrawListener);
                            }
                        };
                        fragment.setCloseParentSheet((fragmentsImmediately) -> {
                            if (closeParentSheet != null) {
                                closeParentSheet.run(fragmentsImmediately);
                            }
                            if (fragmentsImmediately) {
                                skipDismissAnimation();
                            }
                            dismiss();
                        });
                        lastFragment.showAsSheet(fragment, bottomSheetParams);
                        return;
                    }
                    if (gift.auction) {
                        AuctionJoinSheet.show(context, resourcesProvider, currentAccount, dialogId, gift.id, () -> {
                            if (closeParentSheet != null) {
                                closeParentSheet.run(false);
                            }
                            dismiss();
                        });
                        return;
                    }

                    if (gift.sold_out) {
                        StarsIntroActivity.showSoldOutGiftSheet(context, currentAccount, gift, resourcesProvider);
                        return;
                    }
                    if (gift.limited_per_user && gift.per_user_remains <= 0) {
                        BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider)
                            .createSimpleMultiBulletin(gift.getDocument(), AndroidUtilities.replaceTags(formatPluralStringComma("Gift2PerUserLimit", gift.per_user_total)))
                            .show();
                        return;
                    }
                    final Runnable openSendSheet = () -> {
                        new SendGiftSheet(context, currentAccount, gift, this.dialogId, () -> {
                            if (closeParentSheet != null) {
                                closeParentSheet.run(false);
                            }
                            dismiss();
                        }, gift.limited && userSettings != null && userSettings.disallow_limited_stargifts, gift.limited && userSettings != null && userSettings.disallow_unique_stargifts) {
                            @Override
                            protected BulletinFactory getParentBulletinFactory() {
                                return BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider);
                            }
                        }.show();
                    };

                    if (gift.locked_until_date > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(500);

                        final TL_stars.checkCanSendGift req = new TL_stars.checkCanSendGift();
                        req.gift_id = gift.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();

                            if (res instanceof TL_stars.checkCanSendGiftResultOk) {
                                openSendSheet.run();
                            } else if (res instanceof TL_stars.checkCanSendGiftResultFail) {
                                final TL_stars.checkCanSendGiftResultFail fail = (TL_stars.checkCanSendGiftResultFail) res;
                                final AlertDialog dialog = new AlertDialog.Builder(getContext(), resourcesProvider)
                                    .setTitle(getString(R.string.GiftLocked))
                                    .setMessage(MessageObject.formatTextWithEntities(fail.reason, false))
                                    .setPositiveButton(getString(R.string.OK), null)
                                    .show();
                                final TextView textView = dialog.getMessageTextView();
                                if (textView instanceof EffectsTextView) {
                                    ((EffectsTextView) textView).setOnLinkPressListener(link -> {
                                        dialog.dismiss();
                                        if (closeParentSheet != null) {
                                            closeParentSheet.run(false);
                                        }
                                        dismiss();
                                        link.onClick(textView);
                                    });
                                }
                            } else if (err != null) {
                                BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider)
                                    .showForError(err);
                            }
                        }));
                        return;
                    }
                    if (gift.require_premium && !UserConfig.getInstance(currentAccount).isPremium()) {
                        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                        if (fragment == null) return;
                        PremiumPreviewBottomSheet sheet = new PremiumPreviewBottomSheet(fragment, currentAccount, null, null, gift, resourcesProvider);
                        BackupImageView icon = new BackupImageView(getContext());
                        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(icon, dp(160), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE);
                        icon.setImageDrawable(drawable);
                        icon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(@NonNull View v) {
                                drawable.attach();
                            }
                            @Override
                            public void onViewDetachedFromWindow(@NonNull View v) {
                                drawable.detach();
                            }
                        });
                        drawable.set(gift.getDocument(), false);
                        sheet.overrideTitleIcon = icon;
                        sheet.show();
                        drawable.play();
                        return;
                    }

                    openSendSheet.run();
                }
            }
        });

        updatePremiumTiers();
        adapter.update(false);
        updateTitle();

        if (BirthdayController.getInstance(currentAccount).isToday(dialogId)) {
            setBirthday();
        }

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftSoldOut);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);

        actionBar.setTitle(getTitle());
        NotificationCenter.listenEmojiLoading(actionBar.getTitleTextView());
    }

    private boolean shownCollectiblesInfo;
    public void setShowCollectiblesInfo(boolean show) {
        if (show == shownCollectiblesInfo) return;

        shownCollectiblesInfo = show;
        subtitleStarsView.animate()
            .alpha(!show ? 1.0f : 0.0f)
            .scaleX(!show ? 1.0f : 0.85f)
            .scaleY(!show ? 1.0f : 0.85f)
            .setDuration(380)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .start();
        subtitleCollectiblesStarsView.animate()
            .alpha(show ? 1.0f : 0.0f)
            .scaleX(show ? 1.0f : 0.85f)
            .scaleY(show ? 1.0f : 0.85f)
            .setDuration(380)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .start();
    }

    @Override
    public void show() {
        // LoogriGram: sending gifts is an upsell surface and is removed. Guarded
        // here rather than at each of the fourteen call sites - birthday
        // prompts, profile buttons, the gifts tab, deep links - because this is
        // the one place all of them pass through, and upstream already refuses
        // here for a frozen account or a recipient who disallows gifts.
        //
        // Receiving is untouched: a gift someone sends still arrives, renders in
        // the chat and appears on the profile. That has to keep working -
        // ChatMessageCell draws it through StarGiftSheet and MessageObject
        // formats the text through StarsIntroActivity.
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.GiftsSendingRemoved))
                .show();
        }
        if (true) {
            return;
        }
        if (MessagesController.getInstance(currentAccount).isFrozen()) {
            AccountFrozenAlert.show(currentAccount);
            return;
        }
        if (userSettings != null && userSettings.disallow_premium_gifts && userSettings.disallow_unique_stargifts && userSettings.disallow_limited_stargifts && userSettings.disallow_unlimited_stargifts) {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(dialogId)))).show();
            }
            return;
        }
        super.show();
    }

    public GiftSheet setBirthday() {
        return setBirthday(true);
    }

    public GiftSheet setBirthday(boolean b) {
        this.birthday = b;
        adapter.update(false);
        return this;
    }

    private void onGiftSuccess(boolean fromGooglePlay) {
        TLRPC.UserFull full = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
        final TLObject user = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
        if (full != null) {
            if (user instanceof TLRPC.User) {
                ((TLRPC.User) user).premium = true;
                MessagesController.getInstance(currentAccount).putUser((TLRPC.User) user, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, ((TLRPC.User) user).id, full);
            }
        }

        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null && lastFragment.getParentActivity() instanceof LaunchActivity) {
            List<BaseFragment> fragments = new ArrayList<>(((LaunchActivity) lastFragment.getParentActivity()).getActionBarLayout().getFragmentStack());

            INavigationLayout layout = lastFragment.getParentLayout();
            ChatActivity lastChatActivity = null;
            for (BaseFragment fragment : fragments) {
                if (fragment instanceof ChatActivity) {
                    lastChatActivity = (ChatActivity) fragment;
                    if (lastChatActivity.getDialogId() != dialogId) {
                        fragment.removeSelfFromStack();
                    }
                } else if (fragment instanceof ProfileActivity) {
                    if (fromGooglePlay && layout.getLastFragment() == fragment) {
                        fragment.finishFragment();
                    } else {
                        fragment.removeSelfFromStack();
                    }
                }
            }
            if (lastChatActivity == null || lastChatActivity.getDialogId() != dialogId) {
                AndroidUtilities.runOnUIThread(() -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", dialogId);
                    layout.presentFragment(new ChatActivity(args), true);
                }, 200);
            }
        }

        dismiss();
        if (closeParentSheet != null) {
            closeParentSheet.run(false);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftSoldOut);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.billingProductDetailsUpdated) {
            updatePremiumTiers();
        } else if (id == NotificationCenter.starGiftsLoaded) {
            if (adapter != null) {
                adapter.update(true);
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            if (!isShown()) return;
            if ((long) args[0] == dialogId) {
                if (dialogId > 0) {
                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                    userSettings = dialogId != UserConfig.getInstance(currentAccount).getClientUserId() && userFull != null ? userFull.disallowed_stargifts : null;
                    if (userSettings != null && userSettings.disallow_premium_gifts && userSettings.disallow_unique_stargifts && userSettings.disallow_limited_stargifts && userSettings.disallow_unlimited_stargifts) {
                        dismiss();
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(dialogId)))).show();
                        }
                        return;
                    }
                    if (adapter != null) {
                        adapter.update(true);
                    }
                }
            }
            if (premiumTiers == null || premiumTiers.isEmpty()) {
                updatePremiumTiers();
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        } else if (id == NotificationCenter.starGiftSoldOut) {
            if (!isShown()) return;
            final TL_stars.StarGift gift = (TL_stars.StarGift) args[0];
            BulletinFactory.of(container, resourcesProvider)
                .createEmojiBulletin(gift.sticker, getString(R.string.Gift2SoldOutTitle), AndroidUtilities.replaceTags(formatPluralStringComma("Gift2SoldOutCount", gift.availability_total)))
                .show();
            if (adapter != null) {
                adapter.update(true);
            }
        } else if (id == NotificationCenter.starUserGiftsLoaded) {
            if (args[1] == myGifts) {
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        }
    }

    private void updatePremiumTiers() {
        premiumTiers.clear();
        if (premiumTiers.isEmpty() && options != null && !options.isEmpty()) {
            long pricePerMonthMax = 0;
            for (int i = options.size() - 1; i >= 0; i--) {
                final TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                if ("XTR".equalsIgnoreCase(option.currency)) continue;
                Object starsOption = null;
                for (TLRPC.TL_premiumGiftCodeOption o : options) {
                    if (o != option && "XTR".equalsIgnoreCase(o.currency) && o.months == option.months) {
                        starsOption = o;
                        break;
                    }
                }
                final GiftPremiumBottomSheet.GiftTier giftTier = new GiftPremiumBottomSheet.GiftTier(option, starsOption);
                premiumTiers.add(giftTier);
                // LoogriGram: prices come from the server's gift options, never
                // from a Play product query.
                if (giftTier.getPricePerMonth() > pricePerMonthMax) {
                    pricePerMonthMax = giftTier.getPricePerMonth();
                }
            }
            for (GiftPremiumBottomSheet.GiftTier tier : premiumTiers) {
                tier.setPricePerMonthRegular(pricePerMonthMax);
            }
        }
        if (premiumTiers.isEmpty()) {
            BoostRepository.loadGiftOptions(currentAccount, null, paymentOptions -> {
                if (getContext() == null || !isShown()) return;
                options = BoostRepository.filterGiftOptions(paymentOptions, 1);
                options = BoostRepository.filterGiftOptionsByBilling(options);
                if (!options.isEmpty()) {
                    updatePremiumTiers();
                    if (adapter != null) {
                        adapter.update(true);
                    }
                }
            });
        }
    }

    @Override
    protected CharSequence getTitle() {
        if (self) {
            return getString(R.string.Gift2TitleSelf1);
        }
        return Emoji.replaceEmoji(formatString(R.string.Gift2User, name), null, false);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean pushedTopView = false;
        if (!self && dialogId >= 0 && !(userSettings != null && userSettings.disallow_premium_gifts)) {
            items.add(UItem.asCustom(topView));
            pushedTopView = true;
            items.add(UItem.asCustom(premiumHeaderView));
            if (premiumTiers != null && !premiumTiers.isEmpty()) {
                for (GiftPremiumBottomSheet.GiftTier tier : premiumTiers) {
                    items.add(GiftViews.GiftCell.Factory.asPremiumGift(tier));
                }
            } else {
                items.add(UItem.asFlicker(1, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(2, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(3, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
        }

        final StarsController s = StarsController.getInstance(currentAccount);
        ArrayList<TL_stars.StarGift> gifts;
        if (birthday) {
            gifts = s.birthdaySortedGifts;
        } else {
            gifts = s.sortedGifts;
        }
        if (userSettings != null) {
            gifts = gifts.stream().filter(gift -> {
                if (gift instanceof TL_stars.TL_starGiftUnique) {
                    return !userSettings.disallow_unique_stargifts;
                } else if (gift.limited) {
                    return !userSettings.disallow_limited_stargifts || gift.can_upgrade && !userSettings.disallow_unique_stargifts;
                } else {
                    return !userSettings.disallow_unlimited_stargifts;
                }
            }).collect(Collectors.toCollection(ArrayList::new));
        }

        if (dialogId < 0) {
            gifts = gifts.stream().filter(gift -> !gift.auction).collect(Collectors.toCollection(ArrayList::new));
        }

        boolean myGiftsHaveUnique = false;
        if (dialogId != UserConfig.getInstance(currentAccount).getClientUserId()) {
            if (myGifts != null) {
                for (TL_stars.SavedStarGift savedStarGift : myGifts.gifts) {
                    if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                        myGiftsHaveUnique = true;
                        break;
                    }
                }
            }
        }
        if (!MessagesController.getInstance(currentAccount).stargiftsBlocked && (!gifts.isEmpty() || userSettings != null && !userSettings.disallow_unique_stargifts && myGifts != null && !myGifts.gifts.isEmpty())) {
            if (!pushedTopView) {
                items.add(UItem.asCustom(topView));
            } else {
                items.add(UItem.asSpace(dp(16)));
            }
            items.add(UItem.asCustom(starsHeaderView));
            boolean hasResale = false;
            final TreeSet<Long> prices = new TreeSet<>();
            if (userSettings == null || !userSettings.disallow_unique_stargifts) {
                for (int i = 0; i < gifts.size(); ++i) {
                    final TL_stars.StarGift gift = gifts.get(i);
                    prices.add(gift.stars);
                    if (gift.availability_resale > 0) {
                        hasResale = true;
                    }
                }
            }

            final ArrayList<CharSequence> tabs = new ArrayList<>();
            TAB_ALL = TAB_IN_STOCK = TAB_LIMITED = TAB_MY_GIFTS = -1;
            if (!gifts.isEmpty()) {
                TAB_ALL = tabs.size();
                tabs.add(getString(R.string.Gift2TabAll));
            }
            if ((userSettings == null || !userSettings.disallow_unique_stargifts) && myGiftsHaveUnique) {
                TAB_MY_GIFTS = tabs.size();
                tabs.add(getString(R.string.Gift2TabMine));
            }
            TAB_COLLECTIBLES = tabs.size();
            tabs.add(getString(R.string.Gift2TabCollectibles));

            items.add(GiftViews.Tabs.Factory.asTabs(1, tabs, selectedTab, this::selectTab));
            setShowCollectiblesInfo(selectedTab == TAB_COLLECTIBLES && !self && dialogId >= 0);

            final ArrayList<TL_stars.StarGift> finalGifts;
            if (myGifts != null && selectedTab == TAB_MY_GIFTS) {
                finalGifts = new ArrayList<>();
                for (TL_stars.SavedStarGift savedStarGift : myGifts.gifts) {
                    if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                        finalGifts.add(savedStarGift.gift);
                    }
                }
            } else {
                finalGifts = gifts;
            }
            int giftsCount = 0;
            for (int i = 0; i < finalGifts.size(); ++i) {
                final TL_stars.StarGift gift = finalGifts.get(i);
                if (
                    selectedTab == TAB_ALL ||
                    selectedTab == TAB_MY_GIFTS ||
                    selectedTab == TAB_COLLECTIBLES && (gift.availability_resale > 0 || gift.require_premium || gift.locked_until_date != 0)
                ) {
                    if (!gift.sold_out && gift.availability_resale > 0 && selectedTab != TAB_COLLECTIBLES) {
                        items.add(GiftViews.GiftCell.Factory.asStarGift(selectedTab, gift, selectedTab == TAB_MY_GIFTS, gift.limited && userSettings != null && userSettings.disallow_limited_stargifts, false, false, false));
                        giftsCount++;
                    }
                    items.add(GiftViews.GiftCell.Factory.asStarGift(selectedTab, gift, selectedTab == TAB_MY_GIFTS, gift.limited && userSettings != null && userSettings.disallow_limited_stargifts, true, false, false));
                    giftsCount++;
                }
            }
            if (selectedTab == TAB_MY_GIFTS && myGifts != null && !myGifts.endReached) {
                myGifts.load();
                items.add(UItem.asFlicker(4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            } else if (selectedTab != TAB_MY_GIFTS && s.giftsLoading) {
                items.add(UItem.asFlicker(4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
            items.add(UItem.asSpace(dp(giftsCount < 9 ? 300 : 40)));
        } else if (userSettings != null && !userSettings.disallow_unique_stargifts && gifts.isEmpty()) {
            items.add(UItem.asSpace(dp(300)));
        }
    }

    private void selectTab(int tab) {
        if (selectedTab == tab) return;
        selectedTab = tab;
        itemAnimator.endAnimations();
        adapter.update(true);
    }

    // LoogriGram: GiftCell, Tabs, CardBackground, Ribbon and the star
    // backgrounds were nested here and are now in GiftViews - they draw
    // gifts people were given, which this fork keeps.
}
