package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;

// LoogriGram: Appearance offered alternative launcher icons - Vintage and
// Aqua, and Premium's three before them - each an activity-alias switched on
// in place of DefaultIcon. They are gone: there is one icon, as on desktop.
public class LauncherIconController {

    // LoogriGram: choosing an alternative icon disabled DefaultIcon on the
    // device, and that state outlives the update that removed the aliases,
    // which would leave the app with no launcher entry at all. Runs at process
    // start (an update starts the process through MY_PACKAGE_REPLACED) and
    // puts DefaultIcon back to its manifest state.
    public static void tryFixLauncherIconIfNeeded() {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        ComponentName defaultIcon = new ComponentName(ctx.getPackageName(), "org.telegram.messenger.DefaultIcon");
        if (pm.getComponentEnabledSetting(defaultIcon) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(defaultIcon, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        }
    }
}
