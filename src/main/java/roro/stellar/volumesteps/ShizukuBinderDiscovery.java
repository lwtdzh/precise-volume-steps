package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.UserManager;
import rikka.shizuku.Shizuku;

/** Recovers a missed server delivery without opening either application's UI. */
final class ShizukuBinderDiscovery {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean closed;

    ShizukuBinderDiscovery(Context context) {
        this.context = context;
    }

    void inspect() {
        if (closed || !VolumeStepSettings.isShizukuEnabled(context)
                || Shizuku.pingBinder()
                || !context.getSystemService(UserManager.class).isUserUnlocked()) return;
        try {
            String manager = "moe.shizuku.privileged.api";
            int managerUid = context.getPackageManager().getApplicationInfo(manager, 0).uid;
            // Official REQUEST_BINDER receiver protocol, also used by rish.
            // The manager's provider is restricted to privileged callers.
            Binder callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply,
                                                       int flags) {
                    if (code != 1 || Binder.getCallingUid() != managerUid) return false;
                    IBinder found = data.readStrongBinder();
                    handler.post(() -> accept(found));
                    return true;
                }
            };
            Bundle data = new Bundle();
            data.putBinder("binder", callback);
            context.sendBroadcast(new Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                    .setComponent(new ComponentName(manager,
                            "moe.shizuku.manager.receiver.ShizukuReceiver"))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra("data", data));
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Manager missing or unavailable; the next check retries.
        }
    }

    private void accept(IBinder found) {
        if (!closed && VolumeStepSettings.isShizukuEnabled(context)
                && !Shizuku.pingBinder() && found != null && found.pingBinder()) {
            Shizuku.onBinderReceived(found, context.getPackageName());
        }
    }

    void close() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
    }
}
