package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AvailableReactionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;

import java.util.ArrayList;
import java.util.List;

public class ReactionsDoubleTapManageActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private LinearLayout contentView;
    private RecyclerListView listView;
    private RecyclerView.Adapter listAdapter;

    int previewRow;
    int infoRow;
    int reactionsStartRow = -1;
    int rowCount;

    public ReactionsDoubleTapManageActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.reactionsDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.Reactions));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout linaerLayout = new LinearLayout(context);
        linaerLayout.setOrientation(LinearLayout.VERTICAL);

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        ((DefaultItemAnimator)listView.getItemAnimator()).setSupportsChangeAnimations(false);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(listAdapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == 3 || holder.getItemViewType() == 2;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case 0:
                        ThemePreviewMessagesCell messagesCell = new ThemePreviewMessagesCell(context, parentLayout, ThemePreviewMessagesCell.TYPE_REACTIONS_DOUBLE_TAP);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        }
                        messagesCell.fragment = ReactionsDoubleTapManageActivity.this;
                        view = messagesCell;
                        break;
                    case 2:
                        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                        cell.setText(LocaleController.getString(R.string.DoubleTapPreviewRational));
                        view = cell;
                        break;
                    case 4:
                        view = new View(context) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(
                                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(16), MeasureSpec.EXACTLY)
                                );
                            }
                        };
                        view.setTag(RecyclerListView.TAG_NOT_SECTION);
                        break;
                    default:
                    case 1: {
                        view = new AvailableReactionCell(context, true);
                    }
                    break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (getItemViewType(position)) {
                    case 1:
                        AvailableReactionCell reactionCell = (AvailableReactionCell) holder.itemView;
                        TLRPC.TL_availableReaction react = getAvailableReactions().get(position - reactionsStartRow);
                        reactionCell.bind(react, react.reaction.contains(MediaDataController.getInstance(currentAccount).getDoubleTapReaction()));
                        break;
                }
            }

            @Override
            public int getItemCount() {
                return rowCount + getAvailableReactions().size() + 1;
            }

            @Override
            public int getItemViewType(int position) {
                if (position == previewRow) {
                    return 0;
                }
                if (position == infoRow) {
                    return 2;
                }
                if (position == getItemCount() - 1) {
                    return 4;
                }
                return 1;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof AvailableReactionCell) {
                AvailableReactionCell cell = (AvailableReactionCell) view;
                MediaDataController.getInstance(currentAccount).setDoubleTapReaction(cell.react.reaction);
                listView.getAdapter().notifyItemRangeChanged(0, listView.getAdapter().getItemCount());
            }
        });
        linaerLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = contentView = linaerLayout;

        updateColors();
        updateRows();

        return contentView;
    }

    private void updateRows() {
        rowCount = 0;
        previewRow = rowCount++;
        infoRow = rowCount++;
        // LoogriGram: a Premium account got one row instead, to pick any emoji
        // as the double-tap reaction in an emoji picker, and the list of
        // reactions below was left out for it.
        reactionsStartRow = rowCount;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.reactionsDidLoad);
    }

    // LoogriGram: an account without Premium is not offered Premium's
    // reactions at all, rather than offered them padlocked.
    private List<TLRPC.TL_availableReaction> getAvailableReactions() {
        final List<TLRPC.TL_availableReaction> all = getMediaDataController().getReactionsList();
        final ArrayList<TLRPC.TL_availableReaction> free = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); ++i) {
            if (!all.get(i).premium) {
                free.add(all.get(i));
            }
        }
        return free;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector,
                Theme.key_windowBackgroundGray,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_text_RedRegular,
                Theme.key_windowBackgroundChecked,
                Theme.key_windowBackgroundCheckText,
                Theme.key_switchTrackBlue,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listAdapter.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.reactionsDidLoad) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }
}