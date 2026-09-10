/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.telegram.ui.LaunchActivity;

// LoogriGram: upstream can afford to leave this as a plain background service,
// because on a phone with Play Services the actual wake-up arrives over FCM and
// this only helps. Here there is no FCM: notifications exist only while the
// MTProto push connection is alive, and that connection only lives as long as
// the process does. So the service is promoted to a real foreground service.
//
// Android 8+ requires a permanently visible notification for that, which is an
// OS requirement and not a choice. It is posted on its own IMPORTANCE_MIN
// channel so it is silent and sits at the bottom of the shade; it can be hidden
// entirely in the system notification settings, at the cost of Android showing
// a battery-usage nag instead.
public class NotificationsService extends Service {

    // Deliberately distinctive: ids 1, 4, 5, 6, 33, 201, 202 and 301 are
    // already taken by push, video encoding, the music player, live location,
    // imports and calls.
    private static final int NOTIFICATION_ID = 7331;
    private static final String CHANNEL_ID = "loogrigram_connection";
    private static final long WATCHDOG_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        scheduleWatchdog();
        return START_STICKY;
    }

    private void startInForeground() {
        try {
            createChannel();

            final Intent open = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            open.setAction(Intent.ACTION_MAIN);
            open.addCategory(Intent.CATEGORY_LAUNCHER);
            final PendingIntent contentIntent = PendingIntent.getActivity(
                    ApplicationLoader.applicationContext, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            final NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(ApplicationLoader.applicationContext, CHANNEL_ID)
                            .setSmallIcon(R.drawable.notification)
                            .setContentTitle(LocaleController.getString(R.string.AppName))
                            .setContentText(LocaleController.getString(R.string.NotificationsServiceConnection))
                            .setContentIntent(contentIntent)
                            .setPriority(NotificationCompat.PRIORITY_MIN)
                            .setShowWhen(false)
                            .setSilent(true)
                            .setOngoing(true)
                            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

            if (Build.VERSION.SDK_INT >= 34) {
                // specialUse, not dataSync: Android 15 caps a dataSync
                // foreground service at six hours a day, which for a service
                // whose entire job is to stay connected means notifications
                // stop arriving partway through every day. specialUse has no
                // such cap. The manifest declares both so that pre-34 devices,
                // which have never heard of specialUse, still get a type they
                // understand.
                startForeground(NOTIFICATION_ID, builder.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, builder.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, builder.build());
            }
        } catch (Throwable e) {
            // Most likely ForegroundServiceStartNotAllowedException: on API 31+
            // a foreground service cannot be started while the app is in the
            // background. Losing the promotion is survivable - START_STICKY
            // means the system brings the service back itself, and a
            // system-initiated restart is allowed to go foreground - so this
            // must not take the process down with it.
            FileLog.e(e);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        final NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                LocaleController.getString(R.string.NotificationsService),
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        manager.createNotificationChannel(channel);
    }

    // Best-effort backstop, not the primary mechanism. START_STICKY and the
    // boot receiver do the real work; this only covers the case where the
    // process was killed in a way that left neither running. Deliberately
    // inexact: an exact alarm would mean asking for SCHEDULE_EXACT_ALARM, a
    // permission the user has to see and approve, for a guarantee Android
    // does not actually give a backgrounded app anyway.
    private void scheduleWatchdog() {
        try {
            final AlarmManager alarmManager =
                    (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                return;
            }
            final Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            final PendingIntent pending = PendingIntent.getBroadcast(
                    ApplicationLoader.applicationContext, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL,
                    WATCHDOG_INTERVAL, pending);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }
}
