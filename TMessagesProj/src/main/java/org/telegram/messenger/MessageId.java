package org.telegram.messenger;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * LoogriGram: a (dialog, message) pair. Upstream kept it inside StarsController
 * for its paid reactions; MessagesController keys its delivery reports on it.
 */
public class MessageId {
    public long did;
    public int mid;
    private MessageId(long did, int mid) {
        this.did = did;
        this.mid = mid;
    }
    public static MessageId from(long did, int mid) {
        return new MessageId(did, mid);
    }
    public static MessageId from(MessageObject msg) {
        if (msg == null) return null;
        if (msg.messageOwner != null && (msg.messageOwner.isThreadMessage || msg.isForwardedChannelPost()) && msg.messageOwner.fwd_from != null) {
            return new MessageId(msg.getFromChatId(), msg.messageOwner.fwd_from.saved_from_msg_id);
        } else {
            return new MessageId(msg.getDialogId(), msg.getId());
        }
    }
    @Override
    public int hashCode() {
        return Objects.hash(did, mid);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof MessageId) {
            MessageId id = (MessageId) obj;
            return id.did == did && id.mid == mid;
        }
        return false;
    }
}
