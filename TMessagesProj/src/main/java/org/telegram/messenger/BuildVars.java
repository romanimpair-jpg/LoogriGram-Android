/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;



public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.DEBUG_VERSION;
    public static boolean LOGS_ENABLED = BuildConfig.DEBUG_VERSION;
    public static boolean DEBUG_PRIVATE_VERSION = BuildConfig.DEBUG_PRIVATE_VERSION;
    public static boolean USE_CLOUD_STRINGS = true;
    // LoogriGram: no auto-updates. See LaunchActivity.checkAppUpdate, which is
    // also guarded outright - this flag is bypassed on the force path.
    public static boolean CHECK_UPDATES = false;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static String BUILD_VERSION_STRING = BuildConfig.BUILD_VERSION_STRING;

    public static int APP_ID = 4;
    public static String APP_HASH = "014b35b6184100b085b0d0572f9b5103";

    // LoogriGram: SAFETYNET_KEY and GOOGLE_AUTH_CLIENT_ID are Telegram's own
    // Google credentials, and as of this version nothing in the tree reads
    // either of them - they are dead constants. Emptied rather than left
    // sitting in our source: shipping somebody else's API key is a bad habit
    // even when it is inert, and if a rebase ever wires them up again, empty
    // is the value that disables the Google Identity path.
    public static String SAFETYNET_KEY = "";
    public static String GOOGLE_AUTH_CLIENT_ID = "";

    // These are the "your client is too old, update it" destinations. Upstream
    // points them at the official app's store pages, which for this fork would
    // send the user off to install Telegram instead. There is no auto-update
    // here, so the honest destination is where the build actually comes from.
    public static String PLAYSTORE_APP_URL = "https://github.com/romanimpair-jpg/LoogriGram-Android";
    public static String HUAWEI_STORE_URL = "https://github.com/romanimpair-jpg/LoogriGram-Android";

    public static String HUAWEI_APP_ID = "101184875";

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    // LoogriGram: taking upstream at its word - this is the fork flag, and it
    // closes the gift and premium buttons that the premium getters do not
    // reach on their own.
    public static boolean IS_BILLING_UNAVAILABLE = true;

    // works only on official app ids, disable on your forks
    // LoogriGram: doing as the comment above says. Passkeys need the Google
    // credentials provider anyway, which is not present here.
    public static boolean SUPPORTS_PASSKEYS = false;

    static {
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
            LOGS_ENABLED = DEBUG_VERSION || sharedPreferences.getBoolean("logsEnabled", DEBUG_VERSION);
            if (LOGS_ENABLED) {
                final Thread.UncaughtExceptionHandler pastHandler = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
                    FileLog.fatal(exception, false);
                    if (pastHandler != null) {
                        pastHandler.uncaughtException(thread, exception);
                    }
                });
            }
        }
    }

    // LoogriGram: always Telegram's own invoices, never Google Play Billing.
    //
    // Upstream reaches the same answer here by a longer route -
    // billingClientEmpty is true when there is no Play Billing, which is now
    // permanent - but it is stated outright because it is a decision, not a
    // consequence of the device. hasDirectCurrency went with it: that asked the
    // Play product details whether the subscription is priced in one of the
    // currencies Telegram bills directly, and there are no product details.
    public static boolean useInvoiceBilling() {
        return true;
    }

    private static Boolean betaApp;
    public static boolean isBetaApp() {
        if (betaApp == null) {
            betaApp = ApplicationLoader.applicationContext != null && "org.telegram.messenger.beta".equals(ApplicationLoader.applicationContext.getPackageName());
        }
        return betaApp;
    }


    public static boolean isHuaweiStoreApp() {
        return ApplicationLoader.isHuaweiStoreBuild();
    }

    public static String getSmsHash() {
        return ApplicationLoader.isStandaloneBuild() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    }
}
