package org.telegram.ui.Components.Premium.boosts;

import android.os.Build;
import android.util.Pair;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BoostRepository {

    // LoogriGram: this was the boost and giveaway backend - applying and
    // reassigning boosts, fetching my boosts, paying for gift codes and
    // giveaways (in the app and by invoice), launching a prepaid giveaway,
    // checking and applying a gift code, giveaway info, gift option
    // filtering and the giveaway limits. Its screens are gone; what is left
    // serves the user picker, the poll country picker and contact search.

    private static HashMap<Integer, Pair<Long, List<TLRPC.TL_premiumGiftCodeOption>>> cachedGiftOptions;

    public static void loadParticipantsCount(Utilities.Callback<HashMap<Long, Integer>> callback) {
        MessagesStorage storage = MessagesStorage.getInstance(UserConfig.selectedAccount);
        storage.getStorageQueue().postRunnable(() -> {
            HashMap<Long, Integer> participantsCountByChat = storage.getSmallGroupsParticipantsCount();
            if (participantsCountByChat == null || participantsCountByChat.isEmpty()) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(participantsCountByChat));
        });
    }

    public static void loadCountriesForPolls(Utilities.Callback<Pair<Map<String, List<TLRPC.TL_help_country>>, List<String>>> onDone) {
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);

        TLRPC.TL_help_getCountriesList req = new TLRPC.TL_help_getCountriesList();
        req.lang_code = LocaleController.getInstance().getCurrentLocaleInfo() != null ? LocaleController.getInstance().getCurrentLocaleInfo().getLangCode() : Locale.getDefault().getCountry();
        int reqId = connection.sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_help_countriesList help_countriesList = (TLRPC.TL_help_countriesList) response;
                Map<String, List<TLRPC.TL_help_country>> countriesMap = new HashMap<>();
                List<String> sortedLetters = new ArrayList<>();

                for (int i = 0; i < help_countriesList.countries.size(); i++) {
                    TLRPC.TL_help_country country = help_countriesList.countries.get(i);
                    final boolean isFragment = country.iso2.equalsIgnoreCase("FT");
                    if (country.name != null) {
                        country.default_name = country.name;
                    }
                    if (country.hidden && !isFragment) {
                        continue;
                    }
                    if (isFragment) {
                        country.name = country.default_name = LocaleController.getString(R.string.Fragment);
                    }

                    String letter = country.default_name.substring(0, 1).toUpperCase();
                    List<TLRPC.TL_help_country> arr = countriesMap.get(letter);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        countriesMap.put(letter, arr);
                        sortedLetters.add(letter);
                    }
                    arr.add(country);
                }

                Comparator<String> comparator;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Collator collator = Collator.getInstance(LocaleController.getInstance().getCurrentLocale() != null ? LocaleController.getInstance().getCurrentLocale() : Locale.getDefault());
                    comparator = collator::compare;
                } else {
                    comparator = String::compareTo;
                }
                Collections.sort(sortedLetters, comparator);
                for (List<TLRPC.TL_help_country> arr : countriesMap.values()) {
                    Collections.sort(arr, (country, country2) -> comparator.compare(country.default_name, country2.default_name));
                }
                AndroidUtilities.runOnUIThread(() -> onDone.run(new Pair<>(countriesMap, sortedLetters)));
            }
        });
    }

    public static List<TLRPC.TL_premiumGiftCodeOption> getCachedGiftOptions(int currentAccount) {
        if (cachedGiftOptions == null) return null;
        Pair<Long, List<TLRPC.TL_premiumGiftCodeOption>> pair = cachedGiftOptions.get(currentAccount);
        if (pair != null && System.currentTimeMillis() - pair.first < 1000 * 60 * 30) {
            return pair.second;
        }
        return null;
    }

    public static void saveGiftOptionsToCache(int currentAccount, List<TLRPC.TL_premiumGiftCodeOption> options) {
        if (cachedGiftOptions == null) cachedGiftOptions = new HashMap<>();
        cachedGiftOptions.put(currentAccount, new Pair<>(System.currentTimeMillis(), options));
    }

    public static int loadGiftOptions(int currentAccount, TLRPC.Chat chat, Utilities.Callback<List<TLRPC.TL_premiumGiftCodeOption>> onDone) {
        if (chat == null) {
            List<TLRPC.TL_premiumGiftCodeOption> cached = getCachedGiftOptions(currentAccount);
            if (cached != null) {
                onDone.run(cached);
                return -1;
            }
        }

        MessagesController controller = MessagesController.getInstance(currentAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(currentAccount);
        TLRPC.TL_payments_getPremiumGiftCodeOptions req = new TLRPC.TL_payments_getPremiumGiftCodeOptions();
        if (chat != null) {
            req.flags = 1;
            req.boost_peer = controller.getInputPeer(-chat.id);
        }

        return connection.sendRequest(req, (response, error) -> {
            if (response instanceof Vector) {
                final Vector<TLRPC.TL_premiumGiftCodeOption> vector = (Vector) response;
                final List<TLRPC.TL_premiumGiftCodeOption> result = new ArrayList<>();
                for (int i = 0; i < vector.objects.size(); i++) {
                    result.add(vector.objects.get(i));
                }
                // LoogriGram: options keep the server's own prices. Upstream
                // collected each option's store_product and re-priced them from
                // Play product details when Google billing was available; it
                // never is now, so this is the branch upstream already took when
                // there were no store products to query.
                AndroidUtilities.runOnUIThread(() -> {
                    if (chat == null) {
                        saveGiftOptionsToCache(currentAccount, result);
                    }
                    onDone.run(result);
                });
            }
        });
    }

    public static int searchContacts(String query, boolean allowBots, Utilities.Callback<List<TLRPC.User>> onDone) {
        MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
        ConnectionsManager connection = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        if (query == null || query.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> onDone.run(Collections.emptyList()));
            return 0;
        }
        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        req.limit = 50;
        return connection.sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_contacts_found) {
                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                controller.putUsers(res.users, false);
                List<TLRPC.User> result = new ArrayList<>();
                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User user = res.users.get(a);
                    if (!user.self && !UserObject.isDeleted(user) && (allowBots || !user.bot) && !UserObject.isService(user.id)) {
                        result.add(user);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> onDone.run(result));
            }
        });
    }

    public static void searchContactsLocally(String query, boolean allowBots, Utilities.Callback<List<TLRPC.User>> onDone) {
        final int currentAccount = UserConfig.selectedAccount;
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.TL_contact> contacts = ContactsController.getInstance(currentAccount).contacts;
        if (contacts == null || contacts.isEmpty()) {
            ContactsController.getInstance(currentAccount).loadContacts(false, 0);
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final String q = query.toLowerCase();
        final String qt = AndroidUtilities.translitSafe(q);
        if (contacts != null) {
            for (int i = 0; i < contacts.size(); ++i) {
                final TLRPC.TL_contact contact = contacts.get(i);
                if (contact != null) {
                    final TLRPC.User user = messagesController.getUser(contact.user_id);
                    if (user == null || !allowBots && user.bot || UserObject.isService(user.id) || UserObject.isUserSelf(user)) continue;
                    final String u = UserObject.getUserName(user).toLowerCase();
                    final String ut = AndroidUtilities.translitSafe(u);
                    if (u.startsWith(q) || u.contains(" " + q) || ut.startsWith(qt) || ut.contains(" " + qt)) {
                        users.add(user);
                    } else if (user.usernames != null) {
                        for (int j = 0; j < user.usernames.size(); ++j) {
                            TLRPC.TL_username username = user.usernames.get(j);
                            if (username == null || !username.active) continue;
                            final String us = username.username.toLowerCase();
                            if (us.startsWith(q) || us.contains("_" + q) || us.startsWith(qt) || us.contains(" " + qt)) {
                                users.add(user);
                                break;
                            }
                        }
                    } else if (user.username != null) {
                        final String us = user.username.toLowerCase();
                        if (us.startsWith(q) || us.contains("_" + q) || us.startsWith(qt) || us.contains(" " + qt)) {
                            users.add(user);
                        }
                    }
                }
            }
        }
        onDone.run(users);
    }
}
