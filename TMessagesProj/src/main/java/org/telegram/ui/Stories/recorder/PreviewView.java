package org.telegram.ui.Stories.recorder;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.SparseIntArray;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.theme.ThemeKey;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.io.File;

// LoogriGram: this was the story editor's preview, with its video player,
// collage, round video and crop. Stories are not posted here; what is left
// are the chat wallpaper helpers that video conversion, chat and theme
// previews borrow.
public class PreviewView {

    public static Drawable getBackgroundDrawable(Drawable prevDrawable, int currentAccount, long dialogId, boolean isDark) {
        if (dialogId == Long.MIN_VALUE) {
            return null;
        }
        TLRPC.WallPaper wallpaper = null;
        if (dialogId >= 0) {
            TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            if (userFull != null) {
                wallpaper = userFull.wallpaper;
            }
        } else {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            if (chatFull != null) {
                wallpaper = chatFull.wallpaper;
            }
        }
        return getBackgroundDrawable(prevDrawable, currentAccount, wallpaper, isDark);
    }

    public static Drawable getBackgroundDrawable(Drawable prevDrawable, int currentAccount, TLRPC.WallPaper wallpaper, boolean isDark) {
        if (wallpaper != null && TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(wallpaper))) {
            return ChatBackgroundDrawable.getOrCreate(prevDrawable, wallpaper, isDark);
        }

        EmojiThemes theme = null;
        if (wallpaper != null && wallpaper.settings != null) {
            theme = ChatThemeController.getInstance(currentAccount).getTheme(ThemeKey.ofEmoticon(wallpaper.settings.emoticon));
        }
        if (theme != null) {
            return getBackgroundDrawableFromTheme(currentAccount, theme, 0, isDark);
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }
        if (isDark) {
            themeInfo = Theme.getTheme(nightThemeName);
        } else {
            themeInfo = Theme.getTheme(dayThemeName);
        }
        SparseIntArray currentColors = new SparseIntArray();
        final String[] wallpaperLink = new String[1];
        final SparseIntArray themeColors;
        if (themeInfo.assetName != null) {
            themeColors = Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink);
        } else {
            themeColors = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
        }
        int[] defaultColors = Theme.getDefaultColors();
        if (defaultColors != null) {
            for (int i = 0; i < defaultColors.length; ++i) {
                currentColors.put(i, defaultColors[i]);
            }
        }
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (accent != null) {
            accent.fillAccentColors(themeColors, currentColors);
        } else if (themeColors != null) {
            for (int i = 0; i < themeColors.size(); ++i) {
                currentColors.put(themeColors.keyAt(i), themeColors.valueAt(i));
            }
        }
        Theme.BackgroundDrawableSettings bg = Theme.createBackgroundDrawable(themeInfo, currentColors, wallpaperLink[0], 0, true);
        return bg.themedWallpaper != null ? bg.themedWallpaper : bg.wallpaper;
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, String emoticon, boolean isDark) {
        return getBackgroundDrawableFromTheme(currentAccount, emoticon, isDark, false);
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, String emoticon, boolean isDark, boolean preview) {
        EmojiThemes theme = ChatThemeController.getInstance(currentAccount).getTheme(ThemeKey.ofEmoticon(emoticon));
        if (theme == null) {
            return Theme.getCachedWallpaper();
        }
        return getBackgroundDrawableFromTheme(currentAccount, theme, 0, isDark, preview);
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, EmojiThemes chatTheme, int prevPhase, boolean isDark) {
        return getBackgroundDrawableFromTheme(currentAccount, chatTheme, prevPhase, isDark, false);
    }

    public static Drawable getBackgroundDrawableFromTheme(int currentAccount, EmojiThemes chatTheme, int prevPhase, boolean isDark, boolean preview) {
        Drawable drawable;
        if (chatTheme.isAnyStub()) {
            Theme.ThemeInfo themeInfo = EmojiThemes.getDefaultThemeInfo(isDark);
            SparseIntArray currentColors = chatTheme.getPreviewColors(currentAccount, isDark ? 1 : 0);
            String wallpaperLink = chatTheme.getWallpaperLink(isDark ? 1 : 0);
            Theme.BackgroundDrawableSettings settings = Theme.createBackgroundDrawable(themeInfo, currentColors, wallpaperLink, prevPhase, false);
            drawable = settings.wallpaper;
            drawable = new ColorDrawable(Color.BLACK);
        } else {
            SparseIntArray currentColors = chatTheme.getPreviewColors(currentAccount, isDark ? 1 : 0);
            int backgroundColor = currentColors.get(Theme.key_chat_wallpaper, Theme.getColor(Theme.key_chat_wallpaper));
            int gradientColor1 = currentColors.get(Theme.key_chat_wallpaper_gradient_to1, Theme.getColor(Theme.key_chat_wallpaper_gradient_to1));
            int gradientColor2 = currentColors.get(Theme.key_chat_wallpaper_gradient_to2, Theme.getColor(Theme.key_chat_wallpaper_gradient_to2));
            int gradientColor3 = currentColors.get(Theme.key_chat_wallpaper_gradient_to3, Theme.getColor(Theme.key_chat_wallpaper_gradient_to3));

            MotionBackgroundDrawable motionDrawable = new MotionBackgroundDrawable();
            motionDrawable.isPreview = preview;
            motionDrawable.setPatternBitmap(chatTheme.getWallpaper(isDark ? 1 : 0).settings.intensity);
            motionDrawable.setColors(backgroundColor, gradientColor1, gradientColor2, gradientColor3, 0,true);
            motionDrawable.setPhase(prevPhase);
            int patternColor = motionDrawable.getPatternColor();
            final boolean isDarkTheme = isDark;
            chatTheme.loadWallpaper(isDark ? 1 : 0, pair -> {
                if (pair == null) {
                    return;
                }
                long themeId = pair.first;
                Bitmap bitmap = pair.second.bitmap;
                if (themeId == chatTheme.getThemeId(isDark ? 1 : 0) && bitmap != null) {
                    int intensity = chatTheme.getWallpaper(isDarkTheme ? 1 : 0).settings.intensity;
                    motionDrawable.setPatternBitmap(intensity, bitmap);
                    motionDrawable.setPatternColorFilter(patternColor);
                    motionDrawable.setPatternAlpha(1f);
                }
            });
            drawable = motionDrawable;
        }
        return drawable;
    }
}
