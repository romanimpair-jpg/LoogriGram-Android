package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import androidx.core.content.IntentCompat;

/**
 * LoogriGram: where the updater's install session reports back. See
 * LoogriGramUpdate.install.
 *
 * Only two outcomes reach it. STATUS_PENDING_USER_ACTION means Android wants
 * the user first - this app is not yet allowed to install unknown apps, or the
 * phone is older than Android 12 - so it opens the confirmation the system
 * handed over. Anything else but success is a failure or a "Cancel" there, and
 * the update goes back to being offered. A success is never heard: installing
 * replaced this process.
 */
public class LoogriGramInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            final Intent confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                    return;
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            LoogriGramUpdate.getInstance().onInstallFailed();
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            FileLog.e("LoogriGramUpdate: install ended with status " + status + ": " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            LoogriGramUpdate.getInstance().onInstallFailed();
        }
    }
}
