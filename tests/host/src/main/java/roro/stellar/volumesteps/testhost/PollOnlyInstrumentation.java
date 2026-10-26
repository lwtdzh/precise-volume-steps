package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Runs in the target process, disconnecting its broadcast receiver for a test. */
public final class PollOnlyInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            Class<?> keeper = target.getClassLoader().loadClass(
                    target.getPackageName() + ".VolumeKeeperService");
            Method getController = keeper.getDeclaredMethod("controller");
            getController.setAccessible(true);
            Object controller = null;
            for (int i = 0; i < 100 && controller == null; i++) {
                controller = getController.invoke(null);
                if (controller == null) Thread.sleep(100);
            }
            if (controller == null) throw new IllegalStateException("Foreground monitor not started");
            Object monitor = controller;
            Field receiver = monitor.getClass().getDeclaredField("volumeReceiver");
            Field registered = monitor.getClass().getDeclaredField("receiverRegistered");
            Field owner = monitor.getClass().getDeclaredField("context");
            receiver.setAccessible(true);
            registered.setAccessible(true);
            owner.setAccessible(true);
            runOnMainSync(() -> {
                try {
                    ((Context) owner.get(monitor)).unregisterReceiver(
                            (BroadcastReceiver) receiver.get(monitor));
                    registered.setBoolean(monitor, false);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            Bundle ready = new Bundle();
            ready.putString("mode", "poll-only");
            sendStatus(0, ready);
            // The external runner presses real device keys while this process runs.
            Thread.sleep(20000);
            Method diagnostics = monitor.getClass().getDeclaredMethod("diagnostics");
            diagnostics.setAccessible(true);
            runOnMainSync(() -> {
                try {
                    result.putString("diagnostics", (String) diagnostics.invoke(monitor));
                } catch (Exception exception) {
                    result.putString("error", exception.toString());
                }
            });
            finish(result.containsKey("error") ? 1 : 0, result);
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            finish(1, result);
        }
    }
}
