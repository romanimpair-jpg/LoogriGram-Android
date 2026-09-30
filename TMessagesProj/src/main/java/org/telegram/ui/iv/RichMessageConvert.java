package org.telegram.ui.iv;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.CodeHighlighting;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;
import java.util.List;

// LoogriGram: this also turned the article editor's rows and the message
// field's text into article blocks and back, and judged what a conversion
// would lose. The editor is deleted, as on desktop; what is left is
// upstream's article-to-plain-message conversion, which reads a cloud draft
// holding an article as its text (MediaDataController.readRichDraftAsText).
public class RichMessageConvert {

    public static CharSequence toCharSequence(TL_iv.RichMessage msg) {
        return blocksToCharSequence(msg == null ? null : msg.blocks);
    }

    public static CharSequence blocksToCharSequence(List<TL_iv.PageBlock> blocks) {
        final ArrayList<CharSequence> parts = new ArrayList<>();
        collectBlocks(parts, blocks);
        return join(parts);
    }

    private static void collectBlocks(ArrayList<CharSequence> out, List<TL_iv.PageBlock> blocks) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            final CharSequence cs = renderBlock(block);
            if (cs != null) out.add(cs);
        }
    }

    private static CharSequence renderBlock(TL_iv.PageBlock block) {
        if (block == null) return null;

        if (block instanceof TL_iv.pageBlockPreformatted) {
            final SpannableStringBuilder sb = new SpannableStringBuilder(RichTextStyle.toSpannable(block.text, block));
            final String lng = ((TL_iv.pageBlockPreformatted) block).language;
            sb.setSpan(new CodeHighlighting.Span(true, 0, null, lng, sb.toString()), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }

        if (block instanceof TL_iv.pageBlockBlockquote || block instanceof TL_iv.pageBlockPullquote) {
            final TL_iv.RichText caption = block instanceof TL_iv.pageBlockBlockquote
                ? ((TL_iv.pageBlockBlockquote) block).caption
                : ((TL_iv.pageBlockPullquote) block).caption;
            return quoted(RichTextStyle.toSpannable(block.text, block), caption);
        }

        if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
            final TL_iv.pageBlockBlockquoteBlocks q = (TL_iv.pageBlockBlockquoteBlocks) block;
            final ArrayList<CharSequence> inner = new ArrayList<>();
            collectBlocks(inner, q.blocks);
            return quoted(join(inner), q.caption);
        }

        if (block instanceof TL_iv.pageBlockDetails) {
            final TL_iv.pageBlockDetails d = (TL_iv.pageBlockDetails) block;
            final ArrayList<CharSequence> parts = new ArrayList<>();
            final CharSequence title = RichTextStyle.toSpannable(d.title);
            if (!TextUtils.isEmpty(title)) parts.add(title);
            collectBlocks(parts, d.blocks);
            return parts.isEmpty() ? null : join(parts);
        }

        if (block instanceof TL_iv.pageBlockList) {
            return renderList(((TL_iv.pageBlockList) block).items);
        }
        if (block instanceof TL_iv.pageBlockOrderedList) {
            return renderOrderedList(((TL_iv.pageBlockOrderedList) block).items);
        }

        if (block instanceof TL_iv.pageBlockTable) {
            return renderTable((TL_iv.pageBlockTable) block);
        }

        if (block instanceof TL_iv.pageBlockMath) {
            final String source = ((TL_iv.pageBlockMath) block).source;
            if (TextUtils.isEmpty(source)) return null;
            return mono(new SpannableStringBuilder(source));
        }

        if (block instanceof TL_iv.pageBlockDivider) {
            return "——————————";
        }

        if (isHeading(block)) {
            final CharSequence t = bold(RichTextStyle.toSpannable(block.text, block));
            return TextUtils.isEmpty(t) ? null : t;
        }

        if (block instanceof TL_iv.pageBlockAuthorDate) {
            return RichTextStyle.toSpannable(((TL_iv.pageBlockAuthorDate) block).author);
        }

        if (block instanceof TL_iv.pageBlockParagraph
                || block instanceof TL_iv.pageBlockFooter
                || block instanceof TL_iv.pageBlockKicker
                || block instanceof TL_iv.pageBlockThinking) {
            return RichTextStyle.toSpannable(block.text, block);
        }

        return captionText(block);
    }

    private static CharSequence renderList(ArrayList<TL_iv.PageListItem> items) {
        if (items == null || items.isEmpty()) return null;
        final ArrayList<CharSequence> lines = new ArrayList<>();
        for (TL_iv.PageListItem item : items) {
            final CharSequence text = listItemText(item);
            if (text == null) continue;
            final String prefix = item.checkbox ? (item.checked ? "☑  " : "☐  ") : "•  ";
            lines.add(prefixed(prefix, text));
        }
        return lines.isEmpty() ? null : join(lines);
    }

    private static CharSequence renderOrderedList(ArrayList<TL_iv.PageListOrderedItem> items) {
        if (items == null || items.isEmpty()) return null;
        final ArrayList<CharSequence> lines = new ArrayList<>();
        int counter = 1;
        for (TL_iv.PageListOrderedItem item : items) {
            final CharSequence text = orderedItemText(item);
            if (text == null) { counter++; continue; }
            final String num = !TextUtils.isEmpty(item.num) ? item.num : String.valueOf(counter);
            lines.add(prefixed(num + ".  ", text));
            counter++;
        }
        return lines.isEmpty() ? null : join(lines);
    }

    private static CharSequence renderTable(TL_iv.pageBlockTable table) {
        final ArrayList<CharSequence> lines = new ArrayList<>();
        final CharSequence title = RichTextStyle.toSpannable(table.title);
        if (!TextUtils.isEmpty(title)) lines.add(title);
        if (table.rows != null) {
            for (TL_iv.pageTableRow row : table.rows) {
                if (row.cells == null || row.cells.isEmpty()) continue;
                final SpannableStringBuilder line = new SpannableStringBuilder();
                for (int c = 0; c < row.cells.size(); c++) {
                    if (c > 0) line.append("  |  ");
                    line.append(RichTextStyle.toSpannable(row.cells.get(c).text));
                }
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : join(lines);
    }

    private static CharSequence listItemText(TL_iv.PageListItem item) {
        if (item instanceof TL_iv.TL_pageListItemText) {
            return RichTextStyle.toSpannable(((TL_iv.TL_pageListItemText) item).text);
        }
        if (item instanceof TL_iv.TL_pageListItemBlocks) {
            return blocksToCharSequence(((TL_iv.TL_pageListItemBlocks) item).blocks);
        }
        return null;
    }

    private static CharSequence orderedItemText(TL_iv.PageListOrderedItem item) {
        if (item instanceof TL_iv.TL_pageListOrderedItemText) {
            return RichTextStyle.toSpannable(((TL_iv.TL_pageListOrderedItemText) item).text);
        }
        if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
            return blocksToCharSequence(((TL_iv.TL_pageListOrderedItemBlocks) item).blocks);
        }
        return null;
    }

    private static CharSequence captionText(TL_iv.PageBlock block) {
        if (block.caption == null) return null;
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        final CharSequence text = RichTextStyle.toSpannable(block.caption.text);
        if (!TextUtils.isEmpty(text)) sb.append(text);
        final CharSequence credit = RichTextStyle.toSpannable(block.caption.credit);
        if (!TextUtils.isEmpty(credit)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(credit);
        }
        return sb.length() > 0 ? sb : null;
    }

    private static CharSequence quoted(CharSequence text, TL_iv.RichText caption) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(text == null ? "" : text);
        final CharSequence author = RichTextStyle.toSpannable(caption);
        if (!TextUtils.isEmpty(author)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("— ").append(author);
        }
        if (sb.length() == 0) return null;
        QuoteSpan.putQuote(sb, 0, sb.length(), false);
        return sb;
    }

    private static CharSequence prefixed(String prefix, CharSequence text) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        sb.append(text == null ? "" : text);
        return sb;
    }

    private static CharSequence bold(CharSequence text) {
        return styled(text, TextStyleSpan.FLAG_STYLE_BOLD);
    }

    private static CharSequence mono(CharSequence text) {
        return styled(text, TextStyleSpan.FLAG_STYLE_MONO);
    }

    private static CharSequence styled(CharSequence text, int flags) {
        if (TextUtils.isEmpty(text)) return text;
        final SpannableStringBuilder sb = new SpannableStringBuilder(text);
        final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flags;
        sb.setSpan(new TextStyleSpan(run), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static CharSequence join(ArrayList<CharSequence> parts) {
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(parts.get(i));
        }
        return sb;
    }

    private static boolean isHeading(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockTitle
            || b instanceof TL_iv.pageBlockSubtitle
            || b instanceof TL_iv.pageBlockHeader
            || b instanceof TL_iv.pageBlockSubheader
            || b instanceof TL_iv.pageBlockHeading1
            || b instanceof TL_iv.pageBlockHeading2
            || b instanceof TL_iv.pageBlockHeading3
            || b instanceof TL_iv.pageBlockHeading4
            || b instanceof TL_iv.pageBlockHeading5
            || b instanceof TL_iv.pageBlockHeading6;
    }
}
