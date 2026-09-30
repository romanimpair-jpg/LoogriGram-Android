package org.telegram.ui.iv;

import android.text.TextPaint;
import android.text.style.CharacterStyle;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;

// LoogriGram: this was the article editor's inline button, a replacement span
// drawing a TL_iv.textButton with RichMessageLayout's renderer while its text
// was edited. The editor is deleted, as on desktop. What is left marks a
// button's text in RichTextStyle's spannable so that RichHtml writes it back
// as a <button> when an article is copied. It stays a CharacterStyle, as the
// replacement span was, because RichHtml splits its runs at CharacterStyle
// boundaries.
public class RichInlineButtonSpan extends CharacterStyle {

    private final TL_iv.textButton button;

    public RichInlineButtonSpan(TL_iv.textButton button) {
        this.button = button;
    }

    public TL_iv.textButton getButton() {
        return button;
    }

    public static boolean isSupported(TL_keyboard.InlineButtonType type) {
        return type instanceof TL_keyboard.TL_inlineButtonTypeUrl
            || type instanceof TL_keyboard.TL_inlineButtonTypeCopy
            || type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
    }
}
