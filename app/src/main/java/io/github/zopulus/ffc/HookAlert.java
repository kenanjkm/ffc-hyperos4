package io.github.zopulus.ffc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import io.github.zopulus.ffc.util.HookStatus;
import io.github.zopulus.ffc.util.RomInfo;

/**
 * One notification after boot when a hook group that belongs to this ROM could not be installed
 * (typically after a system update renamed a class). Posted from the provider call that
 * system_server makes once per boot; nothing is scheduled.
 */
final class HookAlert {
    private HookAlert() {}
    private static final String CHANNEL = "hook_status";
    private static final int ID = 1;

    static void update(Context context, HookStatus.Snapshot snapshot) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            List<String> failed = snapshot.failedApplicable(RomInfo.isHyperOs());
            if (failed.isEmpty()) {
                manager.cancel(ID);
                return;
            }
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    context.getString(R.string.hook_status_title), NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent intent = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String text = String.join(", ", failed);
            manager.notify(ID, new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_troubleshoot)
                    .setContentTitle(context.getString(R.string.hook_alert_title, failed.size()))
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(
                            context.getString(R.string.hook_alert_text, text)))
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build());
        } catch (Throwable error) {
            android.util.Log.w("fcmfix", "Hook alert unavailable", error);
        }
    }
}
