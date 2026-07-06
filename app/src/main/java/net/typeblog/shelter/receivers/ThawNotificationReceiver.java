package net.typeblog.shelter.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.typeblog.shelter.util.ThawManager;

// Keeps the thaw notification alive across user dismissal and reboots.
//
// - ACTION_DISMISSED fires when the user swipes the notification away (its deleteIntent).
//   We schedule a repost a short while later, so the "window onto the thaw list" comes back
//   as long as anything is still thawed. Swiping the notification never freezes anything;
//   only swiping inside the panel does.
// - ACTION_REPOST is the alarm firing: just re-sync the notification with the list.
// - BOOT_COMPLETED reposts after a reboot, since hidden/unhidden state (and thus the thaw
//   list) survives reboots but posted notifications do not.
public class ThawNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_DISMISSED = "net.typeblog.shelter.action.THAW_NOTIFICATION_DISMISSED";
    public static final String ACTION_REPOST = "net.typeblog.shelter.action.THAW_NOTIFICATION_REPOST";

    private static final long REPOST_DELAY_MS = 30 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_DISMISSED.equals(action)) {
            scheduleRepost(context);
        } else {
            // ACTION_REPOST or BOOT_COMPLETED: re-sync (posts only if the list is non-empty).
            ThawManager.refreshNotification(context);
        }
    }

    private void scheduleRepost(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        Intent repost = new Intent(context, ThawNotificationReceiver.class)
                .setAction(ACTION_REPOST);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, repost, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.set(AlarmManager.RTC, System.currentTimeMillis() + REPOST_DELAY_MS, pi);
    }
}