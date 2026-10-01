package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import org.telegram.ui.recyclerview.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter;
import org.telegram.ui.Components.Premium.boosts.adapters.SelectorAdapter.Item;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorHeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorSearchCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class UserSelectorBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    // LoogriGram: this sheet picked whom to gift Premium to (TYPE_PREMIUM, opened
    // by t.me/premium_multigift and tg:premium_multigift), whom to send a star
    // gift (TYPE_STAR_GIFT), whom to gift Stars or hand a collectible to, and
    // whom to add to a call (TYPE_CALL). Money in both directions and Premium
    // are gone; adding people to a call is what it is for now, so the type,
    // the gift options, the birthday sections and the "gift to myself" row went.

    private static final int BOTTOM_HEIGHT_DP = 60;

    private final ButtonWithCounterView actionButton;
    private final SelectorSearchCell searchField;
    private final View sectionCell;
    private final SelectorHeaderCell headerView;
    private CheckBox2 videoCheckbox;
    private final SelectorBtnCell buttonContainer;
    private final FrameLayout bulletinContainer;

    private final ArrayList<Item> oldItems = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();
    private final List<TLRPC.TL_contact> contacts = new ArrayList<>();
    private final List<TLRPC.TL_topPeer> hints = new ArrayList<>();
    private final ArrayList<TLObject> searchResult = new ArrayList<>();
    private final Map<String, List<TLRPC.TL_contact>> contactsMap = new HashMap<>();
    private final List<String> contactsLetters = new ArrayList<>();
    private String query;
    private SelectorAdapter selectorAdapter;
    private int listPaddingTop = AndroidUtilities.dp(56 + 64);
    private boolean isHintSearchText = false;
    private int lastRequestId;

    private final Runnable remoteSearchRunnable = new Runnable() {
        @Override
        public void run() {
            final String finalQuery = query;
            if (finalQuery != null) {
                search(finalQuery);
            }
        }
    };

    private int runningRequest = -1;
    private void cancelSearch() {
        if (runningRequest >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(runningRequest, true);
            runningRequest = -1;
        }
    }

    private void search(String query) {
        cancelSearch();
        final TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        runningRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            searchResult.clear();
            runningRequest = -1;
            if (res instanceof TLRPC.TL_contacts_found) {
                final TLRPC.TL_contacts_found r = (TLRPC.TL_contacts_found) res;
                final MessagesController m = MessagesController.getInstance(currentAccount);
                m.putUsers(r.users, false);
                m.putChats(r.chats, false);

                final HashSet<Long> dialogIds = new HashSet<>();
                for (TLRPC.Peer peer : r.my_results) {
                    final long did = DialogObject.getPeerDialogId(peer);
                    if (dialogIds.contains(did)) continue;
                    final TLObject obj = m.getUserOrChat(did);
                    if (obj == null) continue;
                    searchResult.add(obj);
                    dialogIds.add(did);
                }
                for (TLRPC.Peer peer : r.results) {
                    final long did = DialogObject.getPeerDialogId(peer);
                    if (dialogIds.contains(did)) continue;
                    final TLObject obj = m.getUserOrChat(did);
                    if (obj == null) continue;
                    searchResult.add(obj);
                    dialogIds.add(did);
                }
            }
            updateList(true, true);
        }));
    }

    private void checkEditTextHint() {
        if (!isHintSearchText) {
            isHintSearchText = true;
            AndroidUtilities.runOnUIThread(() -> searchField.setHintText(getString(R.string.Search), true), 10);
        }
    }

    public UserSelectorBottomSheet(Context context, int currentAccount, boolean needFocus, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, needFocus, false, false, resourcesProvider);
        this.currentAccount = currentAccount;
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        drawDoubleNavigationBar = false;

        if (selectorAdapter != null) {
            selectorAdapter.setNeedChecks2(true);
        }

        headerView = new SelectorHeaderCell(getContext(), resourcesProvider) {
            @Override
            protected int getHeaderHeight() {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    return dp(48);
                } else {
                    return dp(54);
                }
            }
        };
        headerView.setOnCloseClickListener(this::dismiss);
        headerView.setText(getTitle());
        headerView.setCloseImageVisible(false);
        headerView.backDrawable.setRotation(0f, false);

        searchField = new SelectorSearchCell(getContext(), resourcesProvider, null) {
            private boolean isKeyboardVisible;

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                listPaddingTop = getMeasuredHeight() + dp(64);
                selectorAdapter.notifyChangedLast();
                if (isKeyboardVisible != isKeyboardVisible()) {
                    isKeyboardVisible = isKeyboardVisible();
                    if (isKeyboardVisible) {
                        scrollToTop(true);
                    }
                }
            }
        };
        searchField.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        searchField.setOnSearchTextChange(this::onSearch);
        searchField.setHintText(getString(R.string.Search), false);

        sectionCell = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawColor(getThemedColor(Theme.key_graySection));
            }
        };

        containerView.addView(headerView, 0, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        containerView.addView(searchField, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));
        containerView.addView(sectionCell, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, null);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(10), dp(10), dp(10), dp(10));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        LinearLayout videoLayout = new LinearLayout(context);
        videoLayout.setPadding(dp(12), dp(4), dp(12), dp(4));
        videoLayout.setClipToPadding(false);
        videoLayout.setOrientation(LinearLayout.HORIZONTAL);
        videoLayout.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 6, 6));
        videoCheckbox = new CheckBox2(context, 24, resourcesProvider);
        videoCheckbox.setColor(Theme.key_featuredStickers_addButton, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        videoCheckbox.setDrawUnchecked(true);
        videoCheckbox.setChecked(false, false);
        videoCheckbox.setDrawBackgroundAsArc(10);
        videoLayout.addView(videoCheckbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        TextView videoTextView = new TextView(context);
        videoTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        videoTextView.setText(LocaleController.getString(R.string.ConferenceCallWithVideo));
        videoLayout.addView(videoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
        ScaleStateListAnimator.apply(videoLayout, 0.025f, 1.5f);
        videoLayout.setOnClickListener(v -> {
            videoCheckbox.setChecked(!videoCheckbox.isChecked(), true);
        });
        buttonContainer.addView(videoLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 8));
        actionButton = new ButtonWithCounterView(getContext(), resourcesProvider);
        buttonContainer.setAlpha(0.0f);
        buttonContainer.setVisibility(View.GONE);
        actionButton.setOnClickListener(v -> next());
        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        bulletinContainer = new FrameLayout(getContext());
        containerView.addView(bulletinContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 300, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(68)));

        selectorAdapter.setData(items, recyclerListView);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(BOTTOM_HEIGHT_DP));
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(searchField.getEditText());
                }
            }
        });
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof TextCell) {
                if (onShareCallLinkListener != null) {
                    onShareCallLinkListener.run();
                    dismiss();
                }
                return;
            }
            if (view instanceof SelectorUserCell) {
                TLRPC.User user = ((SelectorUserCell) view).getUser();
                TLRPC.Chat chat = ((SelectorUserCell) view).getChat();
                if (user == null && chat == null) return;
                long id = user != null ? user.id : -chat.id;
                // LoogriGram: picking someone here opened the gift sheet for
                // them. Nothing sends a gift; this sheet still adds people to
                // a call.
                if (selectedIds.isEmpty()) {
                    selectedIds.add(id);
                    if (onUsersSelectedListener != null) {
                        onUsersSelectedListener.run(videoCheckbox != null && videoCheckbox.isChecked(), selectedIds);
                        onUsersSelectedListener = null;
                    }
                    dismiss();
                    return;
                }
                final boolean wasButtonVisible = !selectedIds.isEmpty();
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                }
                if (selectedIds.size() == getLimit() + 1) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return;
                }
                final boolean isButtonVisible = !selectedIds.isEmpty();
                if (wasButtonVisible != isButtonVisible) {
                    buttonContainer.setVisibility(View.VISIBLE);
                    buttonContainer.animate()
                        .alpha(isButtonVisible ? 1.0f : 0.0f)
                        .translationY(isButtonVisible ? 0 : dp(12))
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .setDuration(320)
                        .withEndAction(!isButtonVisible ? () -> buttonContainer.setVisibility(View.GONE) : null)
                        .start();
                    selectorAdapter.setCallButtonsVisible(!isButtonVisible);
                }
                checkEditTextHint();
                searchField.updateSpans(true, selectedIds, () -> {
                    checkEditTextHint();
                    updateList(true, false);
                }, null);
                updateList(true, true);
                clearSearchAfterSelect();
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (view instanceof SelectorUserCell) {
                TLRPC.User user = ((SelectorUserCell) view).getUser();
                TLRPC.Chat chat = ((SelectorUserCell) view).getChat();
                long id = user != null ? user.id : -chat.id;
                final boolean wasButtonVisible = !selectedIds.isEmpty();
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                }
                if (selectedIds.size() == getLimit() + 1) {
                    selectedIds.remove(id);
                    showMaximumUsersToast();
                    return true;
                }
                final boolean isButtonVisible = !selectedIds.isEmpty();
                if (wasButtonVisible != isButtonVisible) {
                    buttonContainer.setVisibility(View.VISIBLE);
                    buttonContainer.animate()
                        .alpha(isButtonVisible ? 1.0f : 0.0f)
                        .translationY(isButtonVisible ? 0 : dp(12))
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .setDuration(320)
                        .withEndAction(!isButtonVisible ? () -> buttonContainer.setVisibility(View.GONE) : null)
                        .start();
                    selectorAdapter.setCallButtonsVisible(!isButtonVisible);
                }
                checkEditTextHint();
                searchField.updateSpans(true, selectedIds, () -> {
                    checkEditTextHint();
                    updateList(true, false);
                }, null);
                updateList(true, true);
                clearSearchAfterSelect();
                return true;
            }
            return false;
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int position = parent.getChildAdapterPosition(view);
                if (position == items.size()) {
                    outRect.bottom = listPaddingTop;
                }
            }
        });

        searchField.setText("");
        searchField.spansContainer.removeAllSpans(false);
        searchField.updateSpans(false, selectedIds, () -> {
            checkEditTextHint();
            updateList(true, false);
        }, null);
        headerView.setText(getTitle());
        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
        updateActionButton(false);
        initContacts(false);
        initHints(false);
        updateList(false, true);
    }

    private void initContacts(boolean needUpdate) {
        if (contacts.isEmpty()) {
            contacts.addAll(ContactsController.getInstance(currentAccount).contacts);
            contactsMap.putAll(ContactsController.getInstance(currentAccount).usersSectionsDict);
            contactsLetters.addAll(ContactsController.getInstance(currentAccount).sortedUsersSectionsArray);
            if (needUpdate) {
                updateItems(true, true);
            }
        }
    }

    private void initHints(boolean needUpdate) {
        if (hints.isEmpty()) {
            hints.addAll(MediaDataController.getInstance(currentAccount).hints);
            if (needUpdate) {
                updateItems(true, true);
            }
        }
    }

    @Override
    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {
        final float minTop = AndroidUtilities.statusBarHeight - dp(8);
        final float fromY = Math.max(top, minTop) + dp(8);
        headerView.setTranslationY(fromY);
        searchField.setTranslationY(headerView.getTranslationY() + headerView.getMeasuredHeight());
        sectionCell.setTranslationY(searchField.getTranslationY() + searchField.getMeasuredHeight());
        recyclerListView.setTranslationY(headerView.getMeasuredHeight() + searchField.getMeasuredHeight() + sectionCell.getMeasuredHeight() - AndroidUtilities.dp(8));
    }

    private void next() {
        if (selectedIds.size() == 0) {
            return;
        }
        AndroidUtilities.hideKeyboard(searchField.getEditText());
        if (onUsersSelectedListener != null) {
            onUsersSelectedListener.run(videoCheckbox != null && videoCheckbox.isChecked(), selectedIds);
            onUsersSelectedListener = null;
        }
        dismiss();
    }

    public void scrollToTop(boolean animate) {
        if (animate) {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .6f);
            linearSmoothScroller.setTargetPosition(1);
            linearSmoothScroller.setOffset(dp(36));
            recyclerListView.getLayoutManager().startSmoothScroll(linearSmoothScroller);
        } else {
            recyclerListView.scrollToPosition(0);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadHints);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadHints);
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
    }

    protected int getLimit() {
        return Math.max(0, MessagesController.getInstance(currentAccount).conferenceCallSizeLimit - excludeUserIds.size() - 1);
    }

    private void showMaximumUsersToast() {
        final String text = formatPluralStringComma("UserSelectorLimit", getLimit());
        BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, text).show(true);
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
    }

    private void updateList(boolean animated, boolean notify) {
        updateItems(animated, notify);
        updateCheckboxes(animated);
        updateActionButton(animated);
    }

    private void updateCheckboxes(boolean animated) {
        int visibleItemsFrom = -1;
        int visibleItemsTo = 0;
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof SelectorUserCell) {
                int position = recyclerListView.getChildAdapterPosition(child);
                if (position - 1 < 0 || position - 1 >= items.size()) {
                    continue;
                }
                if (visibleItemsFrom == -1) {
                    visibleItemsFrom = position;
                }
                visibleItemsTo = position;
                Item item = items.get(position - 1);
                SelectorUserCell cell = (SelectorUserCell) child;
                cell.setChecked(item.checked, animated);
                if (item.chat != null) {
                    cell.setCheckboxAlpha(selectorAdapter.getParticipantsCount(item.chat) > 200 ? .3f : 1f, animated);
                } else {
                    cell.setCheckboxAlpha(1f, animated);
                }
            }
        }
        if (animated) {
            selectorAdapter.notifyItemRangeChanged(0, visibleItemsFrom);
            selectorAdapter.notifyItemRangeChanged(visibleItemsTo, selectorAdapter.getItemCount() - visibleItemsTo);
        }
    }

    private void updateActionButton(boolean animated) {
        actionButton.setShowZero(false);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        stringBuilder.append(getString(R.string.CallInviteMembersButton));
        actionButton.setCount(selectedIds.size(), true);
        actionButton.setText(stringBuilder, animated, false);
        actionButton.setEnabled(selectedIds.size() > 0);
    }

    private void onSearch(String text) {
        this.query = text;
        AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
        AndroidUtilities.runOnUIThread(remoteSearchRunnable, 350);
    }

    private void clearSearchAfterSelect() {
        if (isSearching()) {
            query = null;
            searchField.setText("");
            AndroidUtilities.cancelRunOnUIThread(remoteSearchRunnable);
            updateItems(true, true);
        }
    }

    private boolean isSearching() {
        return !TextUtils.isEmpty(query);
    }

    private Item decorate(Item item) {
        if (item.user == null) return item;
        final long userId = item.user.id;
        return item.withCall(v -> {
            selectedIds.add(userId);
            if (onUsersSelectedListener != null) {
                onUsersSelectedListener.run(false, selectedIds);
                onUsersSelectedListener = null;
            }
            dismiss();
        }, v -> {
            selectedIds.add(userId);
            if (onUsersSelectedListener != null) {
                onUsersSelectedListener.run(true, selectedIds);
                onUsersSelectedListener = null;
            }
            dismiss();
        });
    }


    @SuppressLint("NotifyDataSetChanged")
    public void updateItems(boolean animated, boolean notify) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        int h = 0;
        if (isSearching()) {
            for (TLObject peer : searchResult) {
                long did;
                if (peer instanceof TLRPC.User) {
                    final TLRPC.User user = (TLRPC.User) peer;
                    if (user.bot || UserObject.isService(user.id)) continue;
                    did = user.id;
                    h += dp(56);
                    if (excludeUserIds.contains(user.id)) continue;
                    items.add(decorate(Item.asUser(user, selectedIds.contains(did))));
                } else if (peer instanceof TLRPC.Chat) {
                    // LoogriGram: only the transfer picker listed channels here.
                    continue;
                }
            }
        } else {
            // LoogriGram: an "Export to TON" row stood at the top of the transfer picker.
            if (onShareCallLinkListener != null) {
                items.add(Item.asButton(3, R.drawable.msg2_link2, getString(R.string.VoipConferenceShareLink)));
            }
            Item topSection = null;
            final ArrayList<Long> selected = new ArrayList<>();
            if (!hints.isEmpty()) {
                final List<Item> userItems = new ArrayList<>();
                for (final TLRPC.TL_topPeer hint : hints) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(hint.peer.user_id);
                    if (user == null || user.self || user.bot || UserObject.isService(user.id) || UserObject.isDeleted(user)) {
                        continue;
                    }
                    if (excludeUserIds.contains(user.id)) continue;
                    if (selectedIds.contains(user.id)) selected.add(user.id);
                    h += dp(56);
                    userItems.add(decorate(Item.asUser(user, selectedIds.contains(user.id))));
                }
                if (!userItems.isEmpty()) {
                    h += dp(32);
                    topSection = Item.asTopSection(getString(R.string.GiftPremiumFrequentContacts));
                    items.add(topSection);
                    items.addAll(userItems);
                }
            }
            for (final String contactLetter : contactsLetters) {
                final List<Item> userItems = new ArrayList<>();
                for (final TLRPC.TL_contact contact : contactsMap.get(contactLetter)) {
                    final long myUid = UserConfig.getInstance(currentAccount).getClientUserId();
                    if (contact.user_id == myUid) {
                        continue;
                    }
                    if (excludeUserIds.contains(contact.user_id)) continue;
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (user == null || user.bot || UserObject.isService(user.id)) continue;
                    h += dp(56);
                    if (selectedIds.contains(user.id)) selected.add(user.id);
                    userItems.add(decorate(Item.asUser(user, selectedIds.contains(user.id))));
                }

                if (!userItems.isEmpty()) {
                    h += dp(32);
                    items.add(Item.asLetter(contactLetter.toUpperCase()));
                    items.addAll(userItems);
                }
            }
            if (topSection != null && selected.size() > 0 && !selectedIds.isEmpty()) {
                topSection.withRightText(getString(R.string.DeselectAll), v -> {
                    for (long userId : selected) {
                        selectedIds.remove(userId);
                    }
                    checkEditTextHint();
                    searchField.updateSpans(true, selectedIds, () -> {
                        checkEditTextHint();
                        updateList(true, false);
                    }, null);
                    updateList(true, true);
                    clearSearchAfterSelect();
                });
            }
        }

        if (items.isEmpty()) {
            items.add(Item.asNoUsers());
            h += dp(150);
        }
        int minHeight = (int) (AndroidUtilities.displaySize.y * 0.6f);
        items.add(Item.asPad(Math.max(0, minHeight - h)));

        if (notify && selectorAdapter != null) {
            if (animated) {
                selectorAdapter.setItems(oldItems, items);
            } else {
                selectorAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateItems(false, true);
    }

    private String customTitle;
    public void setTitle(String title) {
        customTitle = title;

        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
        if (headerView != null) {
            headerView.setText(getTitle());
        }
    }

    private Runnable onShareCallLinkListener;
    public UserSelectorBottomSheet setOnShareCallLinkListener(Runnable listener) {
        onShareCallLinkListener = listener;
        updateItems(false, true);
        return this;
    }

    private final HashSet<Long> excludeUserIds = new HashSet<>();
    public UserSelectorBottomSheet exceptUsers(long ...ids) {
        for (long id : ids)
            excludeUserIds.add(id);
        updateItems(false, true);
        return this;
    }
    public UserSelectorBottomSheet exceptUsers(Collection<Long> ids) {
        excludeUserIds.addAll(ids);
        updateItems(false, true);
        return this;
    }

    private Utilities.Callback2<Boolean, HashSet<Long>> onUsersSelectedListener;
    public UserSelectorBottomSheet setOnUsersSelector(Utilities.Callback2<Boolean, HashSet<Long>> listener) {
        onUsersSelectedListener = listener;
        return this;
    }

    // LoogriGram: addTONOption stood here, with the flag and the countdown it set.


    @Override
    protected CharSequence getTitle() {
        if (customTitle != null) {
            return customTitle;
        }
        return getString(R.string.VoipConferenceAddPeople);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        selectorAdapter = new SelectorAdapter(getContext(), false, resourcesProvider);
        selectorAdapter.setGreenSelector(true);
        return selectorAdapter;
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(searchField.getEditText());
        super.dismiss();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            AndroidUtilities.runOnUIThread(() -> initContacts(true));
        } else if (id == NotificationCenter.reloadHints) {
            AndroidUtilities.runOnUIThread(() -> initHints(true));
        } else if (id == NotificationCenter.userInfoDidLoad) {
            AndroidUtilities.runOnUIThread(() -> updateItems(true, true));
        }
    }

}
