package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * LoogriGram: messages this build does not show.
 *
 * There are no money features here in either direction, so a message that is
 * a gift, an invoice, a payment, a giveaway, paid media or a Stars transfer
 * has nothing to display. The desktop fork settled the shape of this and the
 * reasoning is worth repeating, because the obvious implementation is wrong:
 *
 * **The message is still created and still lives in history - it is only
 * empty.** The server tracks what has been read by message id, and the read
 * position only ever moves past messages we hold. Drop one on the floor and
 * nothing marks it read, so the chat keeps an unread badge that scrolling
 * cannot clear - the same coupling that made suppressing read receipts get
 * reverted on both platforms.
 *
 * So the message is parsed, stored and counted as normal, and only its
 * content is refused: setType gives it contentType -1 and type -1, which is
 * upstream's own state for a message that exists but is never drawn (it uses
 * it for TL_messageActionHistoryClear), so the adapter, the album grouping
 * and the cell machinery already handle it.
 *
 * Known and accepted, as on desktop: a paid post in a channel vanishes
 * without trace.
 */
public class LoogriGramHidden {

    private LoogriGramHidden() {
    }

    /** True for a message whose whole content is a money feature. */
    public static boolean isHidden(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        return isHiddenMedia(MessageObject.getMedia(message)) || isHiddenAction(message.action);
    }

    private static boolean isHiddenMedia(TLRPC.MessageMedia media) {
        return media instanceof TLRPC.TL_messageMediaInvoice
            || media instanceof TLRPC.TL_messageMediaPaidMedia
            || media instanceof TLRPC.TL_messageMediaGiveaway
            || media instanceof TLRPC.TL_messageMediaGiveawayResults;
    }

    private static boolean isHiddenAction(TLRPC.MessageAction action) {
        return action instanceof TLRPC.TL_messageActionPaymentSent
            || action instanceof TLRPC.TL_messageActionPaymentSentMe
            || action instanceof TLRPC.TL_messageActionPaymentRefunded
            || action instanceof TLRPC.TL_messageActionGiftPremium
            || action instanceof TLRPC.TL_messageActionGiftCode
            || action instanceof TLRPC.TL_messageActionGiftStars
            || action instanceof TLRPC.TL_messageActionGiftTon
            || action instanceof TLRPC.TL_messageActionStarGift
            || action instanceof TLRPC.TL_messageActionStarGiftUnique
            || action instanceof TLRPC.TL_messageActionStarGiftPurchaseOffer
            || action instanceof TLRPC.TL_messageActionStarGiftPurchaseOfferDeclined
            || action instanceof TLRPC.TL_messageActionPrizeStars
            || action instanceof TLRPC.TL_messageActionGiveawayLaunch
            || action instanceof TLRPC.TL_messageActionGiveawayResults
            || action instanceof TLRPC.TL_messageActionBoostApply
            || action instanceof TLRPC.TL_messageActionSuggestedPostApproval
            || action instanceof TLRPC.TL_messageActionSuggestedPostRefund
            || action instanceof TLRPC.TL_messageActionSuggestedPostSuccess
            // not money, but it is drawn with the gift card and has no other
            // presentation - the desktop fork hides it for the same reason
            || action instanceof TLRPC.TL_messageActionSuggestBirthday;
    }
}
