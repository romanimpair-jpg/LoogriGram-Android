package org.telegram.messenger;

public class SendMessageChatArguments {
    public static final SendMessageChatArguments EMPTY = new SendMessageChatArguments.Builder().build();

    public final long welcomeMessageChatId;

    // LoogriGram: this also carried the quick reply a message was being
    // added to (quickReplyShortcut and its id). Business quick replies are gone.
    private SendMessageChatArguments(Builder builder) {
        this.welcomeMessageChatId = builder.welcomeMessageChatId;
    }

    public static class Builder {
        private long welcomeMessageChatId;

        public void setWelcomeMessageChatId(long welcomeMessageChatId) {
            this.welcomeMessageChatId = welcomeMessageChatId;
        }

        public SendMessageChatArguments build() {
            return new SendMessageChatArguments(this);
        }
    }
}
