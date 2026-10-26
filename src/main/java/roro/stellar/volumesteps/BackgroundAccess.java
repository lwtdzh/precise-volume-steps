package roro.stellar.volumesteps;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

final class BackgroundAccess {
    private static final ComponentName[] AUTO_START_COMPONENTS = {
            new ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
            ),
            new ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            new ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            ),
            new ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"
            ),
            new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            new ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            new ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
    };

    private BackgroundAccess() {}

    static boolean hasVendorAutoStartSettings(Context context) {
        for (ComponentName component : AUTO_START_COMPONENTS) {
            if (isAvailable(context, component)) {
                return true;
            }
            try {
                // A renamed vendor activity does not establish that startup
                // restrictions are absent. Package visibility is declared.
                context.getPackageManager().getApplicationInfo(component.getPackageName(), 0);
                return true;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        return false;
    }

    static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    @SuppressLint("BatteryLife")
    static void requestBatteryOptimizationExemption(Activity activity, int requestCode) {
        Intent directRequest = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + activity.getPackageName())
        );
        try {
            activity.startActivityForResult(directRequest, requestCode);
        } catch (RuntimeException exception) {
            activity.startActivityForResult(new Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ), requestCode);
        }
    }

    static void openAutoStartSettings(Activity activity) {
        openAutoStartSettings(activity, activity.getPackageName());
    }

    static void openAutoStartSettings(Activity activity, String packageName) {
        for (ComponentName component : AUTO_START_COMPONENTS) {
            Intent intent = new Intent("secure.intent.action.softPermissionDetail")
                    .setComponent(component)
                    .putExtra("packagename", packageName)
                    .putExtra("packageName", packageName);
            if (!isAvailable(activity, component)) {
                continue;
            }
            try {
                activity.startActivity(intent);
                return;
            } catch (RuntimeException ignored) {
                // Try the next vendor entry.
            }
        }

        Intent details = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)
        );
        activity.startActivity(details);
    }

    static void openVendorBackgroundSettings(Activity activity) {
        ComponentName[] components = {
                new ComponentName("com.vivo.abe",
                        "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                new ComponentName("com.iqoo.powersaving",
                        "com.iqoo.powersaving.PowerSavingManagerActivity")
        };
        for (ComponentName component : components) {
            Intent intent = new Intent().setComponent(component)
                    .putExtra("packagename", activity.getPackageName())
                    .putExtra("packageName", activity.getPackageName());
            if (launchAvailable(activity, intent)) {
                return;
            }
        }
        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getPackageName())));
    }

    private static boolean launchAvailable(Activity activity, Intent intent) {
        if (!isAvailable(activity, intent.getComponent())) {
            return false;
        }
        try {
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isAvailable(Context context, ComponentName component) {
        android.content.pm.ResolveInfo info = context.getPackageManager().resolveActivity(
                new Intent().setComponent(component), 0);
        return info != null && info.activityInfo != null && info.activityInfo.exported;
    }
}
