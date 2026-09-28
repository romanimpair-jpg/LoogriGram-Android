package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class CreateRtmpStreamBottomSheet extends BottomSheetWithRecyclerListView {

    private static final int CONTAINER_HEIGHT_DP = 72;

    public static void show(TLRPC.Peer peer, BaseFragment fragment, long dialogId, boolean hasFewPeers, JoinCallAlert.JoinCallAlertDelegate joinCallDelegate) {
        CreateRtmpStreamBottomSheet alert = new CreateRtmpStreamBottomSheet(fragment, peer, dialogId, hasFewPeers, joinCallDelegate);
        if (fragment != null && fragment.getParentActivity() != null) {
            fragment.showDialog(alert);
        } else {
            alert.show();
        }
    }

    private final JoinCallAlert.JoinCallAlertDelegate joinCallDelegate;
    private TLRPC.InputPeer selectAfterDismiss;
    private final boolean hasFewPeers;
    private String rtmpUrl;
    private String rtmpKey;
    private SpannableStringBuilder rtmpKeySpoiled;
    private UniversalAdapter adapter;

    private boolean hasButton;

    // LoogriGram: a second constructor showed the RTMP settings of a live
    // story being set up. Live stories are removed, as on desktop.

    public CreateRtmpStreamBottomSheet(BaseFragment fragment, TLRPC.Peer selectedPeer, long dialogId, boolean hasFewPeers, JoinCallAlert.JoinCallAlertDelegate joinCallDelegate) {
        super(fragment, false, false);
        this.topPadding = 0.26f;
        this.joinCallDelegate = joinCallDelegate;
        this.hasFewPeers = hasFewPeers;
        Context context = containerView.getContext();

        final boolean hasRevoke = ChatObject.isCreator(MessagesController.getInstance(currentAccount).getChat(-dialogId));
        hasButton = true;
        TextView startBtn = new TextView(context);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setEllipsize(TextUtils.TruncateAt.END);
        startBtn.setSingleLine(true);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        startBtn.setTypeface(AndroidUtilities.bold());
        startBtn.setText(getString(R.string.VoipChannelStartStreaming));
        startBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        startBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), 120)));
        containerView.addView(startBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, (hasRevoke ? 52 : 0) + 12));
        startBtn.setOnClickListener(view -> {
            selectAfterDismiss = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
            dismiss();
        });

        if (hasRevoke) {
            final ButtonWithCounterView revokeBtn = new ButtonWithCounterView(context, false, resourcesProvider);
            revokeBtn.setColor(Theme.getColor(Theme.key_fill_RedNormal));
            revokeBtn.text.setTypeface(AndroidUtilities.bold());
            revokeBtn.setText(getString(R.string.LiveStoryRTMPRevoke), false);
            revokeBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(context, resourcesProvider)
                    .setTitle(getString(R.string.LiveStoryRTMPRevokeTitle))
                    .setMessage(getString(R.string.LiveStoryRTMPRevokeText))
                    .setPositiveButton(getString(R.string.RevokeButton), (di, w) -> {
                        if (revokeBtn.isLoading()) return;
                        revokeBtn.setLoading(true);

                        final TL_phone.getGroupCallStreamRtmpUrl req = new TL_phone.getGroupCallStreamRtmpUrl();
                        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                        req.revoke = true;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            revokeBtn.setLoading(false);
                            if (response != null) {
                                if (response instanceof TL_phone.groupCallStreamRtmpUrl) {
                                    TL_phone.groupCallStreamRtmpUrl rtmpUrl = (TL_phone.groupCallStreamRtmpUrl) response;
                                    this.rtmpUrl = rtmpUrl.url;
                                    this.rtmpKey = rtmpUrl.key;
                                    this.rtmpKeySpoiled = new SpannableStringBuilder(rtmpKey);
                                    adapter.update(true);
                                }
                            }
                        }));
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .makeRed(AlertDialog.BUTTON_POSITIVE)
                    .show();
            });
            containerView.addView(revokeBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 12));
        }

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp((hasRevoke ? 52 : 0) + 72));

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);

        fixNavigationBar();
        updateTitle();

        final TL_phone.getGroupCallStreamRtmpUrl req = new TL_phone.getGroupCallStreamRtmpUrl();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.revoke = false;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                if (response instanceof TL_phone.groupCallStreamRtmpUrl) {
                    TL_phone.groupCallStreamRtmpUrl rtmpUrl = (TL_phone.groupCallStreamRtmpUrl) response;
                    this.rtmpUrl = rtmpUrl.url;
                    this.rtmpKey = rtmpUrl.key;

                    this.rtmpKeySpoiled = new SpannableStringBuilder(rtmpKey);
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                    run.start = 0;
                    run.end = rtmpKeySpoiled.length();
                    rtmpKeySpoiled.setSpan(new TextStyleSpan(run), 0, rtmpKeySpoiled.length(), 0);

                    adapter.update(false);
                }
            }
        }));
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (joinCallDelegate != null && selectAfterDismiss != null) {
            joinCallDelegate.didSelectChat(selectAfterDismiss, hasFewPeers, false, true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.Streaming);
    }

    @Override
    public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        return adapter;
    }

    private TopCell topCell;
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (topCell == null) {
            topCell = new TopCell(getContext(), resourcesProvider);
        }
        items.add(UItem.asCustom(topCell));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.VoipChatStreamSettings)));
        items.add(TextDetailCellFactory.of(rtmpUrl, getString(R.string.VoipChatStreamServerUrl), true));
        items.add(TextDetailCellFactory.of(rtmpKeySpoiled, getString(R.string.VoipChatStreamKey), false));
        items.add(UItem.asShadow(hasButton ? getString(R.string.VoipChatStreamWithAnotherAppDescription) : null));
    }

    private static class TopCell extends LinearLayout {

        public TopCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);

            RLottieImageView imageView = new RLottieImageView(context);
            imageView.setAutoRepeat(true);
            imageView.setAnimation(R.raw.utyan_streaming, 112, 112);
            imageView.playAnimation();
            addView(imageView, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 24, 0, 0));

            TextView title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(LocaleController.formatString(R.string.Streaming));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 7));

            TextView description = new TextView(context);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            description.setText(LocaleController.formatString(R.string.VoipStreamStart));
            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 28, 0, 28, 17));
        }
    }

    public static class TextDetailCellFactory extends UItem.UItemFactory<TextDetailCell> {
        static { setup(new TextDetailCellFactory()); }
        @Override
        public TextDetailCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            TextDetailCell view = new TextDetailCell(context, resourcesProvider, true, false);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_copy).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            view.setImage(drawable);
            view.setImageClickListener(v -> copyRtmpValue(context, view.textView.getText().toString()));
            return view;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((TextDetailCell) view).setTextAndValue(item.text, item.textValue, !item.hideDivider);
            if (item.text instanceof SpannableStringBuilder) {
                ((TextDetailCell) view).textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                ((TextDetailCell) view).textView.setTranslationY(dp(2));
                ((TextDetailCell) view).textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            }
        }

        public static UItem of(CharSequence text, CharSequence value, boolean divider) {
            UItem item = UItem.ofFactory(TextDetailCellFactory.class);
            item.text = text;
            item.textValue = value;
            item.hideDivider = !divider;
            item.enabled = false;
            return item;
        }

        private void copyRtmpValue(Context context, String value) {
            AndroidUtilities.addToClipboard(value);
            if (AndroidUtilities.shouldShowClipboardToast()) {
                Toast.makeText(context, getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
