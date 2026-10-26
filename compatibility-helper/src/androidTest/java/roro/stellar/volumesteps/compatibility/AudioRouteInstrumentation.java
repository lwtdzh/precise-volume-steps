package roro.stellar.volumesteps.compatibility;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Test-only coarse volume writer that runs in the helper process, reproducing the
 * audio-service outcome of a hardware volume key routed directly by firmware while
 * the display is off (native jump, no key event to accessibility).
 *
 * One-shot: {@code am instrument -e value 50}.
 *
 * Daemon: {@code am instrument -e mode daemon} registers an exported receiver for
 * action {@link #ACTION} with int extras {@code stream}, {@code value}, {@code token}
 * and logs {@code PVSRoute RESULT token=.. applied=.. after=..}. The process then
 * stays alive so repeated writes do not recreate the helper process.
 */
public final class AudioRouteInstrumentation extends Instrumentation {
    public static final String ACTION = "pvs.tests.ROUTE_VOLUME";
    private static final String TAG = "PVSRoute";
    private Bundle args;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        if ("daemon".equals(args.getString("mode"))) {
            startDaemon();
            return;
        }
        Bundle result = new Bundle();
        try {
            AudioManager audio = getTargetContext().getSystemService(AudioManager.class);
            int stream = Integer.parseInt(arg("stream", "3"));
            int value = Integer.parseInt(args.getString("value"));
            int[] r = write(audio, stream, value);
            result.putString("before", String.valueOf(r[0]));
            result.putString("requested", String.valueOf(r[1]));
            result.putString("after", String.valueOf(r[2]));
            result.putString("applied", String.valueOf(r[2] == r[1]));
        } catch (Exception e) {
            result.putString("error", e.toString());
        }
        finish(result.containsKey("error") ? 1 : 0, result);
    }

    private static int[] write(AudioManager audio, int stream, int value) {
        int before = audio.getStreamVolume(stream);
        int safe = Math.max(audio.getStreamMinVolume(stream),
                Math.min(audio.getStreamMaxVolume(stream), value));
        audio.setStreamVolume(stream, safe, 0);
        try { Thread.sleep(120); } catch (InterruptedException ignored) { }
        return new int[]{before, safe, audio.getStreamVolume(stream)};
    }

    private void startDaemon() {
        Context context = getTargetContext();
        AudioManager audio = context.getSystemService(AudioManager.class);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                int stream = intent.getIntExtra("stream", 3);
                int value = intent.getIntExtra("value", Integer.MIN_VALUE);
                String token = intent.getStringExtra("token");
                int[] r = {-1, -1, -1};
                Exception failure = null;
                try {
                    r = write(audio, stream, value);
                } catch (Exception e) {
                    failure = e;
                }
                Log.i(TAG, "RESULT token=" + token + " before=" + r[0]
                        + " requested=" + r[1] + " after=" + r[2]
                        + " applied=" + (r[2] == r[1])
                        + (failure == null ? "" : " error=" + failure));
            }
        };
        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        Log.i(TAG, "READY");
        // Keep the helper instrumentation process alive; the process is stopped explicitly
        // after the verification finishes.
        synchronized (this) {
            while (true) {
                try {
                    wait(60_000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private String arg(String key, String def) {
        String v = args.getString(key);
        return v == null ? def : v;
    }
}
