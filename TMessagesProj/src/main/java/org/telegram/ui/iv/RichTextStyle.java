package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.FormattedDateSpan;
import org.telegram.ui.Components.SquigglyLinesSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;

// LoogriGram: this also turned the article editor's spannable text back into
// rich text and applied, tested and removed its styles, links and dates.
// The editor is deleted, as on desktop. What is left reads an article's rich
// text: for RichMessageLayout, and as the spannable RichHtml and
// RichMessageConvert write out when an article is copied or a draft holding
// one is read as text.
public class RichTextStyle {

    public static final int BOLD = TextStyleSpan.FLAG_STYLE_BOLD;
    public static final int ITALIC = TextStyleSpan.FLAG_STYLE_ITALIC;
    public static final int UNDERLINE = TextStyleSpan.FLAG_STYLE_UNDERLINE;
    public static final int STRIKE = TextStyleSpan.FLAG_STYLE_STRIKE;
    public static final int MONO = TextStyleSpan.FLAG_STYLE_MONO;
    public static final int SPOILER = TextStyleSpan.FLAG_STYLE_SPOILER;
    public static final int SUBSCRIPT = TextStyleSpan.FLAG_STYLE_SUBSCRIPT;
    public static final int SUPERSCRIPT = TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
    public static final int MARKED = TextStyleSpan.FLAG_STYLE_MARKED;

    public static int emojiOnlyCount(CharSequence text) {
        if (!(text instanceof Spanned) || text.length() == 0) return 0;
        final Spanned spanned = (Spanned) text;
        final ArrayList<Object> emojiSpans = new ArrayList<>();
        final AnimatedEmojiSpan[] animated = spanned.getSpans(0, text.length(), AnimatedEmojiSpan.class);
        for (AnimatedEmojiSpan span : animated) {
            emojiSpans.add(span);
        }
        final Emoji.EmojiSpan[] standard = spanned.getSpans(0, text.length(), Emoji.EmojiSpan.class);
        for (Emoji.EmojiSpan span : standard) {
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            boolean overlapsAnimated = false;
            for (AnimatedEmojiSpan animatedSpan : animated) {
                if (spanned.getSpanStart(animatedSpan) == start && spanned.getSpanEnd(animatedSpan) == end) {
                    overlapsAnimated = true;
                    break;
                }
            }
            if (!overlapsAnimated) emojiSpans.add(span);
        }
        if (emojiSpans.isEmpty()) return 0;
        for (int i = 0; i < text.length(); i++) {
            boolean covered = false;
            for (Object span : emojiSpans) {
                if (spanned.getSpanStart(span) <= i && spanned.getSpanEnd(span) > i) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return 0;
        }
        return emojiSpans.size();
    }

    public static CharSequence toSpannable(TL_iv.RichText rt) {
        return toSpannable(rt, null);
    }

    public static CharSequence toSpannable(TL_iv.RichText rt, TL_iv.PageBlock block) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, rt, 0, block);
        return sb;
    }

    private static void append(SpannableStringBuilder sb, TL_iv.RichText rt, int flags, TL_iv.PageBlock block) {
        if (rt == null || rt instanceof TL_iv.textEmpty) {
            return;
        }
        if (rt instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                append(sb, child, flags, block);
            }
            return;
        }
        if (rt instanceof TL_iv.textDiff) {
            TL_iv.textDiff diff = (TL_iv.textDiff) rt;
            boolean textEmpty = isEmpty(diff.text);
            boolean oldTextEmpty = isEmpty(diff.old_text);
            int start = sb.length();
            if (textEmpty) {
                append(sb, diff.old_text, flags, block);
                setDiffStyle(sb, start, TextStyleSpan.FLAG_STYLE_STRIKE_RED);
            } else {
                append(sb, diff.text, flags, block);
                if (oldTextEmpty) {
                    setDiffStyle(sb, start, TextStyleSpan.FLAG_STYLE_ACCENT);
                } else if (sb.length() > start) {
                    sb.setSpan(new SquigglyLinesSpan(), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return;
        }
        if (rt instanceof TL_iv.textPlain) {
            appendLeaf(sb, ((TL_iv.textPlain) rt).text, flags, block);
            return;
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            TL_iv.textCustomEmoji emoji = (TL_iv.textCustomEmoji) rt;
            String alt = emoji.alt == null || emoji.alt.isEmpty() ? "😀" : emoji.alt;
            int start = sb.length();
            sb.append(alt);
            AnimatedEmojiSpan span = new AnimatedEmojiSpan(emoji.document_id, null);
            span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
            sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (flags != 0) {
                sb.setSpan(spanFor(flags, block), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textUrl) {
            TL_iv.textUrl url = (TL_iv.textUrl) rt;
            int start = sb.length();
            append(sb, url.text, flags, block);
            if (sb.length() > start && url.url != null) {
                sb.setSpan(linkSpan(url.url), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textDate) {
            TL_iv.textDate date = (TL_iv.textDate) rt;
            int start = sb.length();
            append(sb, date.text, flags, block);
            if (sb.length() > start) {
                sb.setSpan(dateSpan(date, sb.subSequence(start, sb.length()).toString()), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textMath) {
            TL_iv.textMath math = (TL_iv.textMath) rt;
            int start = sb.length();
            sb.append(" ");
            MathSpan span = MathSpan.create(math.source, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), dp(4 + SharedConfig.fontSize));
            if (span != null) {
                sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.replace(start, sb.length(), math.source == null ? "" : math.source);
            }
            if (sb.length() > start && flags != 0) {
                sb.setSpan(spanFor(flags, block), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textButton) {
            final TL_iv.textButton button = (TL_iv.textButton) rt;
            final int start = sb.length();
            append(sb, button.text, flags, block);
            if (sb.length() > start && RichInlineButtonSpan.isSupported(button.type)) {
                sb.setSpan(new RichInlineButtonSpan(button), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        int childFlag = flagOf(rt);
        if (childFlag != 0) {
            append(sb, rt.text, flags | childFlag, block);
            return;
        }
        appendLeaf(sb, plainOf(rt), flags, block);
    }

    private static void setDiffStyle(SpannableStringBuilder sb, int start, int flags) {
        if (sb.length() > start) {
            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags = flags;
            sb.setSpan(new TextStyleSpan(run), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void appendLeaf(SpannableStringBuilder sb, String text, int flags, TL_iv.PageBlock block) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int start = sb.length();
        sb.append(text);
        if (flags != 0) {
            sb.setSpan(spanFor(flags, block), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int flagOf(TL_iv.RichText rt) {
        if (rt instanceof TL_iv.textBold) return BOLD;
        if (rt instanceof TL_iv.textItalic) return ITALIC;
        if (rt instanceof TL_iv.textUnderline) return UNDERLINE;
        if (rt instanceof TL_iv.textStrike) return STRIKE;
        if (rt instanceof TL_iv.textFixed) return MONO;
        if (rt instanceof TL_iv.textSpoiler) return SPOILER;
        if (rt instanceof TL_iv.textSubscript) return SUBSCRIPT;
        if (rt instanceof TL_iv.textSuperscript) return SUPERSCRIPT;
        if (rt instanceof TL_iv.textMarked) return MARKED;
        return 0;
    }

    public static String plainOf(TL_iv.RichText rt) {
        if (rt == null || rt instanceof TL_iv.textEmpty) {
            return "";
        }
        if (rt instanceof TL_iv.textPlain) {
            String t = ((TL_iv.textPlain) rt).text;
            return t == null ? "" : t;
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            String t = ((TL_iv.textCustomEmoji) rt).alt;
            return t == null ? "" : t;
        }
        if (rt instanceof TL_iv.textMath) {
            return " ";
        }
        if (rt instanceof TL_iv.textConcat) {
            StringBuilder s = new StringBuilder();
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                s.append(plainOf(child));
            }
            return s.toString();
        }
        if (rt instanceof TL_iv.textDiff) {
            TL_iv.textDiff diff = (TL_iv.textDiff) rt;
            String text = plainOf(diff.text);
            return isEmpty(diff.text) ? plainOf(diff.old_text) : text;
        }
        return plainOf(rt.text);
    }

    public static boolean isEmpty(TL_iv.RichText rt) {
        if (rt == null || rt instanceof TL_iv.textEmpty) {
            return true;
        }
        if (rt instanceof TL_iv.textPlain) {
            String text = ((TL_iv.textPlain) rt).text;
            return text == null || text.isEmpty();
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            return false;
        }
        if (rt instanceof TL_iv.textMath) {
            String source = ((TL_iv.textMath) rt).source;
            return source == null || source.isEmpty();
        }
        if (rt instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                if (!isEmpty(child)) {
                    return false;
                }
            }
            return true;
        }
        if (rt instanceof TL_iv.textDiff) {
            TL_iv.textDiff diff = (TL_iv.textDiff) rt;
            return isEmpty(diff.text) && isEmpty(diff.old_text);
        }
        return isEmpty(rt.text);
    }

    private static FormattedDateSpan dateSpan(TL_iv.textDate date, String text) {
        TLRPC.TL_messageEntityFormattedDate entity = new TLRPC.TL_messageEntityFormattedDate();
        entity.flags = date.flags;
        entity.date = date.date;
        entity.applyFlags();
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_URL;
        return new FormattedDateSpan(text, run, entity);
    }

    private static URLSpanReplacement linkSpan(String url) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = TextStyleSpan.FLAG_STYLE_TEXT_URL;
        return new URLSpanReplacement(url, run);
    }

    private static TextStyleSpan spanFor(int flags, TL_iv.PageBlock block) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flags;
        run.header = (
            block instanceof TL_iv.pageBlockTitle ||
            block instanceof TL_iv.pageBlockSubheader ||
            block instanceof TL_iv.pageBlockHeader ||
            block instanceof TL_iv.pageBlockHeading1 ||
            block instanceof TL_iv.pageBlockHeading2 ||
            block instanceof TL_iv.pageBlockHeading3 ||
            block instanceof TL_iv.pageBlockHeading4 ||
            block instanceof TL_iv.pageBlockHeading5 ||
            block instanceof TL_iv.pageBlockHeading6
        );
        return new TextStyleSpan(run);
    }
}
