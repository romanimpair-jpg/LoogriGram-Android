package org.telegram.messenger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashSet;

public class MemberRequestsController extends BaseController {

    private static final MemberRequestsController[] instances = new MemberRequestsController[UserConfig.MAX_ACCOUNT_COUNT];

    public static MemberRequestsController getInstance(int accountNum) {
        MemberRequestsController local = instances[accountNum];
        if (local == null) {
            synchronized (MemberRequestsController.class) {
                local = instances[accountNum];
                if (local == null) {
                    local = new MemberRequestsController(accountNum);
                    instances[accountNum] = local;
                }
            }
        }
        return local;
    }

    private final LongSparseArray<TLRPC.TL_messages_chatInviteImporters> firstImportersCache = new LongSparseArray<>();

    public MemberRequestsController(int accountNum) {
        super(accountNum);
    }

    @Nullable
    public TLRPC.TL_messages_chatInviteImporters getCachedImporters(long chatId) {
        return firstImportersCache.get(chatId);
    }

    public int getImporters(final long chatId, final String query, TLRPC.TL_chatInviteImporter lastImporter, LongSparseArray<TLRPC.User> users, RequestDelegate onComplete) {
        boolean isEmptyQuery = TextUtils.isEmpty(query);
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
        req.requested = true;
        req.limit = 30;
        if (!isEmptyQuery) {
            req.q = query;
            req.flags |= 4;
        }
        if (lastImporter == null) {
            req.offset_user = new TLRPC.TL_inputUserEmpty();
        } else {
            req.offset_user = getMessagesController().getInputUser(users.get(lastImporter.user_id));
            req.offset_date = lastImporter.date;
        }
        return getConnectionsManager().sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_messages_chatInviteImporters importers = (TLRPC.TL_messages_chatInviteImporters) response;
                    if (lastImporter == null && isEmptyQuery)
                        firstImportersCache.put(chatId, importers);
                }
                onComplete.run(response, error);
            });
        });
    }

    public void onPendingRequestsUpdated(TL_update.TL_updatePendingJoinRequests update) {
        long peerId = MessageObject.getPeerId(update.peer);
        firstImportersCache.put(-peerId, null);
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-peerId);
        if (chatFull != null) {
            chatFull.requests_pending = update.requests_pending;
            chatFull.recent_requesters = update.recent_requesters;
            chatFull.flags |= 131072;
            getMessagesStorage().updateChatInfo(chatFull, false);
            getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
        }
        // LoogriGram: upstream stopped at a loaded ChatFull, so the update was
        // lost for any chat not opened this session.
        if (update.peer instanceof TLRPC.TL_peerChannel || update.peer instanceof TLRPC.TL_peerChat) {
            setPendingRequests(-peerId, update.requests_pending, update.recent_requesters, true);
        }
    }

    // LoogriGram: join requests waiting in each chat this account can approve
    // them in, for the chat list's badge (DialogCell) and a notification when
    // one arrives. Kept current by the live update; refreshPendingRequests
    // re-reads every such chat when the chat list loads and after each
    // catch-up (getDifference), at most every ten minutes, in case an update
    // was missed while disconnected. A refresh never notifies: only an update
    // is news.
    private static final long PENDING_REQUESTS_REFRESH_INTERVAL = 10 * 60 * 1000;
    private static final String JOIN_REQUESTS_CHANNEL = "loogrigram_join_requests";

    private final LongSparseArray<Integer> pendingRequests = new LongSparseArray<>();
    private long pendingRequestsRefreshTime;
    private boolean refreshingPendingRequests;
    private long openedDialogId;

    public boolean hasPendingRequests(long chatId) {
        return pendingRequests.get(chatId, 0) > 0;
    }

    public void onChatFullLoaded(long chatId, TLRPC.ChatFull chatFull) {
        setPendingRequests(chatId, (chatFull.flags & 131072) != 0 ? chatFull.requests_pending : 0, null, false);
    }

    // Called with the chat a ChatActivity shows, or 0. Its requests bar is on
    // screen, so it gets no notification, and opening it clears one.
    public void setOpenedDialogId(long dialogId) {
        openedDialogId = dialogId;
        if (DialogObject.isChatDialog(dialogId)) {
            cancelJoinRequestsNotification(-dialogId);
        }
    }

    public void refreshPendingRequests() {
        long now = SystemClock.elapsedRealtime();
        if (refreshingPendingRequests || pendingRequestsRefreshTime != 0 && now - pendingRequestsRefreshTime < PENDING_REQUESTS_REFRESH_INTERVAL) {
            return;
        }
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        HashSet<Long> chatIds = new HashSet<>();
        ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getAllDialogs();
        for (int a = 0; a < dialogs.size(); a++) {
            TLRPC.Dialog dialog = dialogs.get(a);
            if (dialog instanceof TLRPC.TL_dialogFolder || dialog instanceof TLRPC.TL_dialogCommunity || !DialogObject.isChatDialog(dialog.id)) {
                continue;
            }
            TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
            if (chat != null && !chat.monoforum && !ChatObject.isNotInChat(chat) && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE)) {
                chats.add(chat);
                chatIds.add(chat.id);
            }
        }
        if (chats.isEmpty() && dialogs.isEmpty()) {
            return;
        }
        pendingRequestsRefreshTime = now;
        for (int a = pendingRequests.size() - 1; a >= 0; a--) {
            long chatId = pendingRequests.keyAt(a);
            if (!chatIds.contains(chatId)) {
                setPendingRequests(chatId, 0, null, false);
            }
        }
        refreshingPendingRequests = true;
        refreshPendingRequests(chats, 0);
    }

    private void refreshPendingRequests(ArrayList<TLRPC.Chat> chats, int index) {
        if (index >= chats.size()) {
            refreshingPendingRequests = false;
            return;
        }
        long chatId = chats.get(index).id;
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.peer = getMessagesController().getInputPeer(-chatId);
        req.requested = true;
        req.limit = 1;
        req.offset_user = new TLRPC.TL_inputUserEmpty();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_chatInviteImporters) {
                TLRPC.TL_messages_chatInviteImporters res = (TLRPC.TL_messages_chatInviteImporters) response;
                getMessagesController().putUsers(res.users, false);
                setPendingRequests(chatId, res.count, null, false);
            }
            refreshPendingRequests(chats, index + 1);
        }));
    }

    private void setPendingRequests(long chatId, int count, ArrayList<Long> recentRequesters, boolean notify) {
        int oldCount = pendingRequests.get(chatId, 0);
        if (count == oldCount) {
            return;
        }
        if (count > 0) {
            pendingRequests.put(chatId, count);
        } else {
            pendingRequests.remove(chatId);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
        if (count <= 0) {
            cancelJoinRequestsNotification(chatId);
        } else if (notify && count > oldCount && !(openedDialogId == -chatId && ApplicationLoader.isScreenOn)) {
            showJoinRequestsNotification(chatId, count, recentRequesters);
        }
    }

    private void showJoinRequestsNotification(long chatId, int count, ArrayList<Long> recentRequesters) {
        TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat == null) {
            return;
        }
        TLRPC.User user = count == 1 && recentRequesters != null && !recentRequesters.isEmpty() ? getMessagesController().getUser(recentRequesters.get(0)) : null;
        String text = user != null
            ? LocaleController.formatString(R.string.LoogriGramUserRequestedToJoin, UserObject.getUserName(user))
            : LocaleController.formatPluralString("JoinUsersRequests", count);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager.getNotificationChannel(JOIN_REQUESTS_CHANNEL) == null) {
                manager.createNotificationChannel(new NotificationChannel(JOIN_REQUESTS_CHANNEL, LocaleController.getString(R.string.LoogriGramJoinRequestsChannel), NotificationManager.IMPORTANCE_HIGH));
            }
        }

        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.putExtra("chatId", chatId);
        intent.putExtra("currentAccount", currentAccount);
        PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, JOIN_REQUESTS_CHANNEL)
            .setSmallIcon(R.drawable.notification)
            .setColor(0xff11acfa)
            .setContentTitle(chat.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent);
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(joinRequestsNotificationTag(), (int) (chatId ^ (chatId >>> 32)), builder.build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void cancelJoinRequestsNotification(long chatId) {
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(joinRequestsNotificationTag(), (int) (chatId ^ (chatId >>> 32)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private String joinRequestsNotificationTag() {
        return JOIN_REQUESTS_CHANNEL + "_" + currentAccount;
    }
}
