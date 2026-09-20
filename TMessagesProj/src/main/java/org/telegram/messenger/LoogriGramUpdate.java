package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
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
 * - Android cannot install silently. install() opens the system package
 *   installer, which asks the user; the first time, it also sends them to
 *   settings to allow installs from this app. Installing replaces the running
 *   process, so the app is killed at that point - that is normal.
 * - The APK must be signed with the same key as the installed one or Android
 *   refuses the update. CI signs with ours; see LOOGRIGRAM.md.
 */
public class LoogriGramUpdate {

    public static final int STATE_NONE = 0;
    public static final int STATE_CHECKING = 1;
    public static final int STATE_AVAILABLE = 2;
    public static final int STATE_DOWNLOADING = 3;
    public static final int STATE_READY = 4;

    /** The tag a build that was not made by CI carries. Matches no release. */
    private static final String DEV_TAG = "dev";

    private static final String RELEASES_URL =
        "https://api.github.com/repos/romanimpair-jpg/LoogriGram-Android/releases/latest";

    /** How long to leave it before asking again, when nothing was found. */
    private static final long CHECK_INTERVAL = 24 * 60 * 60 * 1000L;

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
     * Called on startup. Checks at most once a day unless forced, and reports
     * a finished download that has not been installed yet.
     */
    public void checkForUpdate(boolean force) {
        if (!updatesEnabled() || state == STATE_CHECKING || state == STATE_DOWNLOADING || state == STATE_READY) {
            return;
        }
        final long last = getPrefs().getLong("lastCheckTime", 0);
        if (!force && Math.abs(System.currentTimeMillis() - last) < CHECK_INTERVAL) {
            return;
        }
        setState(STATE_CHECKING);
        Utilities.globalQueue.postRunnable(this::checkInternal);
    }

    private void checkInternal() {
        String tag = null;
        String url = null;
        long size = 0;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "LoogriGram");
            if (connection.getResponseCode() == 200) {
                final JSONObject release = new JSONObject(readAll(connection.getInputStream()));
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
        AndroidUtilities.runOnUIThread(() -> {
            getPrefs().edit().putLong("lastCheckTime", System.currentTimeMillis()).apply();
            if (foundTag != null && foundUrl != null && isNewer(foundTag)) {
                availableTag = foundTag;
                availableUrl = foundUrl;
                availableSize = foundSize;
                getPrefs().edit()
                    .putString("availableTag", availableTag)
                    .putString("availableUrl", availableUrl)
                    .putLong("availableSize", availableSize)
                    .apply();
                setState(STATE_AVAILABLE);
            } else {
                setState(STATE_NONE);
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
     * Hands the downloaded APK to the system installer. Android always asks the
     * user, and needs this app to be allowed to install unknown apps, so this
     * opens a dialog rather than installing anything by itself.
     */
    public void install(Activity activity) {
        if (state != STATE_READY || readyPath == null || activity == null) {
            return;
        }
        try {
            final File file = new File(readyPath);
            if (!file.exists()) {
                forgetDownload();
                setState(STATE_AVAILABLE);
                return;
            }
            final Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Forgets a finished download - after installing it, or when it is stale. */
    public void forgetDownload() {
        readyPath = null;
        getPrefs().edit().remove("readyTag").remove("readyPath").apply();
    }
}
