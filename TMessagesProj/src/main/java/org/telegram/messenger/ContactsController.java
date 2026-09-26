/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

// LoogriGram: the address book imports went with the phonebook code - the
// AccountManager account, the ContactsContract provider and the cursor,
// content-resolver and content-provider machinery used to read and write it.
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContactsController extends BaseController {

    // LoogriGram: systemAccount (the AccountManager account), ignoreChanges and
    // observerLock (the address book observer's guard), contactsSyncInProgress,
    // contactsBookLoaded and migratingContacts all belonged to the phonebook
    // import and went with it.
    private boolean loadingContacts;
    private final Object loadContactsSync = new Object();
    public boolean contactsLoaded;
    public boolean doneLoadingContacts;
    private String lastContactsVersions = "";
    private ArrayList<Long> delayedContactsUpdate = new ArrayList<>();
    private String inviteLink;
    private boolean updatingInviteLink;
    private HashMap<String, String> sectionsToReplace = new HashMap<>();

    private int loadingGlobalSettings;
    private int loadingDeleteInfo;
    private int deleteAccountTTL;
    private int[] loadingPrivacyInfo = new int[PRIVACY_RULES_TYPE_COUNT];
    private ArrayList<TLRPC.PrivacyRule> lastseenPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> groupPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> callPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> p2pPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> profilePhotoPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> bioPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> musicPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> forwardsPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> phonePrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> addedByPhonePrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> birthdayPrivacyRules;
    private TLRPC.GlobalPrivacySettings globalPrivacySettings;

    public final static int PRIVACY_RULES_TYPE_LASTSEEN = 0;
    public final static int PRIVACY_RULES_TYPE_INVITE = 1;
    public final static int PRIVACY_RULES_TYPE_CALLS = 2;
    public final static int PRIVACY_RULES_TYPE_P2P = 3;
    public final static int PRIVACY_RULES_TYPE_PHOTO = 4;
    public final static int PRIVACY_RULES_TYPE_FORWARDS = 5;
    public final static int PRIVACY_RULES_TYPE_PHONE = 6;
    public final static int PRIVACY_RULES_TYPE_ADDED_BY_PHONE = 7;
    // LoogriGram: 8 was voice messages, whose restriction is Premium's, and 10
    // messages - who may start a chat - where "Contacts and Premium users" is
    // Premium's to pick. Neither screen exists, and the voice rule is neither
    // loaded nor tracked; the server keeps whatever it already is.
    public final static int PRIVACY_RULES_TYPE_BIO = 9;
    public final static int PRIVACY_RULES_TYPE_BIRTHDAY = 11;
    // LoogriGram: 12 was gifts (who may send us one), deleted as desktop
    // deleted it: neither loaded nor tracked, the server keeps the rule.
    // LoogriGram: 13 was NoPaidMessages, the "Remove fee" exceptions to a
    // price we never set.
    public final static int PRIVACY_RULES_TYPE_MUSIC = 14;

    public final static int PRIVACY_RULES_TYPE_COUNT = 15;

    // LoogriGram: MyContentObserver watched ContactsContract.Contacts for any
    // change to the address book and re-uploaded it half a second later. With
    // no read of the phonebook left there is nothing to observe.

    private static Locale cachedCollatorLocale;
    private static Collator cachedCollator;
    public static Collator getLocaleCollator() {
        if (cachedCollator == null || cachedCollatorLocale != Locale.getDefault()) {
            try {
                cachedCollator = Collator.getInstance(cachedCollatorLocale = Locale.getDefault());
                cachedCollator.setStrength(Collator.SECONDARY);
            } catch (Exception e) {
                FileLog.e(e, true);
            }
        }
        if (cachedCollator == null) {
            try {
                cachedCollator = Collator.getInstance();
                cachedCollator.setStrength(Collator.SECONDARY);
            } catch (Exception e) {
                FileLog.e(e, true);
            }
        }
        if (cachedCollator == null) {
            cachedCollator = new Collator() {
                @Override
                public int compare(String source, String target) {
                    if (source == null || target == null) {
                        return 0;
                    }
                    return source.compareTo(target);
                }
                @Override
                public CollationKey getCollationKey(String source) {
                    return null;
                }
                @Override
                public int hashCode() {
                    return 0;
                }
            };
        }
        return cachedCollator;
    }

    // LoogriGram: the Contact class modelled one entry of the phone's address
    // book - its lookup key, every number on it, which of them had been
    // imported and which Telegram user they turned out to be. Nothing reads
    // the address book, so nothing can build one.

    // LoogriGram: the two ContactsContract projections named the address book
    // columns this app used to read - numbers, labels, display names and the
    // owning account - and the six maps below cached what it read. Nothing
    // fills them now, so they are gone rather than left empty.

    public ArrayList<TLRPC.TL_contact> contacts = new ArrayList<>();
    public ConcurrentHashMap<Long, TLRPC.TL_contact> contactsDict = new ConcurrentHashMap<>(20, 1.0f, 2);
    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersSectionsArray = new ArrayList<>();

    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersMutualSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersMutualSectionsArray = new ArrayList<>();

    public HashMap<String, TLRPC.TL_contact> contactsByPhone = new HashMap<>();
    public HashMap<String, TLRPC.TL_contact> contactsByShortPhone = new HashMap<>();

    private int completedRequestsCount;
    
    private static volatile ContactsController[] Instance = new ContactsController[UserConfig.MAX_ACCOUNT_COUNT];
    public static ContactsController getInstance(int num) {
        ContactsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ContactsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ContactsController(num);
                }
            }
        }
        return localInstance;
    }

    public ContactsController(int instance) {
        super(instance);
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        if (preferences.getBoolean("needGetStatuses", false)) {
            reloadContactsStatuses();
        }

        sectionsToReplace.put("À", "A");
        sectionsToReplace.put("Á", "A");
        sectionsToReplace.put("Ä", "A");
        sectionsToReplace.put("Ù", "U");
        sectionsToReplace.put("Ú", "U");
        sectionsToReplace.put("Ü", "U");
        sectionsToReplace.put("Ì", "I");
        sectionsToReplace.put("Í", "I");
        sectionsToReplace.put("Ï", "I");
        sectionsToReplace.put("È", "E");
        sectionsToReplace.put("É", "E");
        sectionsToReplace.put("Ê", "E");
        sectionsToReplace.put("Ë", "E");
        sectionsToReplace.put("Ò", "O");
        sectionsToReplace.put("Ó", "O");
        sectionsToReplace.put("Ö", "O");
        sectionsToReplace.put("Ç", "C");
        sectionsToReplace.put("Ñ", "N");
        sectionsToReplace.put("Ÿ", "Y");
        sectionsToReplace.put("Ý", "Y");
        sectionsToReplace.put("Ţ", "Y");

        // LoogriGram: the first instance used to register a content observer on
        // the address book here. See MyContentObserver above.
    }

    public void cleanup() {
        contacts.clear();
        contactsDict.clear();
        usersSectionsDict.clear();
        usersMutualSectionsDict.clear();
        sortedUsersSectionsArray.clear();
        sortedUsersMutualSectionsArray.clear();
        delayedContactsUpdate.clear();
        contactsByPhone.clear();
        contactsByShortPhone.clear();

        loadingContacts = false;
        doneLoadingContacts = false;
        contactsLoaded = false;
        lastContactsVersions = "";
        loadingGlobalSettings = 0;
        loadingDeleteInfo = 0;
        deleteAccountTTL = 0;
        Arrays.fill(loadingPrivacyInfo, 0);
        lastseenPrivacyRules = null;
        groupPrivacyRules = null;
        callPrivacyRules = null;
        p2pPrivacyRules = null;
        profilePhotoPrivacyRules = null;
        bioPrivacyRules = null;
        musicPrivacyRules = null;
        birthdayPrivacyRules = null;
        forwardsPrivacyRules = null;
        phonePrivacyRules = null;

        Utilities.globalQueue.postRunnable(() -> {
            completedRequestsCount = 0;
        });
    }

    public void checkInviteText() {
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        inviteLink = preferences.getString("invitelink", null);
        int time = preferences.getInt("invitelinktime", 0);
        if (!updatingInviteLink && (inviteLink == null || Math.abs(System.currentTimeMillis() / 1000 - time) >= 86400)) {
            updatingInviteLink = true;
            TLRPC.TL_help_getInviteText req = new TLRPC.TL_help_getInviteText();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    final TLRPC.TL_help_inviteText res = (TLRPC.TL_help_inviteText) response;
                    if (res.message.length() != 0) {
                        AndroidUtilities.runOnUIThread(() -> {
                            updatingInviteLink = false;
                            SharedPreferences preferences1 = MessagesController.getMainSettings(currentAccount);
                            SharedPreferences.Editor editor = preferences1.edit();
                            editor.putString("invitelink", inviteLink = res.message);
                            editor.putInt("invitelinktime", (int) (System.currentTimeMillis() / 1000));
                            editor.commit();
                        });
                    }
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    public String getInviteText(int contacts) {
        String link = inviteLink == null ? "https://telegram.org/dl" : inviteLink;
        if (contacts <= 1) {
            return LocaleController.formatString(R.string.InviteText2, link);
        } else {
            try {
                return String.format(LocaleController.getPluralString("InviteTextNum", contacts), contacts, link);
            } catch (Exception e) {
                return LocaleController.formatString(R.string.InviteText2, link);
            }
        }
    }

    // LoogriGram: the phone's address book is not touched at all, so
    // everything that read it, wrote to it, or kept an Android account for it
    // is gone from this class. What went, in the order it used to appear:
    //
    //   checkAppAccount / deleteUnknownAppAccounts - registered an account of
    //     type com.loogrimedia.loogrigram with AccountManager, which is what
    //     let the OS run a contacts sync adapter against us.
    //   checkContacts / forceImportContacts / syncPhoneBookByAlert /
    //     checkContactsInternal / readContacts / readContactsFromPhoneBook /
    //     migratePhoneBookToV7 / getContactsCopy / performSyncPhoneBook -
    //     read every number in the phonebook and uploaded them to Telegram to
    //     find out which of them had accounts. That upload is the single
    //     biggest thing this app told the server about people who never
    //     installed it, and it is the reason the feature is gone rather than
    //     merely switched off.
    //   mergePhonebookAndTelegramContacts / updateUnregisteredContacts -
    //     built the "invite these people" lists out of the two.
    //   hasContactsPermission / hasContactsWritePermission - the manifest no
    //     longer asks for either permission, so both were always false.
    //   performWriteContactsToPhoneBook / addContactToPhoneBook /
    //     applyContactToPhoneBook / deleteContactFromPhoneBook /
    //     markAsContacted - wrote Telegram contacts back into the address book
    //     under our account, which is what put "Call via Telegram" rows in the
    //     phone's contacts app.
    //   createOrUpdateConnectionServiceContact / deleteConnectionServiceContact
    //     - the same thing for the system call log, so incoming calls showed a
    //     name. VoIPService now passes the name it already has.
    //
    // Telegram-side contacts - the list the account holds on the server - are
    // untouched: loading them, adding one by phone number by hand, and deleting
    // them all still work.





    public void deleteAllContacts(final Runnable runnable) {
        resetImportedContacts();
        TLRPC.TL_contacts_deleteContacts req = new TLRPC.TL_contacts_deleteContacts();
        for (int a = 0, size = contacts.size(); a < size; a++) {
            TLRPC.TL_contact contact = contacts.get(a);
            req.id.add(getMessagesController().getInputUser(contact.user_id));
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                completedRequestsCount = 0;
                contactsLoaded = false;
                loadingContacts = false;
                lastContactsVersions = "";
                AndroidUtilities.runOnUIThread(() -> {
                    // LoogriGram: the account this used to tear down and
                    // recreate in AccountManager no longer exists, and there is
                    // no cached phonebook to clear - see the note above
                    // deleteAllContacts' neighbours.
                    getMessagesStorage().putContacts(new ArrayList<>(), true);
                    contacts.clear();
                    contactsDict.clear();
                    usersSectionsDict.clear();
                    usersMutualSectionsDict.clear();
                    sortedUsersSectionsArray.clear();
                    delayedContactsUpdate.clear();
                    sortedUsersMutualSectionsArray.clear();
                    contactsByPhone.clear();
                    contactsByShortPhone.clear();
                    getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                    loadContacts(false, 0);
                    runnable.run();
                });
            } else {
                AndroidUtilities.runOnUIThread(runnable);
            }
        });
    }

    public void resetImportedContacts() {
        TLRPC.TL_contacts_resetSaved req = new TLRPC.TL_contacts_resetSaved();
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }








    public boolean isLoadingContacts() {
        synchronized (loadContactsSync) {
            return loadingContacts;
        }
    }

    private long getContactsHash(ArrayList<TLRPC.TL_contact> contacts) {
        long acc = 0;
        contacts = new ArrayList<>(contacts);
        Collections.sort(contacts, (tl_contact, tl_contact2) -> {
            if (tl_contact.user_id > tl_contact2.user_id) {
                return 1;
            } else if (tl_contact.user_id < tl_contact2.user_id) {
                return -1;
            }
            return 0;
        });
        int count = contacts.size();
        for (int a = -1; a < count; a++) {
            if (a == -1) {
                acc = MediaDataController.calcHash(acc, getUserConfig().contactsSavedCount);
            } else {
                TLRPC.TL_contact set = contacts.get(a);
                acc = MediaDataController.calcHash(acc, set.user_id);
            }
        }
        return acc;
    }

    public void loadContacts(boolean fromCache, final long hash) {
        synchronized (loadContactsSync) {
            loadingContacts = true;
        }
        if (fromCache) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("load contacts from cache");
            }
            getMessagesStorage().getContacts();
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("load contacts from server");
            }

            TLRPC.TL_contacts_getContacts req = new TLRPC.TL_contacts_getContacts();
            req.hash = hash;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.contacts_Contacts res = (TLRPC.contacts_Contacts) response;
                    if (hash != 0 && res instanceof TLRPC.TL_contacts_contactsNotModified) {
                        contactsLoaded = true;
                        // LoogriGram: this also waited for the phonebook import
                        // to have finished. There is no import, so the only
                        // condition left is that the contacts themselves loaded.
                        if (!delayedContactsUpdate.isEmpty()) {
                            applyContactsUpdates(delayedContactsUpdate, null, null, null);
                            delayedContactsUpdate.clear();
                        }
                        getUserConfig().lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000);
                        getUserConfig().saveConfig(false);
                        AndroidUtilities.runOnUIThread(() -> {
                            synchronized (loadContactsSync) {
                                loadingContacts = false;
                            }
                            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                        });
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("load contacts don't change");
                        }
                        return;
                    } else {
                        getUserConfig().contactsSavedCount = res.saved_count;
                        getUserConfig().saveConfig(false);
                    }
                    processLoadedContacts(res.contacts, res.users, 0);
                }
            });
        }
    }

    public void processLoadedContacts(final ArrayList<TLRPC.TL_contact> contactsArr, final ArrayList<TLRPC.User> usersArr, final int from) {
        //from: 0 - from server, 1 - from db, 2 - from imported contacts
        AndroidUtilities.runOnUIThread(() -> {
            getMessagesController().putUsers(usersArr, from == 1);

            final LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();

            final boolean isEmpty = contactsArr.isEmpty();

            if (from == 2 && !contacts.isEmpty()) {
                for (int a = 0; a < contactsArr.size(); a++) {
                    TLRPC.TL_contact contact = contactsArr.get(a);
                    if (contactsDict.get(contact.user_id) != null) {
                        contactsArr.remove(a);
                        a--;
                    }
                }
                contactsArr.addAll(contacts);
            }

            for (int a = 0; a < contactsArr.size(); a++) {
                TLRPC.User user = getMessagesController().getUser(contactsArr.get(a).user_id);
                if (user != null) {
                    usersDict.put(user.id, user);
                    //if (BuildVars.DEBUG_VERSION) {
                    //    FileLog.e("loaded user contact " + user.first_name + " " + user.last_name + " " + user.phone);
                    //}
                }
            }

            Utilities.stageQueue.postRunnable(() -> {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("done loading contacts");
                }
                if (from == 1 && (contactsArr.isEmpty() || Math.abs(System.currentTimeMillis() / 1000 - getUserConfig().lastContactsSyncTime) >= 24 * 60 * 60)) {
                    loadContacts(false, getContactsHash(contactsArr));
                    if (contactsArr.isEmpty()) {
                        AndroidUtilities.runOnUIThread(() -> {
                            doneLoadingContacts = true;
                            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                        });
                        return;
                    }
                }
                if (from == 0) {
                    getUserConfig().lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000);
                    getUserConfig().saveConfig(false);
                }

                boolean reloadContacts = false;
                for (int a = 0; a < contactsArr.size(); a++) {
                    TLRPC.TL_contact contact = contactsArr.get(a);
                    if (MessagesController.getInstance(currentAccount).getUser(contact.user_id) == null && contact.user_id != getUserConfig().getClientUserId()) {
                        contactsArr.remove(a);
                        a--;
                        reloadContacts = true;
                    }
                }
//                loadContacts(false, 0);
//                if (BuildVars.LOGS_ENABLED) {
//                    FileLog.d("contacts are broken, load from server");
//                }
//                AndroidUtilities.runOnUIThread(() -> {
//                    doneLoadingContacts = true;
//                    getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
//                });

                if (from != 1) {
                    getMessagesStorage().putUsersAndChats(usersArr, null, true, true);
                    getMessagesStorage().putContacts(contactsArr, from != 2);
                }

                final Collator collator = getLocaleCollator();
                Collections.sort(contactsArr, (tl_contact, tl_contact2) -> {
                    TLRPC.User user1 = usersDict.get(tl_contact.user_id);
                    TLRPC.User user2 = usersDict.get(tl_contact2.user_id);
                    String name1 = UserObject.getFirstName(user1);
                    String name2 = UserObject.getFirstName(user2);
                    return collator.compare(name1, name2);
                });

                final ConcurrentHashMap<Long, TLRPC.TL_contact> contactsDictionary = new ConcurrentHashMap<>(20, 1.0f, 2);
                final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<>();
                final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDictMutual = new HashMap<>();
                final ArrayList<String> sortedSectionsArray = new ArrayList<>();
                final ArrayList<String> sortedSectionsArrayMutual = new ArrayList<>();
                // LoogriGram: these two were built only until the phonebook
                // import had run, which then kept them up to date itself. They
                // map a contact's phone number to the contact and are still
                // read outside this class, so they are now built every time.
                final HashMap<String, TLRPC.TL_contact> contactsByPhonesDict = new HashMap<>();
                final HashMap<String, TLRPC.TL_contact> contactsByPhonesShortDict = new HashMap<>();

                final HashMap<String, TLRPC.TL_contact> contactsByPhonesDictFinal = contactsByPhonesDict;
                final HashMap<String, TLRPC.TL_contact> contactsByPhonesShortDictFinal = contactsByPhonesShortDict;

                for (int a = 0; a < contactsArr.size(); a++) {
                    TLRPC.TL_contact value = contactsArr.get(a);
                    TLRPC.User user = usersDict.get(value.user_id);
                    if (user == null) {
                        continue;
                    }
                    contactsDictionary.put(value.user_id, value);
                    if (!TextUtils.isEmpty(user.phone)) {
                        contactsByPhonesDict.put(user.phone, value);
                        contactsByPhonesShortDict.put(user.phone.substring(Math.max(0, user.phone.length() - 7)), value);
                    }

                    String key = UserObject.getFirstName(user);
                    if (key.length() > 1) {
                        key = key.substring(0, 1);
                    }
                    if (key.length() == 0) {
                        key = "#";
                    } else {
                        key = key.toUpperCase();
                    }
                    String replace = sectionsToReplace.get(key);
                    if (replace != null) {
                        key = replace;
                    }
                    ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        sectionsDict.put(key, arr);
                        sortedSectionsArray.add(key);
                    }
                    arr.add(value);
                    if (user.mutual_contact) {
                        arr = sectionsDictMutual.get(key);
                        if (arr == null) {
                            arr = new ArrayList<>();
                            sectionsDictMutual.put(key, arr);
                            sortedSectionsArrayMutual.add(key);
                        }
                        arr.add(value);
                    }
                }

                Collections.sort(sortedSectionsArray, (s, s2) -> {
                    char cv1 = s.charAt(0);
                    char cv2 = s2.charAt(0);
                    if (cv1 == '#') {
                        return 1;
                    } else if (cv2 == '#') {
                        return -1;
                    }
                    return collator.compare(s, s2);
                });

                Collections.sort(sortedSectionsArrayMutual, (s, s2) -> {
                    char cv1 = s.charAt(0);
                    char cv2 = s2.charAt(0);
                    if (cv1 == '#') {
                        return 1;
                    } else if (cv2 == '#') {
                        return -1;
                    }
                    return collator.compare(s, s2);
                });

                boolean finalReloadContacts = reloadContacts;
                AndroidUtilities.runOnUIThread(() -> {
                    contacts = contactsArr;
                    contactsDict = contactsDictionary;
                    usersSectionsDict = sectionsDict;
                    usersMutualSectionsDict = sectionsDictMutual;
                    sortedUsersSectionsArray = sortedSectionsArray;
                    sortedUsersMutualSectionsArray = sortedSectionsArrayMutual;
                    doneLoadingContacts = true;
                    if (from != 2) {
                        synchronized (loadContactsSync) {
                            loadingContacts = false;
                        }
                    }
                    // LoogriGram: wrote these contacts into the address book and
                    // rebuilt the "not on Telegram yet" list from it. Neither
                    // exists now.

                    getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);

                    if (from != 1 && !isEmpty) {
                        saveContactsLoadTime();
                    } else {
                        reloadContactsStatusesMaybe(false);
                    }
                    if (finalReloadContacts) {
                        loadContacts(false, 0);
                    }
                });

                if (!delayedContactsUpdate.isEmpty() && contactsLoaded) {
                    applyContactsUpdates(delayedContactsUpdate, null, null, null);
                    delayedContactsUpdate.clear();
                }

                // LoogriGram: upstream set contactsLoaded here only when the
                // phonebook had already been read, and otherwise went off to
                // read the cached phonebook and set it at the end of that.
                // There is no phonebook step any more, so loading the contacts
                // is the whole job and this is where it finishes - miss this
                // and contactsLoaded never becomes true.
                AndroidUtilities.runOnUIThread(() -> Utilities.globalQueue.postRunnable(() -> {
                    contactsByPhone = contactsByPhonesDictFinal;
                    contactsByShortPhone = contactsByPhonesShortDictFinal;
                }));
                contactsLoaded = true;
            });
        });
    }

    public boolean isContact(long userId) {
        return contactsDict.get(userId) != null;
    }

    public void reloadContactsStatusesMaybe(boolean force) {
        try {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            long lastReloadStatusTime = preferences.getLong("lastReloadStatusTime", 0);
            if (lastReloadStatusTime < System.currentTimeMillis() - 1000 * 60 * 60 * 3 || force) {
                reloadContactsStatuses();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveContactsLoadTime() {
        try {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            preferences.edit().putLong("lastReloadStatusTime", System.currentTimeMillis()).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }




    private void buildContactsSectionsArrays(boolean sort) {
        final Collator collator = getLocaleCollator();
        if (sort) {
            Collections.sort(contacts, (tl_contact, tl_contact2) -> {
                TLRPC.User user1 = getMessagesController().getUser(tl_contact.user_id);
                TLRPC.User user2 = getMessagesController().getUser(tl_contact2.user_id);
                String name1 = UserObject.getFirstName(user1);
                String name2 = UserObject.getFirstName(user2);
                return collator.compare(name1, name2);
            });
        }

        final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<>();
        final ArrayList<String> sortedSectionsArray = new ArrayList<>();

        for (int a = 0; a < contacts.size(); a++) {
            TLRPC.TL_contact value = contacts.get(a);
            TLRPC.User user = getMessagesController().getUser(value.user_id);
            if (user == null) {
                continue;
            }

            String key = UserObject.getFirstName(user);
            if (key.length() > 1) {
                key = key.substring(0, 1);
            }
            if (key.length() == 0) {
                key = "#";
            } else {
                key = key.toUpperCase();
            }
            String replace = sectionsToReplace.get(key);
            if (replace != null) {
                key = replace;
            }
            ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
            if (arr == null) {
                arr = new ArrayList<>();
                sectionsDict.put(key, arr);
                sortedSectionsArray.add(key);
            }
            arr.add(value);
        }

        Collections.sort(sortedSectionsArray, (s, s2) -> {
            char cv1 = s.charAt(0);
            char cv2 = s2.charAt(0);
            if (cv1 == '#') {
                return 1;
            } else if (cv2 == '#') {
                return -1;
            }
            return collator.compare(s, s2);
        });

        usersSectionsDict = sectionsDict;
        sortedUsersSectionsArray = sortedSectionsArray;
    }





    private void applyContactsUpdates(ArrayList<Long> ids, ConcurrentHashMap<Long, TLRPC.User> userDict, ArrayList<TLRPC.TL_contact> newC, ArrayList<Long> contactsTD) {
        if (newC == null || contactsTD == null) {
            newC = new ArrayList<>();
            contactsTD = new ArrayList<>();
            for (int a = 0; a < ids.size(); a++) {
                Long uid = ids.get(a);
                if (uid > 0) {
                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                    contact.user_id = uid;
                    newC.add(contact);
                } else if (uid < 0) {
                    contactsTD.add(-uid);
                }
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("process update - contacts add = " + newC.size() + " delete = " + contactsTD.size());
        }

        // LoogriGram: both loops also maintained the cached phonebook - marking
        // a number as deleted or restored in it, collecting the two comma
        // separated lists that applyPhoneBookUpdates took, and removing the
        // contact's row from the address book. All that is gone; what stays is
        // putting the users the update carried and noticing when one is missing,
        // which is what decides whether the contact list has to be reloaded.
        boolean reloadContacts = false;

        for (int a = 0; a < newC.size(); a++) {
            TLRPC.TL_contact newContact = newC.get(a);
            TLRPC.User user = null;
            if (userDict != null) {
                user = userDict.get(newContact.user_id);
            }
            if (user == null) {
                user = getMessagesController().getUser(newContact.user_id);
            } else {
                getMessagesController().putUser(user, true);
            }
            if (user == null || TextUtils.isEmpty(user.phone)) {
                reloadContacts = true;
            }
        }

        for (int a = 0; a < contactsTD.size(); a++) {
            final Long uid = contactsTD.get(a);

            TLRPC.User user = null;
            if (userDict != null) {
                user = userDict.get(uid);
            }
            if (user == null) {
                user = getMessagesController().getUser(uid);
            } else {
                getMessagesController().putUser(user, true);
            }
            if (user == null) {
                reloadContacts = true;
            }
        }

        if (reloadContacts) {
            Utilities.stageQueue.postRunnable(() -> loadContacts(false, 0));
        } else {
            final ArrayList<TLRPC.TL_contact> newContacts = newC;
            final ArrayList<Long> contactsToDelete = contactsTD;
            AndroidUtilities.runOnUIThread(() -> {
                for (int a = 0; a < newContacts.size(); a++) {
                    TLRPC.TL_contact contact = newContacts.get(a);
                    if (contactsDict.get(contact.user_id) == null) {
                        contacts.add(contact);
                        contactsDict.put(contact.user_id, contact);
                    }
                }
                for (int a = 0; a < contactsToDelete.size(); a++) {
                    Long uid = contactsToDelete.get(a);
                    TLRPC.TL_contact contact = contactsDict.get(uid);
                    if (contact != null) {
                        contacts.remove(contact);
                        contactsDict.remove(uid);
                    }
                }
                // LoogriGram: the three phonebook calls that stood here - the
                // "not on Telegram" list, the write-back into the address book
                // and a re-import - are gone. Rebuilding the section arrays is
                // what actually updates the contact list on screen.
                buildContactsSectionsArrays(!newContacts.isEmpty());
                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
            });
        }
    }

    public void processContactsUpdates(ArrayList<Long> ids, ConcurrentHashMap<Long, TLRPC.User> userDict) {
        final ArrayList<TLRPC.TL_contact> newContacts = new ArrayList<>();
        final ArrayList<Long> contactsToDelete = new ArrayList<>();
        for (Long uid : ids) {
            if (uid > 0) {
                TLRPC.TL_contact contact = new TLRPC.TL_contact();
                contact.user_id = uid;
                newContacts.add(contact);
                if (!delayedContactsUpdate.isEmpty()) {
                    int idx = delayedContactsUpdate.indexOf(-uid);
                    if (idx != -1) {
                        delayedContactsUpdate.remove(idx);
                    }
                }
            } else if (uid < 0) {
                contactsToDelete.add(-uid);
                if (!delayedContactsUpdate.isEmpty()) {
                    int idx = delayedContactsUpdate.indexOf(-uid);
                    if (idx != -1) {
                        delayedContactsUpdate.remove(idx);
                    }
                }
            }
        }
        if (!contactsToDelete.isEmpty()) {
            getMessagesStorage().deleteContacts(contactsToDelete);
        }
        if (!newContacts.isEmpty()) {
            getMessagesStorage().putContacts(newContacts, false);
        }
        // LoogriGram: an update that arrives before the contacts are loaded is
        // still held back; waiting for the phonebook import too is no longer
        // possible, and no longer means anything.
        if (!contactsLoaded) {
            delayedContactsUpdate.addAll(ids);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("delay update - contacts add = " + newContacts.size() + " delete = " + contactsToDelete.size());
            }
        } else {
            applyContactsUpdates(ids, userDict, newContacts, contactsToDelete);
        }
    }





    public void addContact(TLRPC.User user, boolean exception) {
        addContact(user, null, exception);
    }

    public void addContact(TLRPC.User user, TLRPC.TL_textWithEntities note, boolean exception) {
        if (user == null) {
            return;
        }

        final TLRPC.TL_contacts_addContact req = new TLRPC.TL_contacts_addContact();
        req.id = getMessagesController().getInputUser(user);
        req.first_name = user.first_name;
        req.last_name = user.last_name;
        req.phone = user.phone;
        req.add_phone_privacy_exception = exception;
        if (req.phone == null) {
            req.phone = "";
        } else if (req.phone.length() > 0 && !req.phone.startsWith("+")) {
            req.phone = "+" + req.phone;
        }
        if (note != null) {
            req.flags |= 2;
            req.note = note;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            final TLRPC.Updates res = (TLRPC.Updates) response;
            if (user.photo != null && user.photo.personal) {
                for (int i = 0; i < res.users.size(); i++) {
                    if (res.users.get(i).id == user.id) {
                        res.users.get(i).photo = user.photo;
                    }
                }
            }
            getMessagesController().processUpdates(res, false);

            for (int a = 0; a < res.users.size(); a++) {
                final TLRPC.User u = res.users.get(a);
                if (u.id != user.id) {
                    continue;
                }
                // LoogriGram: adding someone as a contact no longer writes a row
                // for them into the phone's address book, and there is no
                // cached phonebook to mark their number live in either.
                final TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                newContact.user_id = u.id;
                final ArrayList<TLRPC.TL_contact> arrayList = new ArrayList<>();
                arrayList.add(newContact);
                getMessagesStorage().putContacts(arrayList, false);
            }

            AndroidUtilities.runOnUIThread(() -> {
                // LoogriGram: the block that stood here moved the matching
                // phonebook entry between letter sections, because a phonebook
                // row that had just become a Telegram contact was filed under
                // the name the address book knew rather than the account's.
                // With no phonebook rows in the list there is nothing to move,
                // and no resort to trigger.
                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User u = res.users.get(a);
                    if (!u.contact || contactsDict.get(u.id) != null) {
                        continue;
                    }
                    TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                    newContact.user_id = u.id;
                    contacts.add(newContact);
                    contactsDict.put(newContact.user_id, newContact);
                }
                buildContactsSectionsArrays(true);
                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagCanCompress);
    }

    public void deleteContactsUndoable(Context context, BaseFragment fragment, final ArrayList<TLRPC.User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }

        HashMap<TLRPC.User, TLRPC.TL_contact> deletedContacts = new HashMap<>();

        for (int i = 0, N = users.size(); i < N; i++) {
            TLRPC.User user = users.get(i);
            TLRPC.TL_contact contact = contactsDict.get(user.id);

            user.contact = false;
            contacts.remove(contact);
            contactsDict.remove(user.id);

            deletedContacts.put(user, contact);
        }
        buildContactsSectionsArrays(false);
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);

        Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(context, fragment.getResourceProvider());
        layout.setTimer();
        layout.textView.setText(LocaleController.formatPluralString("ContactsDeletedUndo", deletedContacts.size()));
        Bulletin.UndoButton undoButton = new Bulletin.UndoButton(context, true, true, fragment.getResourceProvider());
        undoButton.setUndoAction(() -> {
            for (HashMap.Entry<TLRPC.User, TLRPC.TL_contact> entry : deletedContacts.entrySet()) {
                TLRPC.User user = entry.getKey();
                TLRPC.TL_contact contact = entry.getValue();

                user.contact = true;
                contacts.add(contact);
                contactsDict.put(user.id, contact);
            }
            buildContactsSectionsArrays(true);
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
        });
        undoButton.setDelayedAction(() -> {
            deleteContact(users, false);
        });
        layout.setButton(undoButton);
        Bulletin bulletin = Bulletin.make(fragment, layout, Bulletin.DURATION_PROLONG);
        bulletin.show();
    }

    public void deleteContact(final ArrayList<TLRPC.User> users, boolean showBulletin) {
        if (users == null || users.isEmpty()) {
            return;
        }
        TLRPC.TL_contacts_deleteContacts req = new TLRPC.TL_contacts_deleteContacts();
        final ArrayList<Long> uids = new ArrayList<>();
        for (int a = 0, N = users.size(); a < N; a++) {
            TLRPC.User user = users.get(a);
            getMessagesController().getStoriesController().removeContact(user.id);
            TLRPC.InputUser inputUser = getMessagesController().getInputUser(user);
            if (inputUser == null) {
                continue;
            }
            user.contact = false;
            uids.add(user.id);
            req.id.add(inputUser);
        }
        String userName = users.get(0).first_name;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            getMessagesController().processUpdates((TLRPC.Updates) response, false);
            getMessagesStorage().deleteContacts(uids);
            // LoogriGram: deleting a contact used to delete the row this app had
            // written into the phone's address book and mark the number deleted
            // in the cached phonebook. It writes neither now.

            AndroidUtilities.runOnUIThread(() -> {
                boolean remove = false;
                for (TLRPC.User user : users) {
                    TLRPC.TL_contact contact = contactsDict.get(user.id);
                    if (contact != null) {
                        remove = true;
                        contacts.remove(contact);
                        contactsDict.remove(user.id);
                    }
                }
                if (remove) {
                    buildContactsSectionsArrays(false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                if (showBulletin) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.formatString("DeletedFromYourContacts", R.string.DeletedFromYourContacts, userName));
                }
            });
        });
    }

    private void reloadContactsStatuses() {
        saveContactsLoadTime();
        getMessagesController().clearFullUsers();
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("needGetStatuses", true).commit();
        TLRPC.TL_contacts_getStatuses req = new TLRPC.TL_contacts_getStatuses();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof Vector) {
                AndroidUtilities.runOnUIThread(() -> {
                    editor.remove("needGetStatuses").commit();
                    Vector vector = (Vector) response;
                    if (!vector.objects.isEmpty()) {
                        ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                        for (Object object : vector.objects) {
                            TLRPC.User toDbUser = new TLRPC.TL_user();
                            TLRPC.TL_contactStatus status = (TLRPC.TL_contactStatus) object;

                            if (status == null) {
                                continue;
                            }
                            if (status.status instanceof TLRPC.TL_userStatusRecently) {
                                status.status.expires = status.status.by_me ? -1000 : -100;
                            } else if (status.status instanceof TLRPC.TL_userStatusLastWeek) {
                                status.status.expires = status.status.by_me ? -1001 : -101;
                            } else if (status.status instanceof TLRPC.TL_userStatusLastMonth) {
                                status.status.expires = status.status.by_me ? -1002 : -102;
                            }

                            TLRPC.User user = getMessagesController().getUser(status.user_id);
                            if (user != null) {
                                user.status = status.status;
                            }
                            toDbUser.status = status.status;
                            dbUsersStatus.add(toDbUser);
                        }
                        getMessagesStorage().updateUsers(dbUsersStatus, true, true, true);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS);
                });
            }
        });
    }

    public void loadGlobalPrivacySetting() {
        if (loadingGlobalSettings == 0) {
            loadingGlobalSettings = 1;
            TL_account.getGlobalPrivacySettings req = new TL_account.getGlobalPrivacySettings();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    globalPrivacySettings = (TLRPC.GlobalPrivacySettings) response;
                    loadingGlobalSettings = 2;
                } else {
                    loadingGlobalSettings = 0;
                }
                getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
            }));
        }
    }

    public void loadPrivacySettings() {
        loadPrivacySettings(false);
    }
    public void loadPrivacySettings(boolean force) {
        if (loadingDeleteInfo == 0) {
            loadingDeleteInfo = 1;
            TL_account.getAccountTTL req = new TL_account.getAccountTTL();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_accountDaysTTL ttl = (TLRPC.TL_accountDaysTTL) response;
                    deleteAccountTTL = ttl.days;
                    loadingDeleteInfo = 2;
                } else {
                    loadingDeleteInfo = 0;
                }
                getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
            }));
        }
        loadGlobalPrivacySetting();
        for (int a = 0; a < loadingPrivacyInfo.length; a++) {
            if (force ? loadingPrivacyInfo[a] == 1 : loadingPrivacyInfo[a] != 0) {
                continue;
            }
            loadingPrivacyInfo[a] = 1;
            final int num = a;

            TL_account.getPrivacy req = new TL_account.getPrivacy();

            switch (num) {
                case PRIVACY_RULES_TYPE_LASTSEEN:
                    req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
                    break;
                case PRIVACY_RULES_TYPE_INVITE:
                    req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
                    break;
                case PRIVACY_RULES_TYPE_CALLS:
                    req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
                    break;
                case PRIVACY_RULES_TYPE_P2P:
                    req.key = new TLRPC.TL_inputPrivacyKeyPhoneP2P();
                    break;
                case PRIVACY_RULES_TYPE_PHOTO:
                    req.key = new TLRPC.TL_inputPrivacyKeyProfilePhoto();
                    break;
                case PRIVACY_RULES_TYPE_BIO:
                    req.key = new TLRPC.TL_inputPrivacyKeyAbout();
                    break;
                case PRIVACY_RULES_TYPE_MUSIC:
                    req.key = new TLRPC.TL_inputPrivacyKeySavedMusic();
                    break;
                case PRIVACY_RULES_TYPE_FORWARDS:
                    req.key = new TLRPC.TL_inputPrivacyKeyForwards();
                    break;
                case PRIVACY_RULES_TYPE_PHONE:
                    req.key = new TLRPC.TL_inputPrivacyKeyPhoneNumber();
                    break;
                case PRIVACY_RULES_TYPE_BIRTHDAY:
                    req.key = new TLRPC.TL_inputPrivacyKeyBirthday();
                    break;
                case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                    req.key = new TLRPC.TL_inputPrivacyKeyAddedByPhone();
                    break;
                default:
                    continue;
            }

            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TL_account.privacyRules rules = (TL_account.privacyRules) response;
                    getMessagesController().putUsers(rules.users, false);
                    getMessagesController().putChats(rules.chats, false);

                    switch (num) {
                        case PRIVACY_RULES_TYPE_LASTSEEN:
                            lastseenPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_INVITE:
                            groupPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_CALLS:
                            callPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_P2P:
                            p2pPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_PHOTO:
                            profilePhotoPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_BIO:
                            bioPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_MUSIC:
                            musicPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_BIRTHDAY:
                            birthdayPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_FORWARDS:
                            forwardsPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_PHONE:
                            phonePrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                        default:
                            addedByPhonePrivacyRules = rules.rules;
                            break;
                    }
                    loadingPrivacyInfo[num] = 2;
                } else {
                    loadingPrivacyInfo[num] = 0;
                }
                getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
            }));
        }
        getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
    }

    public void setDeleteAccountTTL(int ttl) {
        deleteAccountTTL = ttl;
    }

    public int getDeleteAccountTTL() {
        return deleteAccountTTL;
    }

    public boolean getLoadingDeleteInfo() {
        return loadingDeleteInfo != 2;
    }

    public boolean getLoadingGlobalSettings() {
        return loadingGlobalSettings != 2;
    }

    public boolean getLoadingPrivacyInfo(int type) {
        return loadingPrivacyInfo[type] != 2;
    }

    public TLRPC.GlobalPrivacySettings getGlobalPrivacySettings() {
        return globalPrivacySettings;
    }

    // LoogriGram: the server-side half of ghost mode. Suppressing presence in
    // the client is not enough on its own - the server also infers "last seen
    // recently" from MTProto session activity, so without this the user still
    // shows as recently online no matter what the client withholds. Read
    // receipts are not suppressed at all (see MessagesController), so hiding
    // the read *date* is the part of that which can be had without breaking
    // read-position sync between devices.
    //
    // Applied once per account, and again on every explicit enable, but never
    // reversed: turning ghost mode off leaves both settings alone rather than
    // re-exposing last seen, which is the safer direction to fail in.
    //
    // Both are reciprocal and enforced by Telegram, not by us: hiding last seen
    // means seeing only a vague "recently" for everyone else, and hiding the
    // read date likewise costs seeing other people's. hide_read_marks is free
    // and does not need Premium.
    public void applyGhostModePrivacy(boolean force) {
        if (!SharedConfig.ghostMode || !getUserConfig().isClientActivated()) {
            return;
        }
        final SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        if (!force && preferences.getBoolean("ghostPrivacyApplied", false)) {
            return;
        }

        // Note: this replaces the last-seen rules outright, so any existing
        // exception list for last seen goes with it. Nobody is the point, and
        // an exception to it would defeat it.
        final TL_account.setPrivacy lastSeen = new TL_account.setPrivacy();
        lastSeen.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        lastSeen.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        getConnectionsManager().sendRequest(lastSeen, (response, error) -> {
            if (error != null) {
                FileLog.e("LoogriGram: could not hide last seen: " + error.text);
            }
        });

        // setGlobalPrivacySettings replaces the whole object, which also holds
        // the archive settings - archive_and_mute_new_noncontact_peers,
        // keep_archived_unmuted and so on. Sending a fresh one to set a single
        // flag would silently reset those, and at app start the cached copy is
        // usually still null, so the current settings are read first and only
        // then amended.
        if (globalPrivacySettings != null) {
            sendHideReadMarks(globalPrivacySettings);
        } else {
            final TL_account.getGlobalPrivacySettings req = new TL_account.getGlobalPrivacySettings();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null || !(response instanceof TLRPC.GlobalPrivacySettings)) {
                    FileLog.e("LoogriGram: could not read global privacy settings, not hiding read marks");
                    return;
                }
                final TLRPC.GlobalPrivacySettings settings = (TLRPC.GlobalPrivacySettings) response;
                AndroidUtilities.runOnUIThread(() -> {
                    globalPrivacySettings = settings;
                    loadingGlobalSettings = 2;
                    sendHideReadMarks(settings);
                });
            });
        }

        // Recorded even if a request fails. Retrying on every start would mean
        // two extra requests per launch forever on an account where the server
        // keeps refusing; the explicit-enable path is the way to retry.
        preferences.edit().putBoolean("ghostPrivacyApplied", true).apply();
    }

    private void sendHideReadMarks(TLRPC.GlobalPrivacySettings current) {
        if (current.hide_read_marks) {
            return;
        }
        final TL_account.setGlobalPrivacySettings req = new TL_account.setGlobalPrivacySettings();
        req.settings = current;
        req.settings.hide_read_marks = true;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.e("LoogriGram: could not hide read marks: " + error.text);
            }
        });
    }

    public ArrayList<TLRPC.PrivacyRule> getPrivacyRules(int type) {
        switch (type) {
            case PRIVACY_RULES_TYPE_LASTSEEN:
                return lastseenPrivacyRules;
            case PRIVACY_RULES_TYPE_INVITE:
                return groupPrivacyRules;
            case PRIVACY_RULES_TYPE_CALLS:
                return callPrivacyRules;
            case PRIVACY_RULES_TYPE_P2P:
                return p2pPrivacyRules;
            case PRIVACY_RULES_TYPE_PHOTO:
                return profilePhotoPrivacyRules;
            case PRIVACY_RULES_TYPE_BIO:
                return bioPrivacyRules;
            case PRIVACY_RULES_TYPE_MUSIC:
                return musicPrivacyRules;
            case PRIVACY_RULES_TYPE_BIRTHDAY:
                return birthdayPrivacyRules;
            case PRIVACY_RULES_TYPE_FORWARDS:
                return forwardsPrivacyRules;
            case PRIVACY_RULES_TYPE_PHONE:
                return phonePrivacyRules;
            case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                return addedByPhonePrivacyRules;
        }
        return null;
    }

    public void setPrivacyRules(ArrayList<TLRPC.PrivacyRule> rules, int type) {
        switch (type) {
            case PRIVACY_RULES_TYPE_LASTSEEN:
                lastseenPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_INVITE:
                groupPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_CALLS:
                callPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_P2P:
                p2pPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_PHOTO:
                profilePhotoPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_BIO:
                bioPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_MUSIC:
                musicPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_BIRTHDAY:
                birthdayPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_FORWARDS:
                forwardsPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_PHONE:
                phonePrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                addedByPhonePrivacyRules = rules;
                break;
        }
        getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
        reloadContactsStatuses();
    }



    public static String formatName(TLObject object) {
        if (object instanceof TLRPC.User) {
            return formatName((TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            return chat.title;
        } else {
            return LocaleController.getString(R.string.HiddenName);
        }
    }

    @NonNull
    public static String formatName(TLRPC.User user) {
        if (user == null) {
            return "";
        }
        return formatName(user.first_name, user.last_name, 0);
    }

    @NonNull
    public static String formatName(String firstName, String lastName) {
        return formatName(firstName, lastName, 0);
    }

    @NonNull
    public static String formatName(String firstName, String lastName, int maxLength) {
        /*if ((firstName == null || firstName.length() == 0) && (lastName == null || lastName.length() == 0)) {
            return LocaleController.getString(R.string.HiddenName);
        }*/
        if (firstName != null) {
            firstName = firstName.trim();
        }
        if (firstName != null && lastName == null && maxLength > 0 && firstName.contains(" ") ) {
            int i = firstName.indexOf(" ");
            lastName = firstName.substring(i + 1);
            firstName = firstName.substring(0, i);
        }
        if (lastName != null) {
            lastName = lastName.trim();
        }
        StringBuilder result = new StringBuilder((firstName != null ? firstName.length() : 0) + (lastName != null ? lastName.length() : 0) + 1);
        if (LocaleController.nameDisplayOrder == 1) {
            if (firstName != null && firstName.length() > 0) {
                if (maxLength > 0 && firstName.length() > maxLength + 2) {
                    return firstName.substring(0, maxLength) + "…";
                }
                result.append(firstName);
                if (lastName != null && lastName.length() > 0) {
                    result.append(" ");
                    if (maxLength > 0 && result.length() + lastName.length() > maxLength) {
                        result.append(lastName.charAt(0));
                    } else {
                        result.append(lastName);
                    }
                }
            } else if (lastName != null && lastName.length() > 0) {
                if (maxLength > 0 && lastName.length() > maxLength + 2) {
                    return lastName.substring(0, maxLength) + "…";
                }
                result.append(lastName);
            }
        } else {
            if (lastName != null && lastName.length() > 0) {
                if (maxLength > 0 && lastName.length() > maxLength + 2) {
                    return lastName.substring(0, maxLength) + "…";
                }
                result.append(lastName);
                if (firstName != null && firstName.length() > 0) {
                    result.append(" ");
                    if (maxLength > 0 && result.length() + firstName.length() > maxLength) {
                        result.append(firstName.charAt(0));
                    } else {
                        result.append(firstName);
                    }
                }
            } else if (firstName != null && firstName.length() > 0) {
                if (maxLength > 0 && firstName.length() > maxLength + 2) {
                    return firstName.substring(0, maxLength) + "…";
                }
                result.append(firstName);
            }
        }
        return result.toString();
    }

    private class PhoneBookContact {
        String id;
        String lookup_key;
        String name;
        String phone;
    }


}
