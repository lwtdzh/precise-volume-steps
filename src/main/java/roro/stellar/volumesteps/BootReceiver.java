package roro.stellar.volumesteps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            VolumeStepSettings.preferences(context).edit()
                    .putString("last_boot_action", action)
                    .putLong("last_boot_time", System.currentTimeMillis()).apply();
            VolumeKeeperService.start(context);
        }
    }
}
