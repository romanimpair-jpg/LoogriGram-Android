package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class ChatMessagesMetadataController {

    final ChatActivity chatActivity;
    private final ArrayList<MessageObject> reactionsToCheck = new ArrayList<>(10);

    ArrayList<Integer> reactionsRequests = new ArrayList<>();


    public ChatMessagesMetadataController(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
    }

    public void checkMessages(ChatActivity.ChatActivityAdapter chatAdapter, int maxAdapterPosition, int minAdapterPosition, long currentTime) {
        ArrayList<MessageObject> messages = chatAdapter.getMessages();
        if (!chatActivity.isInScheduleMode() && maxAdapterPosition >= 0 && minAdapterPosition >= 0) {
            int from = minAdapterPosition - chatAdapter.messagesStartRow - 10;
            int to = maxAdapterPosition - chatAdapter.messagesStartRow + 10;
            if (from < 0) {
                from = 0;
            }
            if (to > messages.size()) {
                to = messages.size();
            }
            reactionsToCheck.clear();
            for (int i = from; i < to; i++) {
                MessageObject messageObject = messages.get(i);
                if (chatActivity.getThreadMessage() != messageObject && messageObject.getId() > 0 && (messageObject.messageOwner.action == null || messageObject.canSetReaction()) && (currentTime - messageObject.reactionsLastCheckTime) > 15000L) {
                    messageObject.reactionsLastCheckTime = currentTime;
                    reactionsToCheck.add(messageObject);
                }
                // LoogriGram: a message carrying a story, and the story a reply
                // quoted, were re-fetched here every five minutes while on screen
                // to notice the story expiring. The first is held unshown
                // (LoogriGramHidden) and stories are removed, so neither is drawn.
            }
            loadReactionsForMessages(chatActivity.getDialogId(), reactionsToCheck);
        }
    }

    public void loadReactionsForMessages(long dialogId, ArrayList<MessageObject> visibleObjects) {
        if (visibleObjects.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_getMessagesReactions req = new TLRPC.TL_messages_getMessagesReactions();
        req.peer = chatActivity.getMessagesController().getInputPeer(dialogId);
        for (int i = 0; i < visibleObjects.size(); i++) {
            MessageObject messageObject = visibleObjects.get(i);
            req.id.add(messageObject.getId());
        }
        int reqId = chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.Updates updates = (TLRPC.Updates) response;
                for (int i = 0; i < updates.updates.size(); i++) {
                    if (updates.updates.get(i) instanceof TL_update.TL_updateMessageReactions) {
                        ((TL_update.TL_updateMessageReactions) updates.updates.get(i)).updateUnreadState = false;
                    }
                }
                chatActivity.getMessagesController().processUpdates(updates, false);
            }
        });
        reactionsRequests.add(reqId);
        if (reactionsRequests.size() > 5) {
            chatActivity.getConnectionsManager().cancelRequest(reactionsRequests.remove(0), true);
        }
    }

    public void onFragmentDestroy() {
        for (int i = 0; i < reactionsRequests.size(); i++) {
            chatActivity.getConnectionsManager().cancelRequest(reactionsRequests.get(i), false);
        }
        reactionsRequests.clear();
    }
}
