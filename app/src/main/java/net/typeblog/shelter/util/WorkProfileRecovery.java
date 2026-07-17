package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import net.typeblog.shelter.ui.DummyActivity;

import java.util.List;

/**
 * Detect and recover from failed / incomplete work-profile provisioning.
 * <p>
 * ManagedProvisioning (the system "MDM"/work-profile setup UI) can fail on any OEM:
 * crash, cancel after the managed user was created, policy errors, missing OEM hooks, etc.
 * Shelter cannot see the OEM's internal exception. What we <em>can</em> detect generically is
 * the aftermath: an extra profile exists that is not yet a usable Shelter work profile.
 * <p>
 * Shelter cannot {@code removeUser} from the main profile. When Shelter is still profile owner
 * inside the incomplete profile, we can finalize ({@code setProfileEnabled}) or
 * {@code wipeData(0)} via cross-profile intents; otherwise we open Settings.
 */
public final class WorkProfileRecovery {
    private static final String TAG = "WorkProfileRecovery";

    /** How setup should present a failure to the user. */
    public enum FailureKind {
        /**
         * A managed/other profile exists but is not usable as Shelter's work profile —
         * typical leftover from a failed or aborted provisioning run on any OEM.
         */
        INCOMPLETE_WORK_PROFILE,
        /** Setup failed without leaving a detectable incomplete profile (e.g. clean cancel). */
        SETUP_FAILED,
    }

    public enum Outcome {
        HEALTHY,
        HEAL_LAUNCHED,
        HEAL_UNREACHABLE,
        WIPE_LAUNCHED,
        OPENED_SETTINGS,
        NOTHING_TO_DO,
    }

    private WorkProfileRecovery() {}

    public static int profileCount(Context context) {
        UserManager um = context.getSystemService(UserManager.class);
        if (um == null) return 1;
        List<UserHandle> profiles = um.getUserProfiles();
        return profiles == null ? 1 : profiles.size();
    }

    public static boolean hasOtherProfiles(Context context) {
        return profileCount(context) > 1;
    }

    /**
     * Generic signal that provisioning did not finish cleanly: another profile is present,
     * but cross-profile Shelter handshakes do not work yet.
     */
    public static boolean isIncompleteWorkProfile(Context context) {
        return hasOtherProfiles(context) && !Utility.isWorkProfileAvailable(context);
    }

    /**
     * Classify the outcome of a provisioning attempt using only observable profile state.
     *
     * @param priorProfileCount {@link #profileCount} taken before launching provisioning
     * @param provisionReturnedOk whether the system provisioning activity returned RESULT_OK
     */
    public static FailureKind classifyFailure(Context context, int priorProfileCount,
                                              boolean provisionReturnedOk) {
        if (isIncompleteWorkProfile(context)) {
            return FailureKind.INCOMPLETE_WORK_PROFILE;
        }
        // Profile appeared during the attempt but is not handshake-ready yet.
        if (profileCount(context) > priorProfileCount) {
            return FailureKind.INCOMPLETE_WORK_PROFILE;
        }
        // Platform refuses another managed profile while something unusable remains.
        DevicePolicies policies = new DevicePolicies(context);
        if (!policies.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                && hasOtherProfiles(context)) {
            return FailureKind.INCOMPLETE_WORK_PROFILE;
        }
        if (!provisionReturnedOk) {
            return FailureKind.SETUP_FAILED;
        }
        // RESULT_OK but profile still not available and no extra user — treat as incomplete
        // setup that needs user action (legacy notification path uses a different fragment).
        return FailureKind.SETUP_FAILED;
    }

    public static Outcome tryHeal(Context context) {
        if (Utility.isWorkProfileAvailable(context)) {
            return Outcome.HEALTHY;
        }
        if (!hasOtherProfiles(context)) {
            return Outcome.NOTHING_TO_DO;
        }
        if (launchInProfile(context, DummyActivity.FINALIZE_PROVISION)) {
            return Outcome.HEAL_LAUNCHED;
        }
        return Outcome.HEAL_UNREACHABLE;
    }

    public static Outcome tryWipeOrOpenSettings(Context context) {
        if (!hasOtherProfiles(context)) {
            return Outcome.NOTHING_TO_DO;
        }
        if (launchInProfile(context, DummyActivity.WIPE_ORPHAN_PROFILE)) {
            return Outcome.WIPE_LAUNCHED;
        }
        openWorkProfileSettings(context);
        return Outcome.OPENED_SETTINGS;
    }

    public static void openWorkProfileSettings(Context context) {
        Intent[] candidates = new Intent[] {
                new Intent(Settings.ACTION_SYNC_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
        };
        for (Intent intent : candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                try {
                    context.startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to open " + intent.getAction(), e);
                }
            }
        }
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.fromParts("package", context.getPackageName(), null));
        details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(details);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open app details", e);
        }
    }

    private static boolean launchInProfile(Context context, String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            Utility.transferIntentToProfileUnsigned(context, intent);
            context.startActivity(intent);
            return true;
        } catch (IllegalStateException e) {
            Log.i(TAG, "Cannot reach work profile for action " + action + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Failed launching " + action + " in work profile", e);
            return false;
        }
    }
}
