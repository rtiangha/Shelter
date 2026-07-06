package net.typeblog.shelter.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import net.typeblog.shelter.R;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.receivers.ThawNotificationReceiver;
import net.typeblog.shelter.ui.ThawPanelActivity;

import java.util.ArrayList;
import java.util.List;

// Tracks the set of apps currently thawed (unfrozen) through Shelter and keeps a single
// notification in sync with it. The notification is the user's window onto the thaw list:
// tapping it opens ThawPanelActivity, where swiping an app out freezes it.
//
// Everything here runs in the WORK PROFILE, because only the profile owner can hide/unhide
// apps, and the thaw list is stored in work-profile prefs. Every freeze/unfreeze code path
// calls onThawed()/onFrozen() so the list -- and thus the notification -- stays exact.
public class ThawManager {
    private static final String CHANNEL_ID = "ShelterThaw";
    private static final int NOTIFICATION_ID = 0x54481; // "TH"

    private ThawManager() {}

    public static synchronized void onThawed(Context context, String packageName) {
        LocalStorageManager.getInstance().appendStringListIfAbsent(
                LocalStorageManager.PREF_THAWED_LIST_WORK_PROFILE, packageName);
        refreshNotification(context);
    }

    public static synchronized void onFrozen(Context context, String packageName) {
        LocalStorageManager.getInstance().removeFromStringList(
                LocalStorageManager.PREF_THAWED_LIST_WORK_PROFILE, packageName);
        refreshNotification(context);
    }

    // The authoritative thaw list: the stored packages minus any that have since been
    // uninstalled or frozen out-of-band. Prunes the stored list in place when it finds
    // stale entries, so callers (and the notification) always see reality.
    public static synchronized List<String> getThawedApps(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        if (!dpm.isProfileOwnerApp(context.getPackageName())) {
            // Not the work profile -- there is nothing we can freeze, so there is no list.
            return new ArrayList<>();
        }

        ComponentName admin = new ComponentName(context, ShelterDeviceAdminReceiver.class);
        PackageManager pm = context.getPackageManager();
        List<String> result = new ArrayList<>();
        boolean changed = false;

        for (String pkg : LocalStorageManager.getInstance()
                .getStringList(LocalStorageManager.PREF_THAWED_LIST_WORK_PROFILE)) {
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }

            boolean stale;
            try {
                // MATCH_UNINSTALLED_PACKAGES so a merely-hidden app still resolves here;
                // a truly removed one throws and is pruned.
                pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                // Frozen behind our back (e.g. some path we do not hook) -> drop it.
                stale = dpm.isApplicationHidden(admin, pkg);
            } catch (PackageManager.NameNotFoundException e) {
                stale = true;
            }

            if (stale) {
                changed = true;
            } else {
                result.add(pkg);
            }
        }

        if (changed) {
            LocalStorageManager.getInstance().setStringList(
                    LocalStorageManager.PREF_THAWED_LIST_WORK_PROFILE,
                    result.toArray(new String[]{}));
        }

        return result;
    }

    // Posts, updates, or cancels the notification to match the current thaw list.
    // Safe to call from any work-profile entry point (hooks, alarm repost, boot).
    public static synchronized void refreshNotification(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        List<String> apps = getThawedApps(context);

        if (apps.isEmpty()) {
            nm.cancel(NOTIFICATION_ID);
            return;
        }

        nm.notify(NOTIFICATION_ID, buildNotification(context, nm, apps));
    }

    private static Notification buildNotification(Context context, NotificationManager nm, List<String> apps) {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.thaw_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            chan.enableVibration(false);
            chan.enableLights(false);
            nm.createNotificationChannel(chan);
        }

        PackageManager pm = context.getPackageManager();
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (String pkg : apps) {
            style.addLine(loadLabel(pm, pkg));
        }

        // Tapping the notification opens the panel.
        Intent panelIntent = new Intent(context, ThawPanelActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, panelIntent, PendingIntent.FLAG_IMMUTABLE);

        // Being swiped away schedules a repost (if anything is still thawed).
        Intent dismissIntent = new Intent(context, ThawNotificationReceiver.class)
                .setAction(ThawNotificationReceiver.ACTION_DISMISSED);
        PendingIntent deleteIntent = PendingIntent.getBroadcast(
                context, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getResources().getQuantityString(
                        R.plurals.thaw_notification_title, apps.size(), apps.size()))
                .setContentText(context.getString(R.string.thaw_notification_text))
                .setStyle(style)
                .setSmallIcon(R.drawable.ic_notification_white_24dp)
                // Deliberately NOT ongoing: the user must be able to clear it, and the
                // delete intent below reposts it after a delay. An ongoing notification
                // would be skipped by "Clear all" and never fire the delete intent.
                .setContentIntent(contentIntent)
                .setDeleteIntent(deleteIntent)
                .build();
    }

    private static String loadLabel(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}