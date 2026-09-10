/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AppStartReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedConfig.loadConfig();
                if (SharedConfig.passcodeHash.length() > 0) {
                    SharedConfig.appLocked = true;
                    SharedConfig.saveConfig();
                }
                ApplicationLoader.startPushService();
            });
        } else if ("org.telegram.start".equals(intent.getAction())) {
            // LoogriGram: this receiver is registered for org.telegram.start in
            // the manifest, but upstream's guard above only ever acted on
            // ACTION_BOOT_COMPLETED - so NotificationsService.onDestroy's
            // restart broadcast, and now the watchdog alarm, both landed here
            // and did nothing. Harmless upstream, where FCM delivers the
            // wake-up regardless; here it is the restart path.
            AndroidUtilities.runOnUIThread(ApplicationLoader::startPushService);
        }
    }
}
