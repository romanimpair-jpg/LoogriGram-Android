package org.telegram.ui.ActionBar.theme;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.tgnet.TLRPC;

// LoogriGram: a key also named a collectible gift's theme by its slug
// (giftSlug, "gift_" saved strings, inputChatThemeUniqueGift). Those themes
// are not applied, as desktop's 42294064: a peer or a theme change carrying
// one reads as no theme, while the server's value is left as it is.
public class ThemeKey {
    public final String emoticon;

    private ThemeKey(String emoji) {
        this.emoticon = emoji;
    }

    public static ThemeKey ofEmoticon(String emoji) {
        return new ThemeKey(emoji);
    }

    public static ThemeKey of(TLRPC.TL_theme theme) {
        return new ThemeKey(theme.emoticon);
    }

    public static TLRPC.InputChatTheme toInputTheme(ThemeKey key) {
        if (key != null && !TextUtils.isEmpty(key.emoticon)) {
            TLRPC.Tl_inputChatTheme inputChatTheme = new TLRPC.Tl_inputChatTheme();
            inputChatTheme.emoticon = key.emoticon;
            return inputChatTheme;
        }

        return new TLRPC.Tl_inputChatThemeEmpty();
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(emoticon);
    }

    public String toSavedString() {
        if (emoticon != null) {
            return "emoticon_" + emoticon;
        }
        return null;
    }

    public static ThemeKey fromSavedString(String string) {
        if (string == null) {
            return null;
        }

        if (string.startsWith("gift_")) {
            // LoogriGram: saved by an older build for a collectible's theme. Gift
            // themes are not applied, as on desktop, so it reads as no theme.
            return null;
        }
        if (string.startsWith("emoticon_")) {
            return new ThemeKey(string.substring(9));
        }
        if (!TextUtils.isEmpty(string)) {
            return new ThemeKey(string);
        }

        return null;
    }

    @Nullable
    public static ThemeKey of(TLRPC.ChatTheme theme) {
        if (theme instanceof TLRPC.TL_chatTheme) {
            return new ThemeKey(((TLRPC.TL_chatTheme) theme).emoticon);
        }
        return null;
    }

    @Nullable
    public static ThemeKey of(TLRPC.InputChatTheme theme) {
        if (theme instanceof TLRPC.Tl_inputChatTheme) {
            return new ThemeKey(((TLRPC.Tl_inputChatTheme) theme).emoticon);
        }
        return null;
    }

    @Override
    public int hashCode() {
        return emoticon != null ? emoticon.hashCode() : 0;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ThemeKey) {
            ThemeKey key = (ThemeKey) obj;
            return TextUtils.equals(this.emoticon, key.emoticon);
        }

        return false;
    }

    public static boolean equals(@Nullable ThemeKey key1, @Nullable ThemeKey key2) {
        if (key1 == key2) {
            return true;
        }

        if (key1 == null || key2 == null) {
            return false;
        }

        return key1.equals(key2);
    }
}
