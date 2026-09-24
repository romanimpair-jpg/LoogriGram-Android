package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AdminedChannelCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LimitReachedBottomSheet extends BottomSheetWithRecyclerListView {
    public static final int TYPE_PIN_DIALOGS = 0;
    public static final int TYPE_PUBLIC_LINKS = 2;
    public static final int TYPE_FOLDERS = 3;
    public static final int TYPE_CHATS_IN_FOLDER = 4;
    public static final int TYPE_TO0_MANY_COMMUNITIES = 5;
    public static final int TYPE_LARGE_FILE = 6;
    public static final int TYPE_ACCOUNTS = 7;

    public static final int TYPE_CAPTION = 8;
    public static final int TYPE_GIFS = 9;
    public static final int TYPE_STICKERS = 10;

    public static final int TYPE_ADD_MEMBERS_RESTRICTED = 11;
    public static final int TYPE_CALL_RESTRICTED = 34;
    public static final int TYPE_FOLDER_INVITES = 12;
    public static final int TYPE_SHARED_FOLDERS = 13;

    public static final int TYPE_STORIES_COUNT = 14;
    public static final int TYPE_STORIES_WEEK = 15;
    public static final int TYPE_STORIES_MONTH = 16;
    // LoogriGram: 17 to 32 and 35 were the boost types - "boost this channel"
    // (TYPE_BOOSTS_FOR_USERS), the level each boost-locked option needed
    // (posting stories, colours, wallpapers, reactions, emoji status, wearing
    // a collectible, reply and profile icons, an emoji pack, no ads,
    // autotranslation), a group's boost-to-lift-restrictions and the list of
    // what each level unlocks (TYPE_FEATURES) - with the boost button, the
    // level bar, the link to copy, the giveaway footer, boost reassignment
    // and fireworks. Boosts come from Premium, honoured for nobody; nothing
    // opens this sheet for them any more. 33 and 34 keep their numbers.
    public static final int TYPE_PIN_SAVED_DIALOGS = 33;

    protected int storiesCount;
    private boolean canSendLink;

    public static String limitTypeToServerString(int type) {
        switch (type) {
            case TYPE_PIN_DIALOGS:
                return "double_limits__dialog_pinned";
            case TYPE_TO0_MANY_COMMUNITIES:
                return "double_limits__channels";
            case TYPE_PUBLIC_LINKS:
                return "double_limits__channels_public";
            case TYPE_FOLDERS:
                return "double_limits__dialog_filters";
            case TYPE_CHATS_IN_FOLDER:
                return "double_limits__dialog_filters_chats";
            case TYPE_LARGE_FILE:
                return "double_limits__upload_max_fileparts";
            case TYPE_CAPTION:
                return "double_limits__caption_length";
            case TYPE_GIFS:
                return "double_limits__saved_gifs";
            case TYPE_STICKERS:
                return "double_limits__stickers_faved";
            case TYPE_FOLDER_INVITES:
                return "double_limits__chatlist_invites";
            case TYPE_SHARED_FOLDERS:
                return "double_limits__chatlists_joined";
        }
        return null;
    }

    final int type;
    ArrayList<TLRPC.Chat> chats = new ArrayList<>();
    boolean premiumButtonSetSubscribe;

    int rowCount;
    int headerRow = -1;
    int dividerRow = -1;
    int chatsTitleRow = -1;
    int chatStartRow = -1;
    int chatEndRow = -1;
    int loadingRow = -1;
    int emptyViewDividerRow = -1;

    public boolean parentIsChannel;
    private int currentValue = -1;
    LimitPreviewView limitPreviewView;
    HashSet<Object> selectedChats = new HashSet<>();

    private ArrayList<TLRPC.Chat> inactiveChats = new ArrayList<>();
    private ArrayList<String> inactiveChatsSignatures = new ArrayList<>();
    private ArrayList<TLRPC.User> restrictedUsers = new ArrayList<>();
    private ArrayList<Long> premiumMessagingBlockedUsers = new ArrayList<>();
    private ArrayList<Long> premiumInviteBlockedUsers = new ArrayList<>();

    PremiumButtonView premiumButtonView;
    public Runnable onSuccessRunnable;
    public Runnable onShowPremiumScreenRunnable;
    private boolean loading = false;
    RecyclerItemsEnterAnimator enterAnimator;
    BaseFragment parentFragment;
    View divider;
    LimitParams limitParams;
    private boolean isVeryLargeFile;
    private TLRPC.Chat fromChat;

    public LimitReachedBottomSheet(BaseFragment fragment, Context context, int type, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context, fragment, false, hasFixedSize(type), false, resourcesProvider);
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, this.resourcesProvider));
        this.parentFragment = fragment;
        this.currentAccount = currentAccount;
        this.type = type;
        updateTitle();
        updateRows();
        if (type == TYPE_PUBLIC_LINKS) {
            loadAdminedChannels();
        } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
            loadInactiveChannels();
        }
        updatePremiumButtonText();
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);
        Context context = containerView.getContext();

        premiumButtonView = new PremiumButtonView(context, true, resourcesProvider);
        ScaleStateListAnimator.apply(premiumButtonView, .02f, 1.2f);

        if (!hasFixedSize) {
            divider = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (chatEndRow - chatStartRow > 1) {
                        Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                        if (dividerPaint == null) {
                            dividerPaint = Theme.dividerPaint;
                        }
                        canvas.drawRect(0, 0, getMeasuredWidth(), 1, dividerPaint);
                    }
                }
            };
            divider.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            containerView.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72, Gravity.BOTTOM, 0, 0, 0, 0));
        }
        containerView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16 + backgroundPaddingLeft / AndroidUtilities.density, 0, 16 + backgroundPaddingLeft / AndroidUtilities.density, 12));
        recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(72));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setClipChildren(false);
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof AdminedChannelCell) {
                AdminedChannelCell adminedChannelCell = ((AdminedChannelCell) view);
                TLRPC.Chat chat = adminedChannelCell.getCurrentChannel();
                if (selectedChats.contains(chat)) {
                    selectedChats.remove(chat);
                } else {
                    selectedChats.add(chat);
                }
                adminedChannelCell.setChecked(selectedChats.contains(chat), true);
                updateButton();
            } else if (view instanceof GroupCreateUserCell) {
                if (!canSendLink && (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED)) {
                    return;
                }
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                Object object = cell.getObject();
                if (cell.isBlocked()) {
                    long dialogId;
                    if (object instanceof TLRPC.User) {
                        dialogId = ((TLRPC.User) object).id;
                    } else return;
                    showPremiumBlockedToast(cell, dialogId);
                    return;
                }
                if (selectedChats.contains(object)) {
                    selectedChats.remove(object);
                } else {
                    selectedChats.add(object);
                }
                cell.setChecked(selectedChats.contains(object), true);
                updateButton();
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position) -> {
            recyclerListView.getOnItemClickListener().onItemClick(view, position);
            try {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Exception ignored) {}
            return false;
        });
        premiumButtonView.buttonLayout.setOnClickListener(v -> {
            if (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
                return;
            }
            if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() || isVeryLargeFile) {
                dismiss();
                return;
            }
            if (parentFragment == null) {
                return;
            }
            if (parentFragment.getVisibleDialog() != null) {
                parentFragment.getVisibleDialog().dismiss();
            }
            parentFragment.presentFragment(new PremiumPreviewFragment(limitTypeToServerString(type)));
            if (onShowPremiumScreenRunnable != null) {
                onShowPremiumScreenRunnable.run();
            }
            dismiss();
        });
        premiumButtonView.overlayTextView.setOnClickListener(v -> {
            if (premiumButtonSetSubscribe) {
                if (parentFragment == null) return;
                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                params.transitionFromLeft = true;
                params.allowNestedScroll = false;
                parentFragment.showAsSheet(new PremiumPreviewFragment("invite_privacy"), params);
                return;
            }
            if (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
                if (selectedChats.isEmpty()) {
                    dismiss();
                    return;
                }
                sendInviteMessages();
                return;
            }
            if (selectedChats.isEmpty()) {
                return;
            }
            if (type == TYPE_PUBLIC_LINKS) {
                revokeSelectedLinks();
            } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                leaveFromSelectedGroups();
            }
        });
        enterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true);
    }

    private int shiftDp = -4;
    private void showPremiumBlockedToast(View view, long dialogId) {
        AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        // LoogriGram: no "Subscribe to Premium" button (premiumFeaturesBlocked
        // never offered it), and the text also covers paid messages.
        BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createSimpleBulletin(R.raw.star_premium_2, DialogObject.getLockedText(currentAccount, dialogId, R.string.UserBlockedNonPremium)).show();
    }

    private void sendInviteMessages() {
        String link = null;
        if (!TextUtils.isEmpty(forceLink)) {
            link = forceLink;
        } else {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(fromChat.id);
            if (chatFull == null) {
                dismiss();
                return;
            }
            if (fromChat.username != null) {
                link = "@" + fromChat.username;
            } else if (chatFull.exported_invite != null) {
                link = chatFull.exported_invite.link;
            } else {
                dismiss();
                return;
            }
        }
        // LoogriGram: chats that charge per message were sorted out here and a
        // pay-to-send confirmation shown for them first. Nothing is paid; each
        // invite goes out unpaid and a refusal locks that user.
        for (Object obj : selectedChats) {
            TLRPC.User user = (TLRPC.User) obj;
            final SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(link, user.id, null, null, null, true, null, null, null, false, 0, 0, null, false);
            SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
        }
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory factory = BulletinFactory.global();
            if (factory != null) {
                if (selectedChats.size() == 1) {
                    TLRPC.User user = (TLRPC.User) selectedChats.iterator().next();
                    factory.createSimpleBulletin(R.raw.voip_invite,
                            AndroidUtilities.replaceTags(LocaleController.formatString(R.string.InviteLinkSentSingle, ContactsController.formatName(user)))
                    ).show();
                } else {
                    factory.createSimpleBulletin(R.raw.voip_invite,
                            AndroidUtilities.replaceTags(LocaleController.formatPluralString("InviteLinkSent", selectedChats.size(), selectedChats.size()))
                    ).show();
                }
            }
        });
        dismiss();
    }

    public void updatePremiumButtonText() {
        if (premiumButtonSetSubscribe) {
            premiumButtonView.setOverlayText(getString(R.string.InvitePremiumBlockedSubscribe), false, false);
        } else if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() || isVeryLargeFile) {
            premiumButtonView.buttonTextView.setText(getString(R.string.OK));
            premiumButtonView.hideIcon();
        } else {
            premiumButtonView.buttonTextView.setText(getString(R.string.IncreaseLimit));
            if (limitParams != null) {
                if (limitParams.defaultLimit + 1 == limitParams.premiumLimit) {
                    premiumButtonView.setIcon(R.raw.addone_icon);
                } else if (
                        limitParams.defaultLimit != 0 && limitParams.premiumLimit != 0 &&
                                limitParams.premiumLimit / (float) limitParams.defaultLimit >= 1.6f &&
                                limitParams.premiumLimit / (float) limitParams.defaultLimit <= 2.5f
                ) {
                    premiumButtonView.setIcon(R.raw.double_icon);
                } else {
                    premiumButtonView.hideIcon();
                }
            } else {
                premiumButtonView.hideIcon();
            }
        }
    }

    private void leaveFromSelectedGroups() {
        TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        for (Object obj : selectedChats) {
            chats.add((TLRPC.Chat) obj);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.formatPluralString("LeaveCommunities", chats.size()));
        if (chats.size() == 1) {
            TLRPC.Chat channel = chats.get(0);
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelLeaveAlertWithName", R.string.ChannelLeaveAlertWithName, channel.title)));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChatsLeaveAlert", R.string.ChatsLeaveAlert)));
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.VoipGroupLeave), (dialogInterface, interface2) -> {
            dismiss();
            for (int i = 0; i < chats.size(); i++) {
                TLRPC.Chat chat = chats.get(i);
                MessagesController.getInstance(currentAccount).putChat(chat, false);
                MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chat.id, currentUser);
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void updateButton() {
        if (premiumButtonSetSubscribe) {
            premiumButtonView.setOverlayText(getString(R.string.InvitePremiumBlockedSubscribe), false, false);
        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
            premiumButtonView.checkCounterView();
            if (!canSendLink) {
                premiumButtonView.setOverlayText(getString(R.string.Close), true, true);
            } else if (selectedChats.size() > 0) {
                premiumButtonView.setOverlayText(getString(R.string.SendInviteLink), true, true);
            } else {
                premiumButtonView.setOverlayText(getString(R.string.ActionSkip), true, true);
            }
            premiumButtonView.counterView.setCount(selectedChats.size(), true);
            premiumButtonView.invalidate();
        } else {
            if (selectedChats.size() > 0) {
                String str = null;
                if (type == TYPE_PUBLIC_LINKS) {
                    str = LocaleController.formatPluralString("RevokeLinks", selectedChats.size());
                } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                    str = LocaleController.formatPluralString("LeaveCommunities", selectedChats.size());
                }
                premiumButtonView.setOverlayText(str, true, true);
            } else {
                premiumButtonView.clearOverlayText();
            }
        }
    }

    private static boolean hasFixedSize(int type) {
        return (
            type == TYPE_PIN_DIALOGS ||
            type == TYPE_PIN_SAVED_DIALOGS ||
            type == TYPE_FOLDERS ||
            type == TYPE_CHATS_IN_FOLDER ||
            type == TYPE_LARGE_FILE ||
            type == TYPE_ACCOUNTS ||
            type == TYPE_FOLDER_INVITES ||
            type == TYPE_SHARED_FOLDERS ||
            type == TYPE_STORIES_COUNT ||
            type == TYPE_STORIES_WEEK ||
            type == TYPE_STORIES_MONTH
        );
    }

    @Override
    public CharSequence getTitle() {
        switch (type) {
            case TYPE_ADD_MEMBERS_RESTRICTED:
                return getString(R.string.ChannelInviteViaLink2);
            case TYPE_CALL_RESTRICTED:
                return getString(R.string.CallInviteViaLink);
            default:
                return getString(R.string.LimitReached);
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHANNEL = 1;
    private static final int VIEW_TYPE_SHADOW = 2;
    private static final int VIEW_TYPE_HEADER_CELL = 3;
    private static final int VIEW_TYPE_USER = 4;
    private static final int VIEW_TYPE_PROGRESS = 5;
    private static final int VIEW_TYPE_SPACE16DP = 6;

    @Override
    public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                if ((type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) && !canSendLink) {
                    return false;
                }
                return holder.getItemViewType() == VIEW_TYPE_CHANNEL || holder.getItemViewType() == VIEW_TYPE_USER;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                Context context = parent.getContext();
                switch (viewType) {
                    default:
                    case VIEW_TYPE_HEADER:
                        view = new HeaderView(context);
                        break;
                    case VIEW_TYPE_CHANNEL:
                        view = new AdminedChannelCell(context, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AdminedChannelCell cell = (AdminedChannelCell) v.getParent();
                                final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
                                channels.add(cell.getCurrentChannel());
                                revokeLinks(channels);
                            }
                        }, true, 9);
                        break;
                    case VIEW_TYPE_SHADOW:
                        view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
                        break;
                    case VIEW_TYPE_HEADER_CELL:
                        view = new HeaderCell(context);
                        view.setPadding(0, 0, 0, AndroidUtilities.dp(8));
                        break;
                    case VIEW_TYPE_USER:
                        view = new GroupCreateUserCell(context, 1, 0, false, false, resourcesProvider);
                        view.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
                        break;
                    case VIEW_TYPE_PROGRESS:
                        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, null);
                        flickerLoadingView.setViewType(type == TYPE_PUBLIC_LINKS ? FlickerLoadingView.LIMIT_REACHED_LINKS : FlickerLoadingView.LIMIT_REACHED_GROUPS);
                        flickerLoadingView.setIsSingleCell(true);
                        flickerLoadingView.setIgnoreHeightCheck(true);
                        flickerLoadingView.setItemsCount(10);
                        view = flickerLoadingView;
                        break;
                    case VIEW_TYPE_SPACE16DP:
                        view = new View(getContext()) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(16), MeasureSpec.EXACTLY));
                            }
                        };
                        break;
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (holder.getItemViewType()) {
                    case VIEW_TYPE_USER:
                        GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                        if (type == TYPE_TO0_MANY_COMMUNITIES) {
                            TLRPC.Chat chat = inactiveChats.get(position - chatStartRow);
                            String signature = inactiveChatsSignatures.get(position - chatStartRow);
                            cell.setObject(chat, chat.title, signature, position != chatEndRow - 1f);
                            cell.setChecked(selectedChats.contains(chat), false);
                        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
                            TLRPC.User user = restrictedUsers.get(position - chatStartRow);
                            final boolean premiumBlocked = premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.contains(user.id);
                            cell.overridePremiumBlocked(premiumBlocked ? new TL_account.requirementToContactPremium() : null, false);
                            String signature;
                            if (premiumBlocked) {
                                signature = getString(R.string.InvitePremiumBlockedUser);
                            } else {
                                signature = LocaleController.formatUserStatus(currentAccount, user, null, null);
                            }
                            cell.setObject(user, ContactsController.formatName(user.first_name, user.last_name), signature, position != chatEndRow - 1f);
                            cell.setChecked(selectedChats.contains(user), false);
                        }
                        break;
                    case VIEW_TYPE_CHANNEL:
                        TLRPC.Chat chat = chats.get(position - chatStartRow);
                        AdminedChannelCell adminedChannelCell = (AdminedChannelCell) holder.itemView;
                        TLRPC.Chat oldChat = adminedChannelCell.getCurrentChannel();
                        adminedChannelCell.setChannel(chat, false);
                        adminedChannelCell.setChecked(selectedChats.contains(chat), oldChat == chat);
                        break;
                    case VIEW_TYPE_HEADER_CELL:
                        HeaderCell headerCell = (HeaderCell) holder.itemView;
                        if (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
                            if (canSendLink) {
                                headerCell.setText(getString(R.string.ChannelInviteViaLink));
                            } else {
                                if (restrictedUsers.size() == 1) {
                                    headerCell.setText(getString(R.string.ChannelInviteViaLinkRestricted2));
                                } else {
                                    headerCell.setText(getString(R.string.ChannelInviteViaLinkRestricted3));
                                }
                            }
                        } else if (type == TYPE_PUBLIC_LINKS) {
                            headerCell.setText(getString(R.string.YourPublicCommunities));
                        } else {
                            headerCell.setText(getString(R.string.LastActiveCommunities));
                        }
                        break;
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (headerRow == position) {
                    return VIEW_TYPE_HEADER;
                } else if (dividerRow == position) {
                    return VIEW_TYPE_SHADOW;
                } else if (chatsTitleRow == position) {
                    return VIEW_TYPE_HEADER_CELL;
                } else if (loadingRow == position) {
                    return VIEW_TYPE_PROGRESS;
                } else if (emptyViewDividerRow == position) {
                    return VIEW_TYPE_SPACE16DP;
                }
                if (type == TYPE_TO0_MANY_COMMUNITIES || type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
                    return VIEW_TYPE_USER;
                } else {
                    return VIEW_TYPE_CHANNEL;
                }
            }

            @Override
            public int getItemCount() {
                return rowCount;
            }
        };
    }

    public void setCurrentValue(int currentValue) {
        this.currentValue = currentValue;
    }

    public void setVeryLargeFile(boolean b) {
        isVeryLargeFile = b;
        updatePremiumButtonText();
    }

    private String forceLink;
    public void setRestrictedUsers(
        TLRPC.Chat chat,
        ArrayList<TLRPC.User> userRestrictedPrivacy,
        ArrayList<Long> premiumMessagingBlockedUsers,
        ArrayList<Long> premiumInviteBlockedUsers,
        String forceLink
    ) {
        fromChat = chat;
        this.forceLink = forceLink;
        canSendLink = !TextUtils.isEmpty(forceLink) || ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE);
        restrictedUsers = new ArrayList<>(userRestrictedPrivacy);
        this.premiumMessagingBlockedUsers = premiumMessagingBlockedUsers;
        this.premiumInviteBlockedUsers = premiumInviteBlockedUsers;
        selectedChats.clear();
        if (canSendLink) {
            for (TLRPC.User user : restrictedUsers) {
                if (premiumMessagingBlockedUsers == null || !premiumMessagingBlockedUsers.contains(user.id)) {
                    selectedChats.add(user);
                }
            }
        }
        updateRows();
        updateButton();

        if (
            ((type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (premiumInviteBlockedUsers != null && !premiumInviteBlockedUsers.isEmpty() || premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= restrictedUsers.size())) &&
            premiumInviteBlockedUsers != null && premiumMessagingBlockedUsers != null && (premiumInviteBlockedUsers.size() == 1 && premiumMessagingBlockedUsers.size() == 1 || premiumMessagingBlockedUsers.size() >= premiumInviteBlockedUsers.size())
        ) {
            if (LimitReachedBottomSheet.this.premiumButtonView != null && LimitReachedBottomSheet.this.premiumButtonView.getParent() != null) {
                ((ViewGroup) LimitReachedBottomSheet.this.premiumButtonView.getParent()).removeView(LimitReachedBottomSheet.this.premiumButtonView);
            }
            if (divider != null && divider.getParent() != null) {
                ((ViewGroup) divider.getParent()).removeView(divider);
            }
            if (recyclerListView != null) {
                recyclerListView.setPadding(0, 0, 0, 0);
            }
        }
    }

    private class HeaderView extends LinearLayout {

        TextView title;
        TextView description;

        @SuppressLint("SetTextI18n")
        public HeaderView(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setPadding(backgroundPaddingLeft + dp(6), 0, backgroundPaddingLeft + dp(6), 0);

            limitParams = getLimitParams(type, currentAccount);
            int icon = limitParams.icon;
            String descriptionStr;
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            boolean premiumLocked = messagesController.premiumFeaturesBlocked();
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                premiumLocked = true;
                if (!canSendLink) {
                    if (ChatObject.isChannelAndNotMegaGroup(fromChat)) {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString(R.string.InviteChannelRestrictedUsers2One, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteChannelRestrictedUsers2", restrictedUsers.size(), restrictedUsers.size());
                        }
                    } else {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString(R.string.InviteRestrictedUsers2One, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteRestrictedUsers2", restrictedUsers.size(), restrictedUsers.size());
                        }
                    }
                } else {
                    if (ChatObject.isChannelAndNotMegaGroup(fromChat)) {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString(R.string.InviteChannelRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteChannelRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                        }
                    } else {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString(R.string.InviteRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                        }
                    }
                }
            } else if (type == TYPE_CALL_RESTRICTED) {
                if (restrictedUsers.size() == 1) {
                    descriptionStr = LocaleController.formatString(R.string.InviteCallRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                } else {
                    descriptionStr = LocaleController.formatPluralString("InviteCallRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                }
            } else {
                if (premiumLocked) {
                    descriptionStr = limitParams.descriptionStrLocked;
                } else {
                    descriptionStr = (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) ? limitParams.descriptionStrPremium : limitParams.descriptionStr;
                }
            }
            int defaultLimit = limitParams.defaultLimit;
            int premiumLimit = limitParams.premiumLimit;
            int currentValue = LimitReachedBottomSheet.this.currentValue;
            float percent = .5f, position = .5f;

            if (type == TYPE_FOLDERS) {
                currentValue = MessagesController.getInstance(currentAccount).dialogFilters.size() - 1;
            } else if (type == TYPE_ACCOUNTS) {
                currentValue = UserConfig.getActivatedAccountsCount();
            } else if (type == TYPE_PIN_DIALOGS) {
                int pinnedCount = 0;
                ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getDialogs(0);
                for (int a = 0, N = dialogs.size(); a < N; a++) {
                    TLRPC.Dialog dialog = dialogs.get(a);
                    if (dialog instanceof TLRPC.TL_dialogFolder) {
                        continue;
                    }
                    if (dialog.pinned) {
                        pinnedCount++;
                    }
                }
                currentValue = pinnedCount;
            }

            if (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) {
                currentValue = premiumLimit;
                position = 1f;
            } else {
                if (currentValue < 0) {
                    currentValue = defaultLimit;
                }
                if (type == TYPE_ACCOUNTS) {
                    if (currentValue > defaultLimit) {
                        position = (float) (currentValue - defaultLimit) / (float) (premiumLimit - defaultLimit);
                    }
                } else {
                    position = currentValue / (float) premiumLimit;
                }
            }

            percent = defaultLimit / (float) premiumLimit;

            if ((type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (premiumInviteBlockedUsers != null && !premiumInviteBlockedUsers.isEmpty() || premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= restrictedUsers.size())) {
                ArrayList<Long> userIds = premiumInviteBlockedUsers.isEmpty() ? premiumMessagingBlockedUsers : premiumInviteBlockedUsers;

                AvatarsImageView avatarsImageView = new AvatarsImageView(context, false);
                avatarsImageView.avatarsDrawable.strokeWidth = dp(3.33f);
                avatarsImageView.setSize(dp(72));
                avatarsImageView.setStepFactor(0.4f);
                final int count = Math.min(userIds.size(), 3);
                avatarsImageView.setCount(count);
                for (int i = 0; i < count; ++i) {
                    long userId = userIds.get(i);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    avatarsImageView.setObject(i, currentAccount, user);
                }
                avatarsImageView.commitTransition(false);
                addView(avatarsImageView, LayoutHelper.createLinear(72 + (count - 1) * 30, 72, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 13));

                TextView title = new TextView(context);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(AndroidUtilities.bold());
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                title.setText(getString(R.string.InvitePremiumBlockedTitle));
                addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 9));

                TextView description = new TextView(context);
                description.setGravity(Gravity.CENTER);
                description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 19));

                final boolean calls = type == TYPE_CALL_RESTRICTED;
                final boolean andMessaging = premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= premiumInviteBlockedUsers.size();
//                final boolean onlyMessaging = premiumInviteBlockedUsers != null && premiumInviteBlockedUsers.isEmpty();
                String string;
                if (userIds.size() == 1) {
                    string = LocaleController.formatString(
                        calls ? R.string.InviteCallMessagePremiumBlockedOne : andMessaging ? R.string.InviteMessagePremiumBlockedOne : R.string.InvitePremiumBlockedOne,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0)))
                    );
                } else if (userIds.size() == 2) {
                    string = LocaleController.formatString(
                        calls ? R.string.InviteCallMessagePremiumBlockedTwo : andMessaging ? R.string.InviteMessagePremiumBlockedTwo : R.string.InvitePremiumBlockedTwo,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(1)))
                    );
                } else if (userIds.size() == 3) {
                    string = LocaleController.formatString(
                        calls ? R.string.InviteCallMessagePremiumBlockedThree : andMessaging ? R.string.InviteMessagePremiumBlockedThree : R.string.InvitePremiumBlockedThree,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(1))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(2)))
                    );
                } else {
                    string = LocaleController.formatPluralString(
                        calls ? "InviteCallMessagePremiumBlockedMany" : andMessaging ? "InviteMessagePremiumBlockedMany" : "InvitePremiumBlockedMany",
                            userIds.size() - 2,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(1)))
                    );
                    avatarsImageView.setPlus(userIds.size() - 2, getThemedColor(Theme.key_dialogBackground));
                }
                description.setText(AndroidUtilities.replaceTags(string));

                final int blockedInviting = premiumInviteBlockedUsers == null ? 0 : premiumInviteBlockedUsers.size();
                final int blockedMessaging = premiumMessagingBlockedUsers == null ? 0 : premiumMessagingBlockedUsers.size();

                if ((blockedInviting - blockedMessaging) > 0 && !(blockedInviting == 1 && blockedMessaging == 1) && canSendLink) {
                    PremiumButtonView premiumButtonView = new PremiumButtonView(context, false, resourcesProvider);
                    ScaleStateListAnimator.apply(premiumButtonView, .02f, 1.2f);
                    premiumButtonView.setButton(getString(R.string.InvitePremiumBlockedSubscribe), v -> {
                        if (parentFragment == null) return;
                        BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                        params.transitionFromLeft = true;
                        params.allowNestedScroll = false;
                        parentFragment.showAsSheet(new PremiumPreviewFragment("invite_privacy"), params);
                    });
                    addView(premiumButtonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 4 + backgroundPaddingLeft / AndroidUtilities.density, 0, 4 + backgroundPaddingLeft / AndroidUtilities.density, 18));

                    TextView or = new TextView(context) {
                        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        @Override
                        protected void dispatchDraw(Canvas canvas) {
                            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), .8f));
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(1);
                            final float cy = getHeight() / 2f;
                            int textWidth = 0;
                            final Layout layout = getLayout();
                            for (int i = 0; i < layout.getLineCount(); ++i) {
                                textWidth = Math.max(textWidth, (int) layout.getLineWidth(i));
                            }
                            canvas.drawLine(0, cy, getWidth() / 2f - textWidth / 2f - dp(8), cy, paint);
                            canvas.drawLine(getWidth() / 2f + textWidth / 2f + dp(8), cy, getWidth(), cy, paint);
                            super.dispatchDraw(canvas);
                        }
                    };
                    or.setGravity(Gravity.CENTER);
                    or.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                    or.setText(" " + getString(R.string.InvitePremiumBlockedOr) + " ");
                    or.setTextSize(14);
                    addView(or, LayoutHelper.createLinear(190, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 20));

                    title = new TextView(context);
                    title.setGravity(Gravity.CENTER);
                    title.setTypeface(AndroidUtilities.bold());
                    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    title.setText(getString(R.string.InviteBlockedTitle));
                    addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 9));

                    description = new TextView(context);
                    description.setGravity(Gravity.CENTER);
                    description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    if (premiumInviteBlockedUsers.size() <= 1) {
                        description.setText(getString(R.string.InviteBlockedOneMessage));
                    } else {
                        description.setText(getString(R.string.InviteBlockedManyMessage));
                    }
                    addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 19));
                } else {
                    ((MarginLayoutParams) description.getLayoutParams()).bottomMargin = dp(8);
                    premiumButtonSetSubscribe = true;
                }

                updatePremiumButtonText();
                return;
            } else if (type != TYPE_CALL_RESTRICTED) {
                limitPreviewView = new LimitPreviewView(context, icon, currentValue, premiumLimit, percent, resourcesProvider);
                limitPreviewView.setBagePosition(position);
                limitPreviewView.setType(type);
                limitPreviewView.defaultCount.setVisibility(View.GONE);
                if (premiumLocked) {
                    limitPreviewView.setPremiumLocked();
                } else {
                    if (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) {
                        limitPreviewView.premiumCount.setVisibility(View.GONE);
                        if (type == TYPE_LARGE_FILE) {
                            limitPreviewView.defaultCount.setText("2 GB");
                        } else {
                            limitPreviewView.defaultCount.setText(Integer.toString(defaultLimit));
                        }
                        limitPreviewView.defaultCount.setVisibility(View.VISIBLE);
                    }
                }

                if (type == TYPE_PUBLIC_LINKS || type == TYPE_TO0_MANY_COMMUNITIES) {
                    limitPreviewView.setDelayedAnimation();
                }

                addView(limitPreviewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, -4, 0, -4, 0));
            }

            title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                if (canSendLink) {
                    title.setText(getString(R.string.ChannelInviteViaLink));
                } else {
                    title.setText(getString(R.string.ChannelInviteViaLinkRestricted));
                }
            } else if (type == TYPE_CALL_RESTRICTED) {
                title.setText(getString(R.string.CallInviteViaLinkTitle));
            } else if (type == TYPE_LARGE_FILE) {
                title.setText(getString(R.string.FileTooLarge));
            } else if (type == TYPE_STORIES_COUNT && storiesCount > 1) {
                title.setText(getString(R.string.CreateMultipleStories));
            } else {
                title.setText(getString(R.string.LimitReached));
            }
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            title.setGravity(Gravity.CENTER);

            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, premiumLocked ? 8 : 22, 0, 10));
            description = new TextView(context);
            description.setText(AndroidUtilities.replaceTags(descriptionStr));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 0, 24, 24));
            updatePremiumButtonText();
        }

    }

    private static LimitParams getLimitParams(int type, int currentAccount) {
        LimitParams limitParams = new LimitParams();
        if (type == TYPE_PIN_DIALOGS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_pin;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedPinDialogs", R.string.LimitReachedPinDialogs, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPinDialogsPremium", R.string.LimitReachedPinDialogsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPinDialogsLocked", R.string.LimitReachedPinDialogsLocked, limitParams.defaultLimit);
        } else if (type == TYPE_PIN_SAVED_DIALOGS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_pin;
            limitParams.descriptionStr = LocaleController.formatString(R.string.LimitReachedPinSavedDialogs, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString(R.string.LimitReachedPinSavedDialogsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString(R.string.LimitReachedPinSavedDialogsLocked, limitParams.defaultLimit);
        } else if (type == TYPE_PUBLIC_LINKS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).publicLinksLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).publicLinksLimitPremium;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedPublicLinks", R.string.LimitReachedPublicLinks, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPublicLinksPremium", R.string.LimitReachedPublicLinksPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPublicLinksLocked", R.string.LimitReachedPublicLinksLocked, limitParams.defaultLimit);
        } else if (type == TYPE_FOLDER_INVITES) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).chatlistInvitesLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).chatlistInvitesLimitPremium;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolderLinks", R.string.LimitReachedFolderLinks, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFolderLinksPremium", R.string.LimitReachedFolderLinksPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFolderLinksLocked", R.string.LimitReachedFolderLinksLocked, limitParams.defaultLimit);
        } else if (type == TYPE_SHARED_FOLDERS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).chatlistJoinedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).chatlistJoinedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedSharedFolders", R.string.LimitReachedSharedFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedSharedFoldersPremium", R.string.LimitReachedSharedFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedSharedFoldersLocked", R.string.LimitReachedSharedFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_FOLDERS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitPremium;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolders", R.string.LimitReachedFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFoldersPremium", R.string.LimitReachedFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFoldersLocked", R.string.LimitReachedFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_CHATS_IN_FOLDER) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitPremium;
            limitParams.icon = R.drawable.msg_limit_chats;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedChatInFolders", R.string.LimitReachedChatInFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedChatInFoldersPremium", R.string.LimitReachedChatInFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedChatInFoldersLocked", R.string.LimitReachedChatInFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).channelsLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).channelsLimitPremium;
            limitParams.icon = R.drawable.msg_limit_groups;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedCommunities", R.string.LimitReachedCommunities, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedCommunitiesPremium", R.string.LimitReachedCommunitiesPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedCommunitiesLocked", R.string.LimitReachedCommunitiesLocked, limitParams.defaultLimit);
        } else if (type == TYPE_LARGE_FILE) {
            limitParams.defaultLimit = 100;
            limitParams.premiumLimit = 200;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFileSize", R.string.LimitReachedFileSize, "2 GB", "4 GB");
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFileSizePremium", R.string.LimitReachedFileSizePremium, "4 GB");
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFileSizeLocked", R.string.LimitReachedFileSizeLocked, "2 GB");
        } else if (type == TYPE_ACCOUNTS) {
            limitParams.defaultLimit = 3;
            limitParams.premiumLimit = 4;
            limitParams.icon = R.drawable.msg_limit_accounts;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.defaultLimit);
        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
            limitParams.defaultLimit = 0;
            limitParams.premiumLimit = 0;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = "";
            limitParams.descriptionStrLocked = "";
        } else if (type == TYPE_STORIES_COUNT) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storyExpiringLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storyExpiringLimitPremium;
            limitParams.icon = R.drawable.msg_limit_stories;
            limitParams.descriptionStr = LocaleController.formatPluralStringComma("LimitReachedStoriesCount2First", limitParams.defaultLimit) + "\n" + LocaleController.formatPluralStringComma("LimitReachedStoriesCount2Second", limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatPluralStringComma("LimitReachedStoriesCount2Premium", limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatPluralStringComma("LimitReachedStoriesCount2Premium", limitParams.defaultLimit);
        } else if (type == TYPE_STORIES_WEEK) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storiesSentWeeklyLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storiesSentWeeklyLimitPremium;
            limitParams.icon = R.drawable.msg_limit_stories;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedStoriesWeekly", R.string.LimitReachedStoriesWeekly, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedStoriesWeeklyPremium", R.string.LimitReachedStoriesWeeklyPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedStoriesWeeklyPremium", R.string.LimitReachedStoriesWeeklyPremium, limitParams.defaultLimit);
        } else if (type == TYPE_STORIES_MONTH) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storiesSentMonthlyLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storiesSentMonthlyLimitPremium;
            limitParams.icon = R.drawable.msg_limit_stories;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedStoriesMonthly", R.string.LimitReachedStoriesMonthly, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedStoriesMonthlyPremium", R.string.LimitReachedStoriesMonthlyPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedStoriesMonthlyPremium", R.string.LimitReachedStoriesMonthlyPremium, limitParams.defaultLimit);
        }
        return limitParams;
    }

    boolean loadingAdminedChannels;

    private void loadAdminedChannels() {
        loadingAdminedChannels = true;
        loading = true;
        updateRows();
        TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingAdminedChannels = false;
            if (response != null) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                chats.clear();
                chats.addAll(res.chats);
                loading = false;
                enterAnimator.showItemsAnimated(chatsTitleRow + 4);
                int savedTop = 0;
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    if (recyclerListView.getChildAt(i) instanceof HeaderView) {
                        savedTop = recyclerListView.getChildAt(i).getTop();
                        break;
                    }
                }
                updateRows();
                if (headerRow >= 0 && savedTop != 0) {
                    ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(headerRow + 1, savedTop);
                }
            }

            int currentValue = Math.max(chats.size(), limitParams.defaultLimit);
            limitPreviewView.setIconValue(currentValue, false);
            limitPreviewView.setBagePosition(currentValue / (float) limitParams.premiumLimit);
            limitPreviewView.startDelayedAnimation();
        }));
    }

    private void updateRows() {
        rowCount = 0;
        dividerRow = -1;
        chatStartRow = -1;
        chatEndRow = -1;
        loadingRow = -1;
        emptyViewDividerRow = -1;

        headerRow = rowCount++;
        if (!hasFixedSize(type)) {
            if (type != TYPE_ADD_MEMBERS_RESTRICTED && type != TYPE_CALL_RESTRICTED) {
                dividerRow = rowCount++;
                chatsTitleRow = rowCount++;
            } else {
                topPadding = .24f;
            }
            if (loading) {
                loadingRow = rowCount++;
            } else if (!(type == TYPE_ADD_MEMBERS_RESTRICTED && !canSendLink)) {
                if (
                    !(type == TYPE_ADD_MEMBERS_RESTRICTED && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (premiumInviteBlockedUsers != null && !premiumInviteBlockedUsers.isEmpty() || premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= restrictedUsers.size())) ||
                    !(premiumInviteBlockedUsers != null && premiumInviteBlockedUsers.size() == 1 && premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() == 1 && canSendLink)
                ) {
                    chatStartRow = rowCount;
                    if (type == TYPE_ADD_MEMBERS_RESTRICTED || type == TYPE_CALL_RESTRICTED) {
                        rowCount += restrictedUsers.size();
                    } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                        rowCount += inactiveChats.size();
                    } else {
                        rowCount += chats.size();
                    }
                    chatEndRow = rowCount;
                }
                if (chatEndRow - chatStartRow > 1) {
                    emptyViewDividerRow = rowCount++;
                }
            }
        }
        notifyDataSetChanged();
    }


    private void revokeSelectedLinks() {
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        for (Object obj : selectedChats) {
            chats.add((TLRPC.Chat) obj);
        }
        revokeLinks(channels);
    }

    private void revokeLinks(ArrayList<TLRPC.Chat> channels) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.formatPluralString("RevokeLinks", channels.size()));
        if (channels.size() == 1) {
            TLRPC.Chat channel = channels.get(0);
            if (parentIsChannel) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
            }
        } else {
            if (parentIsChannel) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlertChannel", R.string.RevokeLinksAlertChannel)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlert", R.string.RevokeLinksAlert)));
            }
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.RevokeButton), (dialogInterface, interface2) -> {
            dismiss();
            for (int i = 0; i < channels.size(); i++) {
                TLRPC.TL_channels_updateUsername req1 = new TLRPC.TL_channels_updateUsername();
                TLRPC.Chat channel = channels.get(i);
                req1.channel = MessagesController.getInputChannel(channel);
                req1.username = "";
                ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {
                    if (response1 instanceof TLRPC.TL_boolTrue) {
                        AndroidUtilities.runOnUIThread(onSuccessRunnable);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void loadInactiveChannels() {
        loading = true;
        updateRows();
        TLRPC.TL_channels_getInactiveChannels inactiveChannelsRequest = new TLRPC.TL_channels_getInactiveChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(inactiveChannelsRequest, ((response, error) -> {
            if (error == null) {
                final TLRPC.TL_messages_inactiveChats chats = (TLRPC.TL_messages_inactiveChats) response;
                final ArrayList<String> signatures = new ArrayList<>();
                final int count = Math.min(chats.chats.size(), chats.dates.size());
                for (int i = 0; i < count; i++) {
                    TLRPC.Chat chat = chats.chats.get(i);
                    int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                    int date = chats.dates.get(i);
                    int daysDif = (currentDate - date) / 86400;

                    String dateFormat;
                    if (daysDif < 30) {
                        dateFormat = LocaleController.formatPluralString("Days", daysDif);
                    } else if (daysDif < 365) {
                        dateFormat = LocaleController.formatPluralString("Months", daysDif / 30);
                    } else {
                        dateFormat = LocaleController.formatPluralString("Years", daysDif / 365);
                    }
                    if (ChatObject.isMegagroup(chat)) {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    } else if (ChatObject.isChannel(chat)) {
                        signatures.add(LocaleController.formatString("InactiveChannelSignature", R.string.InactiveChannelSignature, dateFormat));
                    } else {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    inactiveChatsSignatures.clear();
                    inactiveChats.clear();
                    inactiveChatsSignatures.addAll(signatures);
                    for (int i = 0; i < count; i++) {
                        inactiveChats.add(chats.chats.get(i));
                    }
                    loading = false;
                    enterAnimator.showItemsAnimated(chatsTitleRow + 4);
                    int savedTop = 0;
                    for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                        if (recyclerListView.getChildAt(i) instanceof HeaderView) {
                            savedTop = recyclerListView.getChildAt(i).getTop();
                            break;
                        }
                    }
                    updateRows();
                    if (headerRow >= 0 && savedTop != 0) {
                        ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(headerRow + 1, savedTop);
                    }

                    if (limitParams == null) {
                        limitParams = getLimitParams(type, currentAccount);
                    }
                    int currentValue = Math.max(inactiveChats.size(), limitParams.defaultLimit);
                    if (limitPreviewView != null) {
                        limitPreviewView.setIconValue(currentValue, false);
                        limitPreviewView.setBagePosition(currentValue / (float) limitParams.premiumLimit);
                        limitPreviewView.startDelayedAnimation();
                    }
                });
            }
        }));
    }

    public static class LimitParams {
        int icon = 0;
        String descriptionStr = null;
        String descriptionStrPremium = null;
        String descriptionStrLocked = null;
        int defaultLimit = 0;
        int premiumLimit = 0;
    }
}
