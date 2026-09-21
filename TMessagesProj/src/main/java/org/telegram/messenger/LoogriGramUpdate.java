package org.telegram.messenger;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * LoogriGram: the updater. There is no store and no Telegram update channel
 * here, so the app watches this fork's own GitHub releases and installs them
 * itself - the Android half of the desktop fork's loogrigram_update.cpp.
 *
 * What it does, in order: ask the releases API which tag is newest, compare it
 * with the tag baked into this build, download that release's APK, and hand the
 * file to the system installer.
 *
 * Things worth knowing:
 *
 * - The request is an anonymous GET to api.github.com and carries nothing about
 *   the account: no token, no id, not even a Telegram connection. It is the
 *   only outbound request in this app that does not go to Telegram.
 * - Releases, not CI artifacts, because downloading an artifact needs an
 *   authenticated token even on a public repository.
 * - The version is the commit. CI stamps the short sha into BuildConfig as
 *   LOOGRIGRAM_TAG and tags the release with the same string; comparing them is
 *   the whole check. A locally built APK carries "dev", which matches no
 *   release, so it never tries to update itself.
 * - Installing is a PackageInstaller session that asks for no user action.
 *   Android 12+ honours that for an app updating itself once the user has
 *   allowed it to install unknown apps - a one-time switch in settings that
 *   no app can flip for itself; only a preinstalled store holds
 *   INSTALL_PACKAGES and skips it. Until it is on, or below Android 12, the
 *   session reports STATUS_PENDING_USER_ACTION and LoogriGramInstallReceiver
 *   opens the system's confirmation, which leads to that switch the first
 *   time. Installing replaces the running process, so the app is killed at
 *   that point - that is normal - and AppStartReceiver restarts the push
 *   service on MY_PACKAGE_REPLACED.
 * - The APK must be signed with the same key as the installed one or Android
 *   refuses the update. CI signs with ours; see LOOGRIGRAM.md.
 */
public class LoogriGramUpdate {

    public static final int STATE_NONE = 0;
    public static final int STATE_CHECKING = 1;
    public static final int STATE_AVAILABLE = 2;
    public static final int STATE_DOWNLOADING = 3;
    public static final int STATE_READY = 4;
    /** Handed to the system; this process is replaced if it succeeds. */
    public static final int STATE_INSTALLING = 5;

    /** The tag a build that was not made by CI carries. Matches no release. */
    private static final String DEV_TAG = "dev";

    private static final String RELEASES_URL =
        "https://api.github.com/repos/romanimpair-jpg/LoogriGram-Android/releases/latest";

    /** Results reported to a manual check. */
    public static final int RESULT_CURRENT = 0;
    public static final int RESULT_FOUND = 1;
    public static final int RESULT_FAILED = 2;

    /**
     * How long to leave it before asking again on resume. The first check of
     * every run ignores this. It was a day, which meant a release published an
     * hour after a check stayed invisible until the next day.
     */
    private static final long CHECK_INTERVAL = 60 * 60 * 1000L;

    /** An APK far smaller than this is an error page, not a build. */
    private static final long MIN_APK_SIZE = 1024 * 1024;

    private static volatile LoogriGramUpdate instance;

    public static LoogriGramUpdate getInstance() {
        if (instance == null) {
            synchronized (LoogriGramUpdate.class) {
                if (instance == null) {
                    instance = new LoogriGramUpdate();
                }
            }
        }
        return instance;
    }

    private int state = STATE_NONE;
    private float progress;
    private String availableTag;
    private String availableUrl;
    private long availableSize;
    private String readyPath;
    private boolean downloading;

    /** Set once the user has been asked about a given tag, so it asks once. */
    private String askedTag;

    /** False until this process has asked once; that first ask is never skipped. */
    private boolean checkedThisRun;

    /** Waiting on the check in flight: the Settings row that asked for it. */
    private Utilities.Callback<Integer> pendingResult;

    private LoogriGramUpdate() {
        SharedPreferences prefs = getPrefs();
        availableTag = prefs.getString("availableTag", null);
        availableUrl = prefs.getString("availableUrl", null);
        availableSize = prefs.getLong("availableSize", 0);
        askedTag = prefs.getString("askedTag", null);

        final String readyTag = prefs.getString("readyTag", null);
        final String path = prefs.getString("readyPath", null);
        if (readyTag != null && path != null && isNewer(readyTag)) {
            final File file = new File(path);
            if (file.exists() && file.length() >= MIN_APK_SIZE) {
                readyPath = path;
                availableTag = readyTag;
                state = STATE_READY;
            } else {
                forgetDownload();
            }
        } else if (readyTag != null) {
            // it is this build, or older: the download did its job
            forgetDownload();
        }

        if (state != STATE_READY && availableTag != null && isNewer(availableTag)) {
            state = STATE_AVAILABLE;
        }
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("loogrigram_update", Context.MODE_PRIVATE);
    }

    public static String currentTag() {
        return BuildConfig.LOOGRIGRAM_TAG;
    }

    /** A build CI made, rather than one built by hand, is the only one that updates. */
    public static boolean updatesEnabled() {
        final String tag = currentTag();
        return tag != null && !DEV_TAG.equals(tag) && tag.length() > 0;
    }

    private static boolean isNewer(String tag) {
        return tag != null && tag.length() > 0 && !tag.equals(currentTag());
    }

    public int getState() {
        return state;
    }

    public float getProgress() {
        return progress;
    }

    public String getAvailableTag() {
        return availableTag;
    }

    /** True once for each newly found version, so the prompt does not nag. */
    public boolean shouldAskAboutDownload() {
        return state == STATE_AVAILABLE && availableTag != null && !availableTag.equals(askedTag);
    }

    public void markAsked() {
        askedTag = availableTag;
        getPrefs().edit().putString("askedTag", askedTag).apply();
    }

    private void setState(int newState) {
        state = newState;
        AndroidUtilities.runOnUIThread(() ->
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.loogriGramUpdateChanged));
    }

    /**
     * Called on every resume. The first call of a run always asks; after that
     * at most once an hour, unless forced.
     */
    public void checkForUpdate(boolean force) {
        checkForUpdate(force, null);
    }

    /**
     * The manual check behind the Settings row: always asks, and reports what
     * it found. A newer build found this way offers itself again even if its
     * prompt was refused before - asking by hand is asking to be asked.
     */
    public void checkForUpdate(boolean force, Utilities.Callback<Integer> onResult) {
        if (!updatesEnabled()) {
            if (onResult != null) {
                onResult.run(RESULT_CURRENT);
            }
            return;
        }
        if (state == STATE_DOWNLOADING || state == STATE_READY || state == STATE_INSTALLING) {
            if (onResult != null) {
                onResult.run(RESULT_FOUND);
            }
            return;
        }
        if (onResult != null) {
            pendingResult = onResult;
        }
        if (state == STATE_CHECKING) {
            return;
        }
        final long last = getPrefs().getLong("lastCheckTime", 0);
        if (!force && checkedThisRun && Math.abs(System.currentTimeMillis() - last) < CHECK_INTERVAL) {
            return;
        }
        checkedThisRun = true;
        setState(STATE_CHECKING);
        Utilities.globalQueue.postRunnable(this::checkInternal);
    }

    private void checkInternal() {
        String tag = null;
        String url = null;
        long size = 0;
        boolean answered = false;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "LoogriGram");
            if (connection.getResponseCode() == 200) {
                final JSONObject release = new JSONObject(readAll(connection.getInputStream()));
                answered = true;
                tag = release.optString("tag_name", null);
                final JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int a = 0; a < assets.length(); a++) {
                        final JSONObject asset = assets.optJSONObject(a);
                        if (asset == null) {
                            continue;
                        }
                        final String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            url = asset.optString("browser_download_url", null);
                            size = asset.optLong("size", 0);
                            break;
                        }
                    }
                }
            } else if (BuildVars.LOGS_ENABLED) {
                FileLog.d("LoogriGramUpdate: releases API returned " + connection.getResponseCode());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        final String foundTag = tag;
        final String foundUrl = url;
        final long foundSize = size;
        final boolean ok = answered;
        AndroidUtilities.runOnUIThread(() -> {
            final Utilities.Callback<Integer> onResult = pendingResult;
            pendingResult = null;
            // only an answer restarts the clock; a failed request (no network,
            // rate limit) leaves the next resume free to try again
            if (ok) {
                getPrefs().edit().putLong("lastCheckTime", System.currentTimeMillis()).apply();
            }
            if (foundTag != null && foundUrl != null && isNewer(foundTag)) {
                availableTag = foundTag;
                availableUrl = foundUrl;
                availableSize = foundSize;
                final SharedPreferences.Editor editor = getPrefs().edit()
                    .putString("availableTag", availableTag)
                    .putString("availableUrl", availableUrl)
                    .putLong("availableSize", availableSize);
                if (onResult != null) {
                    askedTag = null;
                    editor.remove("askedTag");
                }
                editor.apply();
                setState(STATE_AVAILABLE);
                if (onResult != null) {
                    onResult.run(RESULT_FOUND);
                }
            } else {
                // a failed check keeps an update already found on offer
                setState(!ok && availableTag != null && isNewer(availableTag) ? STATE_AVAILABLE : STATE_NONE);
                if (onResult != null) {
                    onResult.run(ok ? RESULT_CURRENT : RESULT_FAILED);
                }
            }
        });
    }

    /** Starts fetching the APK found by the last check. */
    public void startDownload() {
        if (state != STATE_AVAILABLE || availableUrl == null || downloading) {
            return;
        }
        downloading = true;
        progress = 0;
        setState(STATE_DOWNLOADING);
        final String url = availableUrl;
        final String tag = availableTag;
        Utilities.globalQueue.postRunnable(() -> downloadInternal(url, tag));
    }

    private void downloadInternal(String url, String tag) {
        File target = null;
        boolean ok = false;
        HttpURLConnection connection = null;
        try {
            final File dir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "updates");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception("cannot create " + dir);
            }
            final File[] previous = dir.listFiles();
            if (previous != null) {
                for (File old : previous) {
                    old.delete();
                }
            }
            target = new File(dir, "LoogriGram-" + tag + ".apk");

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LoogriGram");
            final long total = availableSize > 0 ? availableSize : connection.getContentLength();

            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
                final byte[] buffer = new byte[64 * 1024];
                long written = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    written += read;
                    if (total > 0) {
                        final float value = Math.min(1f, (float) written / total);
                        AndroidUtilities.runOnUIThread(() -> {
                            progress = value;
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.loogriGramUpdateChanged);
                        });
                    }
                }
            }

            // an error page saved under an .apk name is still a file; a zip
            // starts "PK", and a build of ours is never this small
            ok = target.length() >= MIN_APK_SIZE && startsWithZipMagic(target);
            if (!ok && BuildVars.LOGS_ENABLED) {
                FileLog.d("LoogriGramUpdate: downloaded file is not an APK (" + target.length() + " bytes)");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        final boolean success = ok;
        final File file = target;
        AndroidUtilities.runOnUIThread(() -> {
            downloading = false;
            progress = 0;
            if (success) {
                readyPath = file.getAbsolutePath();
                getPrefs().edit()
                    .putString("readyTag", tag)
                    .putString("readyPath", readyPath)
                    .apply();
                setState(STATE_READY);
            } else {
                if (file != null) {
                    file.delete();
                }
                setState(STATE_AVAILABLE);
            }
        });
    }

    private static String readAll(InputStream in) throws Exception {
        final StringBuilder builder = new StringBuilder();
        final byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            builder.append(new String(buffer, 0, read, "UTF-8"));
        }
        in.close();
        return builder.toString();
    }

    private static boolean startsWithZipMagic(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return in.read() == 'P' && in.read() == 'K';
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /**
     * Installs the downloaded APK through a PackageInstaller session - see the
     * class comment for when Android lets that happen without asking. The
     * outcome arrives at LoogriGramInstallReceiver, except a success, which
     * replaces this process before anything could hear about it.
     */
    public void install(Activity activity) {
        if (state != STATE_READY || readyPath == null || activity == null) {
            return;
        }
        final File file = new File(readyPath);
        if (!file.exists()) {
            forgetDownload();
            setState(STATE_AVAILABLE);
            return;
        }
        final Context context = activity.getApplicationContext();
        setState(STATE_INSTALLING);
        Utilities.globalQueue.postRunnable(() -> {
            final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            boolean committed = false;
            try {
                final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(context.getPackageName());
                params.setSize(file.length());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                }
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream in = new FileInputStream(file); OutputStream out = session.openWrite("base.apk", 0, file.length())) {
                        final byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                        session.fsync(out);
                    }
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // the installer writes the status into this intent
                        flags |= PendingIntent.FLAG_MUTABLE;
                    }
                    final Intent result = new Intent(context, LoogriGramInstallReceiver.class);
                    session.commit(PendingIntent.getBroadcast(context, sessionId, result, flags).getIntentSender());
                    committed = true;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (!committed) {
                if (sessionId != -1) {
                    try {
                        installer.abandonSession(sessionId);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                AndroidUtilities.runOnUIThread(this::onInstallFailed);
            }
        });
    }

    /** The install failed or was declined: offer it again. */
    public void onInstallFailed() {
        if (state == STATE_INSTALLING) {
            setState(STATE_READY);
        }
    }

    /**
     * Forgets a finished download - after installing it, or when it is stale -
     * and deletes the APK, which would otherwise sit in the app's files until
     * the next download cleared it.
     */
    public void forgetDownload() {
        final String path = getPrefs().getString("readyPath", null);
        if (path != null) {
            new File(path).delete();
        }
        readyPath = null;
        getPrefs().edit().remove("readyTag").remove("readyPath").apply();
    }
}
