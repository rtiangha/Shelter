package net.typeblog.shelter.receivers;

import android.app.admin.DeviceAdminReceiver;

// Provisioning finalization is handled by FinalizeActivity via the
// ACTION_PROVISIONING_SUCCESSFUL activity intent, so this receiver only needs to
// exist as the app's device admin component.
public class ShelterDeviceAdminReceiver extends DeviceAdminReceiver {
}
