package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;

import java.util.List;
import java.util.Set;

// Thin wrapper around DevicePolicyManager that caches Shelter's admin component
// and funnels every policy call through one place. Historically the pattern
//   getSystemService(DevicePolicyManager.class) + new ComponentName(ctx, ShelterDeviceAdminReceiver.class)
// was duplicated across half a dozen files; centralizing it here means cross-cutting
// behavior (e.g. checking setApplicationHidden results, preserving app-ops) has a
// single home instead of being scattered.
public class DevicePolicies {
    private final DevicePolicyManager mManager;
    private final ComponentName mAdmin;

    public DevicePolicies(Context context) {
        mManager = context.getSystemService(DevicePolicyManager.class);
        mAdmin = new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class);
    }

    // Escape hatches for the long tail of DevicePolicyManager calls that are not
    // worth a dedicated wrapper method. Prefer the named methods below when one exists.
    public DevicePolicyManager getManager() {
        return mManager;
    }

    public ComponentName getAdminComponent() {
        return mAdmin;
    }

    public boolean isProfileOwner() {
        return mManager.isProfileOwnerApp(mAdmin.getPackageName());
    }

    // Single chokepoint for hiding/unhiding (a.k.a. freezing/unfreezing) apps.
    public boolean setApplicationHidden(String pkg, boolean hidden) {
        return mManager.setApplicationHidden(mAdmin, pkg, hidden);
    }

    public boolean isApplicationHidden(String pkg) {
        return mManager.isApplicationHidden(mAdmin, pkg);
    }

    public void enableSystemApp(String pkg) {
        mManager.enableSystemApp(mAdmin, pkg);
    }

    public void addUserRestriction(String key) {
        mManager.addUserRestriction(mAdmin, key);
    }

    public void clearUserRestriction(String key) {
        mManager.clearUserRestriction(mAdmin, key);
    }

    public void addCrossProfileIntentFilter(IntentFilter filter, int flags) {
        mManager.addCrossProfileIntentFilter(mAdmin, filter, flags);
    }

    public void clearCrossProfileIntentFilters() {
        mManager.clearCrossProfileIntentFilters(mAdmin);
    }

    public void setCrossProfileContactsSearchDisabled(boolean disabled) {
        mManager.setCrossProfileContactsSearchDisabled(mAdmin, disabled);
    }

    public void setProfileEnabled() {
        mManager.setProfileEnabled(mAdmin);
    }

    // Not admin-scoped in the platform API, exposed here so callers still go through
    // one policy surface.
    public boolean isProvisioningAllowed(String action) {
        return mManager.isProvisioningAllowed(action);
    }

    public List<String> getCrossProfileWidgetProviders() {
        return mManager.getCrossProfileWidgetProviders(mAdmin);
    }

    public boolean addCrossProfileWidgetProvider(String pkg) {
        return mManager.addCrossProfileWidgetProvider(mAdmin, pkg);
    }

    public boolean removeCrossProfileWidgetProvider(String pkg) {
        return mManager.removeCrossProfileWidgetProvider(mAdmin, pkg);
    }

    public Set<String> getCrossProfilePackages() {
        return mManager.getCrossProfilePackages(mAdmin);
    }

    public void setCrossProfilePackages(Set<String> packages) {
        mManager.setCrossProfilePackages(mAdmin, packages);
    }
}
