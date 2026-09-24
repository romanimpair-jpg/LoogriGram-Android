package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ThemeSmallPreviewView;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * LoogriGram: a strip of chat themes to pick from.
 *
 * This was ChannelColorActivity.ThemeChooser. That screen - a channel's or
 * group's Appearance, every option on it locked behind a boost level - is
 * deleted, but the story recorder's StoryThemeSheet picks a story's theme
 * with this strip, so it moves out on its own. Upstream's code, unchanged
 * apart from being de-nested.
 */
public class ThemeChooser extends FrameLayout {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    public final List<ChatThemeBottomSheet.ChatThemeItem> items = new ArrayList<>();
    private final RecyclerListView listView;
    private FlickerLoadingView progressView;
    private boolean withRemovedStub;

    private final RecyclerListView.SelectionAdapter adapter;

    private boolean dataLoaded;

    private Utilities.Callback<String> onEmoticonSelected;
    private String currentEmoticon;

    public void setWithRemovedStub(boolean withRemovedStub) {
        this.withRemovedStub = withRemovedStub;
    }

    public void setOnEmoticonSelected(Utilities.Callback<String> callback) {
        onEmoticonSelected = callback;
    }

    public void setSelectedEmoticon(String emoticon, boolean animated) {
        currentEmoticon = emoticon;

        int selectedPosition = -1;
        for (int i = 0; i < items.size(); ++i) {
            ChatThemeBottomSheet.ChatThemeItem item = items.get(i);
            item.isSelected = TextUtils.equals(currentEmoticon, item.getEmoticon()) || TextUtils.isEmpty(emoticon) && item.chatTheme.showAsDefaultStub;
            if (item.isSelected) {
                selectedPosition = i;
            }
        }
        if (selectedPosition >= 0 && !animated && listView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(selectedPosition, (AndroidUtilities.displaySize.x - dp(83)) / 2);
        }
        updateSelected();
    }

    private TLRPC.WallPaper fallbackWallpaper;
    public void setGalleryWallpaper(TLRPC.WallPaper wallPaper) {
        this.fallbackWallpaper = wallPaper;
        AndroidUtilities.forEachViews(listView, child -> {
            if (child instanceof ThemeSmallPreviewView) {
                ((ThemeSmallPreviewView) child).setFallbackWallpaper(((ThemeSmallPreviewView) child).chatThemeItem.chatTheme.showAsRemovedStub ? null : fallbackWallpaper);
            }
        });
        if (fallbackWallpaper != null && (items.isEmpty() || items.get(0).chatTheme.showAsDefaultStub) && withRemovedStub) {
            items.add(0, new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createChatThemesRemoved(currentAccount)));
            adapter.notifyDataSetChanged();
        }
    }

    private void updateSelected() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof ThemeSmallPreviewView) {
                int position = listView.getChildAdapterPosition(child);
                if (position >= 0 && position < items.size()) {
                    ChatThemeBottomSheet.ChatThemeItem item = items.get(position);
                    ((ThemeSmallPreviewView) child).setSelected(item.isSelected, true);
                }
            }
        }
    }

    public boolean isDark() {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    public ThemeChooser(Context context, boolean grid, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        if (!grid) {
            progressView = new FlickerLoadingView(getContext(), resourcesProvider);
            progressView.setViewType(FlickerLoadingView.CHAT_THEMES_TYPE);
            progressView.setVisibility(View.VISIBLE);
            addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 16, 13, 16, 6));
        }

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        listView.setClipToPadding(false);
        listView.setPadding(dp(16), dp(13), dp(16), dp(grid ? 13 : 6));
        if (grid) {
            listView.setHasFixedSize(false);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return 1;
                }
            });
            listView.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            listView.setLayoutManager(layoutManager);
            listView.setAlpha(0f);
        }
        listView.setAdapter(adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new ThemeSmallPreviewView(parent.getContext(), currentAccount, resourcesProvider, grid ? ThemeSmallPreviewView.TYPE_GRID_CHANNEL : ThemeSmallPreviewView.TYPE_CHANNEL) {
                    @Override
                    protected String noThemeString() {
                        return LocaleController.getString(R.string.ChannelNoWallpaper);
                    }

                    @Override
                    protected int noThemeStringTextSize() {
                        if (!grid) {
                            return 13;
                        }
                        return super.noThemeStringTextSize();
                    }
                });
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ThemeSmallPreviewView view = (ThemeSmallPreviewView) holder.itemView;
                Theme.ThemeInfo themeInfo = items.get(position).chatTheme.getThemeInfo(items.get(position).themeIndex);
                if (themeInfo != null && themeInfo.pathToFile != null && !themeInfo.previewParsed) {
                    File file = new File(themeInfo.pathToFile);
                    boolean fileExists = file.exists();
                    if (fileExists) {
                        parseTheme(themeInfo);
                    }
                }
                ChatThemeBottomSheet.ChatThemeItem newItem = items.get(position);
                view.setEnabled(true);
                view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                view.setItem(newItem, false);
                view.setSelected(newItem.isSelected, false);
                view.setFallbackWallpaper(newItem.chatTheme.showAsRemovedStub ? null : fallbackWallpaper);
            }

            @Override
            public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
                final int position = holder.getAdapterPosition();
                if (position < 0 || position >= items.size()) {
                    return;
                }
                ChatThemeBottomSheet.ChatThemeItem newItem = items.get(position);
                ((ThemeSmallPreviewView) holder.itemView).setSelected(newItem.isSelected, false);
                ((ThemeSmallPreviewView) holder.itemView).setFallbackWallpaper(newItem.chatTheme.showAsRemovedStub ? null : fallbackWallpaper);
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, grid ? LayoutHelper.MATCH_PARENT : 13 + 111 + 6));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            ChatThemeBottomSheet.ChatThemeItem thisItem = items.get(position);
            if (!grid) {
                setSelectedEmoticon(thisItem.getEmoticon(), true);
                if (view.getLeft() < listView.getPaddingLeft() + dp(24)) {
                    listView.smoothScrollBy((int) -(listView.getPaddingLeft() + dp(48) - view.getLeft()), 0);
                } else if (view.getLeft() + view.getWidth() > listView.getMeasuredWidth() - listView.getPaddingRight() - dp(24)) {
                    listView.smoothScrollBy((int) (view.getLeft() + view.getWidth() - (listView.getMeasuredWidth() - listView.getPaddingRight() - dp(48))), 0);
                }
            }
            if (onEmoticonSelected != null) {
                onEmoticonSelected.run(thisItem.getEmoticon());
            }
        });

        ChatThemeController chatThemeController = ChatThemeController.getInstance(currentAccount);
        chatThemeController.preloadAllWallpaperThumbs(true);
        chatThemeController.preloadAllWallpaperThumbs(false);
        chatThemeController.preloadAllWallpaperImages(true);
        chatThemeController.preloadAllWallpaperImages(false);
        chatThemeController.requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
//                    if (result != null && !result.isEmpty()) {
//                        themeDelegate.setCachedThemes(result);
//                    }
                NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                    onDataLoaded(result);
                });
            }

            @Override
            public void onError(TLRPC.TL_error error) {
                Toast.makeText(getContext(), error.text, Toast.LENGTH_SHORT).show();
            }
        }, true);

        updateState(false);
    }

    public void updateColors() {
        final boolean isDark = isDark();
        for (int i = 0; i < items.size(); ++i) {
            ChatThemeBottomSheet.ChatThemeItem item = items.get(i);
            item.themeIndex = isDark ? 1 : 0;
        }
        AndroidUtilities.forEachViews(listView, view -> {
            ((ThemeSmallPreviewView) view).setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider));
        });
        adapter.notifyDataSetChanged();
    }

    private void onDataLoaded(List<EmojiThemes> result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        dataLoaded = true;
        items.clear();

        ChatThemeBottomSheet.ChatThemeItem noThemeItem = new ChatThemeBottomSheet.ChatThemeItem(result.get(0));
        items.add(0, noThemeItem);

        if (fallbackWallpaper != null && withRemovedStub) {
            items.add(0, new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createChatThemesRemoved(currentAccount)));
        }

        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        for (int i = 1; i < result.size(); ++i) {
            EmojiThemes chatTheme = result.get(i);
            ChatThemeBottomSheet.ChatThemeItem item = new ChatThemeBottomSheet.ChatThemeItem(chatTheme);

            chatTheme.loadPreviewColors(currentAccount);

            item.themeIndex = isDark ? 1 : 0;
            items.add(item);
        }

        int selectedPosition = -1;
        for (int i = 0; i < items.size(); ++i) {
            ChatThemeBottomSheet.ChatThemeItem item = items.get(i);
            item.isSelected = TextUtils.equals(currentEmoticon, item.getEmoticon()) || TextUtils.isEmpty(currentEmoticon) && item.chatTheme.showAsDefaultStub;
            if (item.isSelected) {
                selectedPosition = i;
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

//            resetToPrimaryState(false);
        listView.animate().alpha(1f).setDuration(150).start();
        updateState(true);

        if (selectedPosition >= 0 && listView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(selectedPosition, (AndroidUtilities.displaySize.x - dp(83)) / 2);
        }
    }

    private final HashMap<String, Theme.ThemeInfo> loadingThemes = new HashMap<>();
    private final HashMap<Theme.ThemeInfo, String> loadingWallpapers = new HashMap<>();
    private boolean parseTheme(Theme.ThemeInfo themeInfo) {
        if (themeInfo == null || themeInfo.pathToFile == null) {
            return false;
        }
        boolean finished = false;
        File file = new File(themeInfo.pathToFile);
        try (FileInputStream stream = new FileInputStream(file)) {
            int currentPosition = 0;
            int idx;
            int read;
            int linesRead = 0;
            while ((read = stream.read(ThemesHorizontalListCell.bytes)) != -1) {
                int previousPosition = currentPosition;
                int start = 0;
                for (int a = 0; a < read; a++) {
                    if (ThemesHorizontalListCell.bytes[a] == '\n') {
                        linesRead++;
                        int len = a - start + 1;
                        String line = new String(ThemesHorizontalListCell.bytes, start, len - 1, "UTF-8");
                        if (line.startsWith("WLS=")) {
                            String wallpaperLink = line.substring(4);
                            Uri uri = Uri.parse(wallpaperLink);
                            themeInfo.slug = uri.getQueryParameter("slug");
                            themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink) + ".wp").getAbsolutePath();

                            String mode = uri.getQueryParameter("mode");
                            if (mode != null) {
                                mode = mode.toLowerCase();
                                String[] modes = mode.split(" ");
                                if (modes != null && modes.length > 0) {
                                    for (int b = 0; b < modes.length; b++) {
                                        if ("blur".equals(modes[b])) {
                                            themeInfo.isBlured = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            String pattern = uri.getQueryParameter("pattern");
                            if (!TextUtils.isEmpty(pattern)) {
                                try {
                                    String bgColor = uri.getQueryParameter("bg_color");
                                    if (!TextUtils.isEmpty(bgColor)) {
                                        themeInfo.patternBgColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                        if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                                            themeInfo.patternBgGradientColor1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                        }
                                        if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                            themeInfo.patternBgGradientColor2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                        }
                                        if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                            themeInfo.patternBgGradientColor3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                        }
                                    }
                                } catch (Exception ignore) {

                                }
                                try {
                                    String rotation = uri.getQueryParameter("rotation");
                                    if (!TextUtils.isEmpty(rotation)) {
                                        themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                                    }
                                } catch (Exception ignore) {

                                }
                                String intensity = uri.getQueryParameter("intensity");
                                if (!TextUtils.isEmpty(intensity)) {
                                    themeInfo.patternIntensity = Utilities.parseInt(intensity);
                                }
                                if (themeInfo.patternIntensity == 0) {
                                    themeInfo.patternIntensity = 50;
                                }
                            }
                        } else if (line.startsWith("WPS")) {
                            themeInfo.previewWallpaperOffset = currentPosition + len;
                            finished = true;
                            break;
                        } else {
                            if ((idx = line.indexOf('=')) != -1) {
                                int key = ThemeColors.stringKeyToInt(line.substring(0, idx));
                                if (key == Theme.key_chat_inBubble || key == Theme.key_chat_outBubble || key == Theme.key_chat_wallpaper || key == Theme.key_chat_wallpaper_gradient_to1 || key == Theme.key_chat_wallpaper_gradient_to2 || key == Theme.key_chat_wallpaper_gradient_to3) {
                                    String param = line.substring(idx + 1);
                                    int value;
                                    if (param.length() > 0 && param.charAt(0) == '#') {
                                        try {
                                            value = Color.parseColor(param);
                                        } catch (Exception ignore) {
                                            value = Utilities.parseInt(param);
                                        }
                                    } else {
                                        value = Utilities.parseInt(param);
                                    }
                                    if (key == Theme.key_chat_inBubble) {
                                        themeInfo.setPreviewInColor(value);
                                    } else if (key == Theme.key_chat_outBubble) {
                                        themeInfo.setPreviewOutColor(value);
                                    } else if (key == Theme.key_chat_wallpaper) {
                                        themeInfo.setPreviewBackgroundColor(value);
                                    } else if (key == Theme.key_chat_wallpaper_gradient_to1) {
                                        themeInfo.previewBackgroundGradientColor1 = value;
                                    } else if (key == Theme.key_chat_wallpaper_gradient_to2) {
                                        themeInfo.previewBackgroundGradientColor2 = value;
                                    } else if (key == Theme.key_chat_wallpaper_gradient_to3) {
                                        themeInfo.previewBackgroundGradientColor3 = value;
                                    }
                                }
                            }
                        }
                        start += len;
                        currentPosition += len;
                    }
                }
                if (finished || previousPosition == currentPosition) {
                    break;
                }
                stream.getChannel().position(currentPosition);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        if (themeInfo.pathToWallpaper != null && !themeInfo.badWallpaper) {
            file = new File(themeInfo.pathToWallpaper);
            if (!file.exists()) {
                if (!loadingWallpapers.containsKey(themeInfo)) {
                    loadingWallpapers.put(themeInfo, themeInfo.slug);
                    TL_account.getWallPaper req = new TL_account.getWallPaper();
                    TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                    inputWallPaperSlug.slug = themeInfo.slug;
                    req.wallpaper = inputWallPaperSlug;
                    ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_wallPaper) {
                            TLRPC.WallPaper wallPaper = (TLRPC.WallPaper) response;
                            String name = FileLoader.getAttachFileName(wallPaper.document);
                            if (!loadingThemes.containsKey(name)) {
                                loadingThemes.put(name, themeInfo);
                                FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, FileLoader.PRIORITY_NORMAL, 1);
                            }
                        } else {
                            themeInfo.badWallpaper = true;
                        }
                    }));
                }
                return false;
            }
        }
        themeInfo.previewParsed = true;
        return true;
    }

    private void updateState(boolean animated) {
        if (!dataLoaded) {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 1f, true, animated);
        } else {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 1f, true, animated);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }
}
