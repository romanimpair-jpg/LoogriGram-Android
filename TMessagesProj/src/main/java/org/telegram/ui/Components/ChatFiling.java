package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

// LoogriGram: "Add to folder" and "Archive" for one chat, in the menu of the
// chat itself (ChatActivity) and of a forum's topic list (TopicsFragment), by
// the chat list's rules (DialogsActivity.updateCounters and its folder
// choice). Desktop's chat and topic-list menus have the same pair.
public class ChatFiling {

    // Neither applies to a chat that is not in the list or is inside a
    // community.
    private static TLRPC.Dialog listedDialog(int account, long dialogId) {
        final TLRPC.Dialog dialog = MessagesController.getInstance(account).dialogs_dict.get(dialogId);
        return dialog != null && dialog.community_id == 0 ? dialog : null;
    }

    public static boolean isArchived(int account, long dialogId) {
        final TLRPC.Dialog dialog = listedDialog(account, dialogId);
        return dialog != null && dialog.folder_id == 1;
    }

    // An archived chat can always come out; Saved Messages, service
    // notifications and the proxy's channel never go in.
    public static boolean canArchive(int account, long dialogId) {
        final TLRPC.Dialog dialog = listedDialog(account, dialogId);
        if (dialog == null) {
            return false;
        } else if (dialog.folder_id == 1) {
            return true;
        }
        return dialogId != UserConfig.getInstance(account).getClientUserId() && dialogId != 777000 && !MessagesController.getInstance(account).isPromoDialog(dialogId, false);
    }

    // A folder must exist to add to.
    public static boolean canAddToFolder(int account, long dialogId) {
        final MessagesController controller = MessagesController.getInstance(account);
        if (listedDialog(account, dialogId) == null || !controller.filtersEnabled) {
            return false;
        }
        for (MessagesController.DialogFilter filter : controller.dialogFilters) {
            if (!filter.isDefault()) {
                return true;
            }
        }
        return false;
    }

    // The chat list's folder sheet for this chat: a checked folder takes it
    // out, an unchecked one puts it in, the last row makes a new folder.
    public static void openFolderChooser(BaseFragment fragment, long dialogId) {
        final ArrayList<Long> dialogs = new ArrayList<>();
        dialogs.add(dialogId);
        final FiltersListBottomSheet sheet = new FiltersListBottomSheet(fragment, dialogs);
        sheet.setDelegate((filter, checked) -> FiltersListBottomSheet.applyChoice(fragment, filter, checked, dialogs, (did, added, count, folder) -> showFolderBulletin(fragment, dialogId, added, folder)));
        fragment.showDialog(sheet);
    }

    // The chat list's undo bar says the same: "X added to Folder".
    private static void showFolderBulletin(BaseFragment fragment, long dialogId, boolean added, MessagesController.DialogFilter folder) {
        final MessagesController controller = fragment.getMessagesController();
        long peerId = dialogId;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            final TLRPC.EncryptedChat encryptedChat = controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            if (encryptedChat == null) {
                return;
            }
            peerId = encryptedChat.user_id;
        }
        final String name;
        final int text;
        if (DialogObject.isUserDialog(peerId)) {
            final TLRPC.User user = controller.getUser(peerId);
            name = UserObject.isUserSelf(user) ? LocaleController.getString(R.string.SavedMessages) : UserObject.getFirstName(user);
            text = added ? R.string.FilterUserAddedToExisting : R.string.FilterUserRemovedFrom;
        } else {
            final TLRPC.Chat chat = controller.getChat(-peerId);
            if (chat == null) {
                return;
            }
            name = chat.title;
            text = added ? R.string.FilterChatAddedToExisting : R.string.FilterChatRemovedFrom;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(added ? R.raw.folder_in : R.raw.folder_out, AndroidUtilities.replaceTags(LocaleController.formatString(text, name, folder.name))).show();
    }

    // Archives the chat or brings it back, as a swipe in the chat list does.
    // Archiving can be undone from its bulletin, which restores a pin.
    public static void toggleArchived(BaseFragment fragment, long dialogId) {
        final MessagesController controller = fragment.getMessagesController();
        final TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
        if (dialog == null) {
            return;
        }
        if (dialog.folder_id == 1) {
            controller.addDialogToFolder(dialogId, 0, -1, 0);
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.ChatWasMovedToMainList)).show();
        } else {
            final int pinnedNum = dialog.pinnedNum;
            controller.addDialogToFolder(dialogId, 1, -1, 0);
            BulletinFactory.of(fragment).createUndoBulletin(LocaleController.getString(R.string.ChatArchived), () -> controller.addDialogToFolder(dialogId, 0, pinnedNum, 0), null).show();
        }
    }
}
