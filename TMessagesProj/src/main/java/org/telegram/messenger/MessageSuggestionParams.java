package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * LoogriGram: a suggested post carries a publishing time and nothing else.
 *
 * Upstream lets a post be suggested for a price in Stars or TON - the channel
 * is paid to publish it, and refunded if it is taken down early. That is a
 * money feature in both directions, so the price is gone: the amount field,
 * the currency it was in, and the minimum and maximum the server advertises.
 * A free suggestion is upstream's own case ("Offer for free"), and toTl never
 * set the price when it was zero, so what is left is the shape the server
 * already expects.
 */
public class MessageSuggestionParams {
    public final long time;

    private MessageSuggestionParams(long time) {
        this.time = time;
    }

    public TLRPC.SuggestedPost toTl() {
        TLRPC.SuggestedPost suggestedPost = new TLRPC.SuggestedPost();

        if (time > 0) {
            suggestedPost.schedule_date = (int) time;
            suggestedPost.flags |= TLObject.FLAG_0;
        }

        return suggestedPost;
    }

    public boolean isEmpty() {
        return time <= 0;
    }

    public static MessageSuggestionParams empty() {
        return new MessageSuggestionParams(0);
    }

    public static MessageSuggestionParams of(TLRPC.SuggestedPost suggestedPost) {
        if (suggestedPost == null) {
            return empty();
        }

        return new MessageSuggestionParams(suggestedPost.schedule_date);
    }

    public static MessageSuggestionParams of(TLRPC.TL_messageActionSuggestedPostApproval approval) {
        return ofTime(approval.schedule_date);
    }

    public static MessageSuggestionParams ofTime(long time) {
        return new MessageSuggestionParams(time);
    }
}
