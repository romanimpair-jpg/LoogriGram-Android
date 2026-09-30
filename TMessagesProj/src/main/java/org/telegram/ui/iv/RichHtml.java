package org.telegram.ui.iv;

import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// LoogriGram: this also parsed pasted html into the article editor's rows
// (BlockRow) and wrote out a selection of them. The editor is deleted, as on
// desktop. What is left writes the html a copied article puts on the
// clipboard beside its text, and the pieces RichMessageLayout's text
// selection copies; the article is flattened into Rows here, the way the
// editor flattened it, and written out whole.
public class RichHtml {

    private static class Row {
        final TL_iv.PageBlock block;
        final int level;
        final int num;
        boolean detailsEnd;
        final ArrayList<Long> quoteIds = new ArrayList<>();

        Row(TL_iv.PageBlock block) {
            this(block, 0, 0);
        }

        Row(TL_iv.PageBlock block, int level, int num) {
            this.block = block;
            this.level = level;
            this.num = num;
        }
    }

    public static String toHtml(TL_iv.RichMessage msg) {
        if (msg == null) return null;
        final HashMap<Long, TL_iv.RichText> authors = new HashMap<>();
        final ArrayList<Row> rows = new ArrayList<>();
        flattenBlocks(rows, msg.blocks, authors);
        if (rows.isEmpty()) return null;
        return serialize(rows, authors);
    }

    private static long lastQuoteId;

    private static void flattenBlocks(ArrayList<Row> out, ArrayList<TL_iv.PageBlock> blocks, Map<Long, TL_iv.RichText> authors) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            if (block instanceof TL_iv.pageBlockList || block instanceof TL_iv.pageBlockOrderedList) {
                expandListBlock(out, block, 1);
            } else if (block instanceof TL_iv.pageBlockDetails) {
                flattenDetails(out, (TL_iv.pageBlockDetails) block, authors);
            } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
                expandBlockquoteBlocks(out, (TL_iv.pageBlockBlockquoteBlocks) block, authors);
            } else {
                out.add(new Row(block));
            }
        }
    }

    private static void expandBlockquoteBlocks(ArrayList<Row> out, TL_iv.pageBlockBlockquoteBlocks src, Map<Long, TL_iv.RichText> authors) {
        final long qid = ++lastQuoteId;
        final int start = out.size();
        flattenBlocks(out, src.blocks, authors);
        for (int i = start; i < out.size(); i++) {
            out.get(i).quoteIds.add(0, qid);
        }
        if (authors != null && src.caption != null && !(src.caption instanceof TL_iv.textEmpty)) {
            authors.put(qid, src.caption);
        }
    }

    private static void flattenDetails(ArrayList<Row> out, TL_iv.pageBlockDetails details,
                                       Map<Long, TL_iv.RichText> authors) {
        if (details.title == null) details.title = new TL_iv.textEmpty();
        out.add(new Row(details));
        final int childStart = out.size();
        flattenBlocks(out, details.blocks, authors);
        if (out.size() == childStart) {
            out.add(new Row(new TL_iv.pageBlockParagraph()));
        }
        final Row end = new Row(new TL_iv.pageBlockParagraph());
        end.detailsEnd = true;
        out.add(end);
    }

    private static void expandListBlock(ArrayList<Row> out, TL_iv.PageBlock listBlock, int level) {
        final boolean ordered = listBlock instanceof TL_iv.pageBlockOrderedList;
        int counter = 1;
        if (ordered) {
            for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) listBlock).items) {
                if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                    addListItemRow(out, ((TL_iv.TL_pageListOrderedItemText) item).text, level, counter);
                } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                    expandItemBlocks(out, ((TL_iv.TL_pageListOrderedItemBlocks) item).blocks, level, true, counter);
                } else {
                    continue;
                }
                counter++;
            }
        } else {
            for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) listBlock).items) {
                if (item instanceof TL_iv.TL_pageListItemText) {
                    addListItemRow(out, ((TL_iv.TL_pageListItemText) item).text, level, 0);
                } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                    expandItemBlocks(out, ((TL_iv.TL_pageListItemBlocks) item).blocks, level, false, 0);
                }
            }
        }
    }

    private static void addListItemRow(ArrayList<Row> out, TL_iv.RichText text, int level, int num) {
        TL_iv.pageBlockParagraph para = new TL_iv.pageBlockParagraph();
        para.text = text != null ? text : new TL_iv.textEmpty();
        out.add(new Row(para, level, num));
    }

    private static void expandItemBlocks(ArrayList<Row> out, ArrayList<TL_iv.PageBlock> blocks,
                                         int level, boolean ordered, int num) {
        boolean started = false;
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                final TL_iv.PageBlock b = blocks.get(i);
                if (b instanceof TL_iv.pageBlockList || b instanceof TL_iv.pageBlockOrderedList) {
                    if (!started) {
                        addListItemRow(out, null, level, num);
                        started = true;
                    }
                    expandListBlock(out, b, level + 1);
                    continue;
                }
                if (!started) {
                    if (b instanceof TL_iv.pageBlockParagraph) {
                        addListItemRow(out, ((TL_iv.pageBlockParagraph) b).text, level, num);
                    } else {
                        out.add(new Row(b, level, num));
                    }
                    started = true;
                } else {
                    out.add(new Row(b, level, ordered ? 1 : 0));
                }
            }
        }
        if (!started) {
            addListItemRow(out, null, level, num);
        }
    }

    private static boolean isDetailsHeader(Row row) {
        return row != null && row.block instanceof TL_iv.pageBlockDetails;
    }

    private static boolean isGallery(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockCollage || b instanceof TL_iv.pageBlockSlideshow;
    }

    private static ArrayList<TL_iv.PageBlock> galleryItems(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockCollage) return ((TL_iv.pageBlockCollage) b).items;
        if (b instanceof TL_iv.pageBlockSlideshow) return ((TL_iv.pageBlockSlideshow) b).items;
        return null;
    }

    private static String serialize(List<Row> rows, Map<Long, TL_iv.RichText> authors) {
        StringBuilder out = new StringBuilder();
        ListState ls = new ListState();
        int[] i = { 0 };
        serializeRange(out, rows, i, rows.size() - 1, ls, false, 0, authors);
        ls.closeAll(out);
        return out.toString();
    }

    private static class ListState {
        // stack of open list levels: true = ordered
        final ArrayList<Boolean> stack = new ArrayList<>();

        void sync(StringBuilder out, int level, boolean ordered) {
            while (stack.size() > level) close(out);
            while (stack.size() < level) open(out, ordered);
            if (!stack.isEmpty()) {
                int top = stack.size() - 1;
                if (stack.get(top) != ordered) {
                    close(out);
                    open(out, ordered);
                }
            }
        }

        private void open(StringBuilder out, boolean ordered) {
            out.append(ordered ? "<ol>" : "<ul>");
            stack.add(ordered);
        }

        private void close(StringBuilder out) {
            boolean ordered = stack.remove(stack.size() - 1);
            out.append(ordered ? "</ol>" : "</ul>");
        }

        void closeAll(StringBuilder out) {
            while (!stack.isEmpty()) close(out);
        }
    }

    private static void serializeRange(StringBuilder out, List<Row> rows, int[] i, int end,
                                       ListState ls, boolean inDetails, int quoteDepth,
                                       Map<Long, TL_iv.RichText> authors) {
        while (i[0] <= end) {
            Row row = rows.get(i[0]);
            if (row.detailsEnd) {
                if (inDetails) {
                    return;
                }
                i[0]++;
                continue;
            }
            if (row.quoteIds.size() > quoteDepth) {
                ls.closeAll(out);
                serializeQuote(out, rows, i, end, inDetails, quoteDepth, authors);
                continue;
            }
            if (isDetailsHeader(row)) {
                ls.closeAll(out);
                serializeDetails(out, rows, i, end, quoteDepth, authors);
                continue;
            }
            if (row.level > 0 && isTextBlock(row.block)) {
                ls.sync(out, row.level, row.num > 0);
                out.append("<li>");
                appendInline(out, styledText(row));
                out.append("</li>");
                i[0]++;
                continue;
            }
            ls.closeAll(out);
            serializeLeaf(out, row);
            i[0]++;
        }
    }

    private static void serializeDetails(StringBuilder out, List<Row> rows, int[] i, int end,
                                         int quoteDepth,
                                         Map<Long, TL_iv.RichText> authors) {
        Row header = rows.get(i[0]);
        TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) header.block;
        out.append(details.open ? "<details open>" : "<details>");
        out.append("<summary>");
        appendInline(out, styledText(header));
        out.append("</summary>");
        i[0]++;
        ListState inner = new ListState();
        serializeRange(out, rows, i, end, inner, true, quoteDepth, authors);
        inner.closeAll(out);
        if (i[0] <= end && i[0] < rows.size() && rows.get(i[0]).detailsEnd) {
            i[0]++;
        }
        out.append("</details>");
    }

    private static void serializeQuote(StringBuilder out, List<Row> rows, int[] i, int end,
                                       boolean inDetails, int quoteDepth,
                                       Map<Long, TL_iv.RichText> authors) {
        long qid = rows.get(i[0]).quoteIds.get(quoteDepth);
        int runEnd = i[0];
        while (runEnd + 1 <= end) {
            Row re = rows.get(runEnd + 1);
            if (re.quoteIds.size() > quoteDepth && re.quoteIds.get(quoteDepth) == qid) runEnd++;
            else break;
        }
        out.append("<blockquote>");
        ListState inner = new ListState();
        serializeRange(out, rows, i, runEnd, inner, inDetails, quoteDepth + 1, authors);
        inner.closeAll(out);
        appendAuthorCite(out, authors == null ? null : authorText(authors.get(qid)));
        out.append("</blockquote>");
    }

    private static CharSequence authorText(TL_iv.RichText author) {
        if (author == null || author instanceof TL_iv.textEmpty) return null;
        CharSequence cs = RichTextStyle.toSpannable(author);
        return cs != null && cs.length() > 0 ? cs : null;
    }

    private static void appendAuthorCite(StringBuilder out, CharSequence author) {
        if (author == null || author.length() == 0) return;
        out.append("<cite>");
        appendInline(out, author);
        out.append("</cite>");
    }

    private static void serializeLeaf(StringBuilder out, Row row) {
        TL_iv.PageBlock b = row.block;
        if (b instanceof TL_iv.pageBlockDivider) {
            out.append("<hr>");
            return;
        }
        if (b instanceof TL_iv.pageBlockButtonRow) {
            serializeButtonRow(out, (TL_iv.pageBlockButtonRow) b);
            return;
        }
        if (b instanceof TL_iv.pageBlockTable) {
            serializeTable(out, (TL_iv.pageBlockTable) b);
            return;
        }
        if (b instanceof TL_iv.pageBlockPhoto) {
            serializeSingleMedia(out, "img", ((TL_iv.pageBlockPhoto) b).photo_id, b);
            return;
        }
        if (b instanceof TL_iv.pageBlockVideo) {
            serializeSingleMedia(out, "video", ((TL_iv.pageBlockVideo) b).video_id, b);
            return;
        }
        if (b instanceof TL_iv.pageBlockAudio) {
            serializeSingleMedia(out, "audio", ((TL_iv.pageBlockAudio) b).audio_id, b);
            return;
        }
        if (b instanceof TL_iv.pageBlockDocument) {
            serializeSingleMedia(out, "document", ((TL_iv.pageBlockDocument) b).document_id, b);
            return;
        }
        if (isGallery(b)) {
            serializeGallery(out, b);
            return;
        }
        if (b instanceof TL_iv.pageBlockMap) {
            serializeMap(out, (TL_iv.pageBlockMap) b);
            return;
        }
        String tag = blockTag(b);
        if (tag == null) {
            CharSequence cap = captionOf(b);
            if (cap != null && cap.length() > 0) {
                out.append("<p>");
                appendInline(out, cap);
                out.append("</p>");
            }
            return;
        }
        if (b instanceof TL_iv.pageBlockPreformatted) {
            String lang = ((TL_iv.pageBlockPreformatted) b).language;
            if (!TextUtils.isEmpty(lang)) {
                out.append("<pre language=\"").append(escapeAttr(lang)).append("\">");
            } else {
                out.append("<pre>");
            }
            appendInline(out, styledText(row));
            out.append("</pre>");
            return;
        }
        if (b instanceof TL_iv.pageBlockPullquote) {
            out.append("<blockquote class=\"pull\">");
            appendInline(out, styledText(row));
            appendAuthorCite(out, authorText(((TL_iv.pageBlockPullquote) b).caption));
            out.append("</blockquote>");
            return;
        }
        if (b instanceof TL_iv.pageBlockBlockquote) {
            if (((TL_iv.pageBlockBlockquote) b).collapsed) {
                out.append("<blockquote collapsed>");
            } else {
                out.append("<blockquote>");
            }
            appendInline(out, styledText(row));
            appendAuthorCite(out, authorText(((TL_iv.pageBlockBlockquote) b).caption));
            out.append("</blockquote>");
            return;
        }
        out.append('<').append(tag).append('>');
        appendInline(out, styledText(row));
        out.append("</").append(tag).append('>');
    }

    private static CharSequence styledText(Row row) {
        if (isDetailsHeader(row)) {
            return RichTextStyle.toSpannable(((TL_iv.pageBlockDetails) row.block).title);
        }
        return row.block == null ? "" : RichTextStyle.toSpannable(row.block.text, row.block);
    }

    private static boolean isTextBlock(TL_iv.PageBlock b) {
        return blockTag(b) != null;
    }

    private static String blockTag(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockHeading1) return "h1";
        if (b instanceof TL_iv.pageBlockHeading2) return "h2";
        if (b instanceof TL_iv.pageBlockHeading3) return "h3";
        if (b instanceof TL_iv.pageBlockHeading4) return "h4";
        if (b instanceof TL_iv.pageBlockHeading5) return "h5";
        if (b instanceof TL_iv.pageBlockHeading6) return "h6";
        if (b instanceof TL_iv.pageBlockBlockquote) return "blockquote";
        if (b instanceof TL_iv.pageBlockPullquote) return "blockquote";
        if (b instanceof TL_iv.pageBlockPreformatted) return "pre";
        if (b instanceof TL_iv.pageBlockFooter) return "footer";
        if (b instanceof TL_iv.pageBlockParagraph) return "p";
        return null;
    }

    private static CharSequence captionOf(TL_iv.PageBlock b) {
        if (b == null || b.caption == null || b.caption.text == null) return null;
        CharSequence cs = RichTextStyle.toSpannable(b.caption.text);
        return cs != null && cs.length() > 0 ? cs : null;
    }

    private static void serializeTable(StringBuilder out, TL_iv.pageBlockTable t) {
        out.append("<table");
        if (t.bordered) out.append(" border=\"1\"");
        if (t.striped || t.compact) {
            out.append(" class=\"");
            if (t.striped) out.append("striped");
            if (t.striped && t.compact) out.append(' ');
            if (t.compact) out.append("compact");
            out.append('\"');
        }
        out.append('>');
        CharSequence title = t.title != null ? RichTextStyle.toSpannable(t.title) : null;
        if (title != null && title.length() > 0) {
            out.append("<caption>");
            appendInline(out, title);
            out.append("</caption>");
        }
        if (t.rows != null) {
            for (TL_iv.pageTableRow r : t.rows) {
                out.append("<tr>");
                if (r != null && r.cells != null) {
                    for (TL_iv.pageTableCell c : r.cells) {
                        if (c == null) continue;
                        String tag = c.header ? "th" : "td";
                        out.append('<').append(tag);
                        int cs = c.colspan > 1 ? c.colspan : 0;
                        if (cs > 0) out.append(" colspan=\"").append(cs).append('"');
                        int rs = c.rowspan > 1 ? c.rowspan : 0;
                        if (rs > 0) out.append(" rowspan=\"").append(rs).append('"');
                        String al = c.align_right ? "right" : (c.align_center ? "center" : null);
                        if (al != null) out.append(" align=\"").append(al).append('"');
                        String va = c.valign_bottom ? "bottom" : (c.valign_middle ? "middle" : null);
                        if (va != null) out.append(" valign=\"").append(va).append('"');
                        out.append('>');
                        appendInline(out, c.text == null ? "" : RichTextStyle.toSpannable(c.text));
                        out.append("</").append(tag).append('>');
                    }
                }
                out.append("</tr>");
            }
        }
        out.append("</table>");
    }

    private static void serializeSingleMedia(StringBuilder out, String tag, long id, TL_iv.PageBlock b) {
        if (id == 0) return;
        CharSequence cap = captionOf(b);
        boolean fig = cap != null && cap.length() > 0;
        if (fig) out.append("<figure>");
        appendMediaTag(out, tag, id, b);
        if (fig) {
            out.append("<figcaption>");
            appendInline(out, cap);
            out.append("</figcaption></figure>");
        }
    }

    private static void appendMediaTag(StringBuilder out, String tag, long id, TL_iv.PageBlock b) {
        out.append('<').append(tag).append(" src=\"").append(id).append('"');
        if (b instanceof TL_iv.pageBlockPhoto && ((TL_iv.pageBlockPhoto) b).spoiler) out.append(" data-spoiler=\"1\"");
        if (b instanceof TL_iv.pageBlockVideo && ((TL_iv.pageBlockVideo) b).spoiler) out.append(" data-spoiler=\"1\"");
        out.append(" />");
    }

    private static void serializeGallery(StringBuilder out, TL_iv.PageBlock b) {
        String cls = b instanceof TL_iv.pageBlockSlideshow ? "slideshow" : "collage";
        out.append("<div class=\"").append(cls).append("\">");
        ArrayList<TL_iv.PageBlock> items = galleryItems(b);
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                TL_iv.PageBlock item = items.get(i);
                if (item instanceof TL_iv.pageBlockVideo) {
                    long id = ((TL_iv.pageBlockVideo) item).video_id;
                    if (id != 0) appendMediaTag(out, "video", id, item);
                } else if (item instanceof TL_iv.pageBlockPhoto) {
                    long id = ((TL_iv.pageBlockPhoto) item).photo_id;
                    if (id != 0) appendMediaTag(out, "img", id, item);
                }
            }
        }
        CharSequence cap = captionOf(b);
        if (cap != null && cap.length() > 0) {
            out.append("<figcaption>");
            appendInline(out, cap);
            out.append("</figcaption>");
        }
        out.append("</div>");
    }

    private static void serializeMap(StringBuilder out, TL_iv.pageBlockMap m) {
        CharSequence cap = captionOf(m);
        boolean fig = cap != null && cap.length() > 0;
        if (fig) out.append("<figure>");
        out.append("<location");
        if (m.geo != null) {
            out.append(" lat=\"").append(m.geo.lat).append('"');
            out.append(" long=\"").append(m.geo._long).append('"');
            if (m.geo.access_hash != 0) out.append(" access=\"").append(m.geo.access_hash).append('"');
        }
        if (m.zoom != 0) out.append(" zoom=\"").append(m.zoom).append('"');
        if (m.w != 0) out.append(" w=\"").append(m.w).append('"');
        if (m.h != 0) out.append(" h=\"").append(m.h).append('"');
        out.append(" />");
        if (fig) {
            out.append("<figcaption>");
            appendInline(out, cap);
            out.append("</figcaption></figure>");
        }
    }

    public static String inlineToHtml(CharSequence styled) {
        StringBuilder out = new StringBuilder();
        appendInline(out, styled);
        return out.toString();
    }

    public static String preToHtml(CharSequence styled, String language) {
        final String inner = inlineToHtml(styled);
        if (inner.isEmpty()) return "";
        final StringBuilder out = new StringBuilder();
        if (!TextUtils.isEmpty(language)) {
            out.append("<pre language=\"").append(escapeAttr(language)).append("\">");
        } else {
            out.append("<pre>");
        }
        out.append(inner).append("</pre>");
        return out.toString();
    }

    public static String tableToHtml(TL_iv.pageBlockTable t) {
        if (t == null) return "";
        StringBuilder out = new StringBuilder();
        serializeTable(out, t);
        return out.toString();
    }

    private static void appendInline(StringBuilder out, CharSequence cs) {
        if (cs == null || cs.length() == 0) return;
        if (!(cs instanceof Spanned)) {
            escape(out, cs, 0, cs.length());
            return;
        }
        Spanned sp = (Spanned) cs;
        int n = cs.length();
        for (int pos = 0; pos < n; ) {
            RichInlineButtonSpan inlineButton = null;
            int inlineButtonEnd = -1;
            for (RichInlineButtonSpan candidate : sp.getSpans(pos, Math.min(n, pos + 1), RichInlineButtonSpan.class)) {
                final int start = sp.getSpanStart(candidate);
                final int end = sp.getSpanEnd(candidate);
                if (start <= pos && end > pos) {
                    inlineButton = candidate;
                    inlineButtonEnd = end;
                    break;
                }
            }
            if (inlineButton != null) {
                appendInlineButton(out, inlineButton.getButton());
                pos = Math.min(n, inlineButtonEnd);
                continue;
            }
            int next = sp.nextSpanTransition(pos, n, CharacterStyle.class);
            int flags = 0;
            for (TextStyleSpan span : sp.getSpans(pos, next, TextStyleSpan.class)) {
                TextStyleSpan.TextStyleRun run = span.getTextStyleRun();
                if (run != null) flags |= run.flags;
            }
            String url = null;
            URLSpanReplacement[] urls = sp.getSpans(pos, next, URLSpanReplacement.class);
            if (urls.length > 0) url = urls[0].getURL();
            long emojiId = 0;
            AnimatedEmojiSpan[] emoji = sp.getSpans(pos, next, AnimatedEmojiSpan.class);
            if (emoji.length > 0) emojiId = emoji[0].getDocumentId();

            openInline(out, flags, url, emojiId);
            escape(out, cs, pos, next);
            closeInline(out, flags, url, emojiId);
            pos = next;
        }
    }

    private static void appendInlineButton(StringBuilder out, TL_iv.textButton button) {
        if (button == null || !RichInlineButtonSpan.isSupported(button.type)) return;
        appendButton(out, button.text, button.type, button.style);
    }

    private static void appendButton(StringBuilder out, TL_iv.RichText text,
                                     TL_keyboard.InlineButtonType type,
                                     TL_keyboard.RichButtonStyle buttonStyle) {
        if (!RichInlineButtonSpan.isSupported(type)) return;
        out.append("<button");
        if (type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
            out.append(" data-type=\"url\" data-url=\"")
                .append(escapeAttr(((TL_keyboard.TL_inlineButtonTypeUrl) type).url)).append("\"");
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeCopy) {
            out.append(" data-type=\"copy\" data-copy-text=\"")
                .append(escapeAttr(((TL_keyboard.TL_inlineButtonTypeCopy) type).copy_text)).append("\"");
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile) {
            out.append(" data-type=\"user-profile\" data-user-id=\"")
                .append(((TL_keyboard.TL_inlineButtonTypeUserProfile) type).user_id).append("\"");
        }
        if (buttonStyle != null) {
            final String style = buttonStyle.bg_primary ? "primary"
                : buttonStyle.bg_danger ? "danger"
                : buttonStyle.bg_success ? "success" : "default";
            out.append(" data-style=\"").append(style).append("\"");
        }
        out.append(">");
        appendInline(out, RichTextStyle.toSpannable(text));
        out.append("</button>");
    }

    private static void serializeButtonRow(StringBuilder out, TL_iv.pageBlockButtonRow row) {
        out.append("<div class=\"button-row\"");
        if (row.align_left) out.append(" data-align=\"left\"");
        else if (row.align_center) out.append(" data-align=\"center\"");
        else if (row.align_right) out.append(" data-align=\"right\"");
        else out.append(" data-align=\"fill\"");
        out.append(">");
        if (row.buttons != null) {
            for (TL_keyboard.PageButton button : row.buttons) {
                if (button != null) appendButton(out, button.text, button.type, button.style);
            }
        }
        out.append("</div>");
    }

    private static void openInline(StringBuilder out, int flags, String url, long emojiId) {
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) out.append("<spoiler>");
        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) != 0) out.append("<b>");
        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0) out.append("<i>");
        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0) out.append("<u>");
        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) != 0) out.append("<s>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) out.append("<code>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUBSCRIPT) != 0) out.append("<sub>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) != 0) out.append("<sup>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MARKED) != 0) out.append("<mark>");
        if (url != null) out.append("<a href=\"").append(escapeAttr(url)).append("\">");
        if (emojiId != 0) out.append("<animated-emoji data-document-id=\"").append(emojiId).append("\">");
    }

    private static void closeInline(StringBuilder out, int flags, String url, long emojiId) {
        if (emojiId != 0) out.append("</animated-emoji>");
        if (url != null) out.append("</a>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MARKED) != 0) out.append("</mark>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) != 0) out.append("</sup>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUBSCRIPT) != 0) out.append("</sub>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) out.append("</code>");
        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) != 0) out.append("</s>");
        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0) out.append("</u>");
        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0) out.append("</i>");
        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) != 0) out.append("</b>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) out.append("</spoiler>");
    }

    private static void escape(StringBuilder out, CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\n') out.append("<br>");
            else if (c == '<') out.append("&lt;");
            else if (c == '>') out.append("&gt;");
            else if (c == '&') out.append("&amp;");
            else out.append(c);
        }
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
