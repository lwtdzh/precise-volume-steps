package roro.stellar.volumesteps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.app.KeyguardManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;

/** Owned by the foreground service; only confirmed key events authorize writes. */
final class VolumeAdjustmentController {
    // Only streams accepted by the public volume-range API. Internal aliases
    // such as SYSTEM_ENFORCED (7) also appear in system volume broadcasts.
    private static final int[] STREAMS = {3, 2, 4, 5, 1, 0};
    private static final long DETECT_MS = 35;
    private static final long KEY_TIMEOUT_MS = 700;
    private static final long WRITE_TIMEOUT_MS = 1000;
    private static final long POLL_MS = 250;

    private final Context context;
    private final AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VolumeOverlayController overlay;
    private final ShizukuVolumeBridge shizuku;
    private final CompatibilityVolumeBridge compatibility;
    private final int[] observed = new int[12];
    private final int[] previousObserved = new int[12];
    private final long[] changedAt = new long[12];
    private final PowerManager.WakeLock keyWakeLock;
    private final VolumeStepHistory[] histories = new VolumeStepHistory[12];
    private VolumeStepHistory.Plan plannedSteps;
    private Gesture plannedGesture;
    private final ArrayList<String> events = new ArrayList<>();
    private Gesture gesture;
    private Correction pendingCorrection;
    private final Runnable detectKey = this::detectKeyAdjustment;
    private final Runnable confirmWrite = this::confirmCorrection;
    private long lastKeyTime;
    private long lastDownTime = -1;
    private int lastDownCode;
    private String lastKeyAction = "none";
    private long lastVolumeTime;
    private String lastSource = "none";
    private boolean closed;
    private boolean receiverRegistered;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
            if (stream < 0 || stream >= observed.length) {
                return;
            }
            observe(stream, "broadcast");
        }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed) {
                return;
            }
            if (VolumeStepSettings.isMediaOnly(context)) {
                observe(AudioManager.STREAM_MUSIC, "poll");
            } else {
                for (int stream : STREAMS) {
                    observe(stream, "poll");
                }
            }
            handler.postDelayed(this, POLL_MS);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (preferences, key) -> {
                if ("steps".equals(key) || "media_only".equals(key)
                        || "shizuku_enabled".equals(key)) {
                    handler.post(() -> {
                        cancelPending();
                        captureBaseline();
                        connectShizuku();
                        VolumeKeeperService.refreshNotification();
                    });
                }
            };

    VolumeAdjustmentController(Context context) {
        this.context = context;
        for (int i = 0; i < histories.length; i++) histories[i] = new VolumeStepHistory();
        audio = context.getSystemService(AudioManager.class);
        keyWakeLock = context.getSystemService(PowerManager.class).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, context.getPackageName() + ":volume-key");
        keyWakeLock.setReferenceCounted(false);
        overlay = new VolumeOverlayController(context);
        shizuku = new ShizukuVolumeBridge(context, VolumeKeeperService::refreshNotification);
        compatibility = new CompatibilityVolumeBridge(context, VolumeKeeperService::refreshNotification);
        captureBaseline();
        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // OEM audio broadcasts may originate outside system_server's UID.
                context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(volumeReceiver, filter);
            }
            receiverRegistered = true;
        } catch (RuntimeException exception) {
            record("broadcast registration failed; polling remains active: " + exception);
        }
        VolumeStepSettings.preferences(context).registerOnSharedPreferenceChangeListener(settingsListener);
        handler.postDelayed(poll, POLL_MS);
        record("monitor started");
    }

    void onKey(KeyEvent event) {
        if (closed || !VolumeStepSettings.isEnabled(context)) {
            return;
        }
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return;
        }
        lastKeyTime = SystemClock.uptimeMillis();
        lastKeyAction = event.getAction() == KeyEvent.ACTION_DOWN ? "down"
                : event.getAction() == KeyEvent.ACTION_UP ? "up" : "other";
        int direction = code == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1;
        if (event.getAction() == KeyEvent.ACTION_UP && event.isCanceled()) {
            cancelPending();
            return;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getDownTime() != lastDownTime || code != lastDownCode) {
                recoverKeyUp(event, direction);
            }
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        lastDownTime = event.getDownTime();
        lastDownCode = code;
        holdKeyWakeLock();
        record("key " + direction + " repeat=" + event.getRepeatCount());
        overlay.holdUpdates();
        shizuku.invalidate();
        compatibility.invalidate();
        if (gesture == null) {
            if (pendingCorrection != null && pendingCorrection.gesture != null) {
                // A newer press supersedes an unconfirmed write. Recompute the
                // complete gesture from its original readback, not an intermediate value.
                gesture = new Gesture(pendingCorrection.gesture.initial.clone());
                gesture.directions.addAll(pendingCorrection.gesture.directions);
                gesture.stream = pendingCorrection.stream;
                gesture.includesPending = true;
            } else {
                gesture = new Gesture(snapshot());
            }
            handler.postDelayed(detectKey, DETECT_MS);
        }
        gesture.directions.add(direction);
        gesture.lastPress = SystemClock.uptimeMillis();
    }

    private void holdKeyWakeLock() {
        try {
            // Keep only this key's detection/acknowledgement alive; never wake the display.
            keyWakeLock.acquire(KEY_TIMEOUT_MS + WRITE_TIMEOUT_MS + 300);
        } catch (RuntimeException exception) {
            record("key wake lock failed: " + exception);
        }
    }

    private void recoverKeyUp(KeyEvent event, int direction) {
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (power.isInteractive() && !keyguard.isKeyguardLocked()) return;
        long now = SystemClock.uptimeMillis();
        if (now - event.getDownTime() > KEY_TIMEOUT_MS || gesture != null) return;
        int[] current = snapshot();
        int[] initial = current.clone();
        boolean changed = false;
        for (int stream : STREAMS) {
            if (!inScope(stream)) continue;
            int before = observed[stream];
            if (changedAt[stream] >= event.getDownTime()
                    && now - changedAt[stream] <= KEY_TIMEOUT_MS) {
                before = previousObserved[stream];
            }
            if (before >= 0 && Integer.signum(current[stream] - before) == direction) {
                initial[stream] = before;
                changed = true;
            }
        }
        if (!changed) {
            record("unmatched key up without recent native change");
            return;
        }
        holdKeyWakeLock();
        overlay.holdUpdates();
        shizuku.invalidate();
        compatibility.invalidate();
        gesture = new Gesture(initial);
        gesture.directions.add(direction);
        gesture.lastPress = now;
        record("locked/off key up recovered direction=" + direction);
        handler.postDelayed(detectKey, DETECT_MS);
    }

    private void detectKeyAdjustment() {
        Gesture active = gesture;
        if (active == null || closed) {
            return;
        }
        int[] after = snapshot();
        for (int stream : STREAMS) {
            if (!inScope(stream)) {
                continue;
            }
            int delta = after[stream] - active.initial[stream];
            // A delayed acknowledgement of our previous write is not a new
            // native key adjustment, even if the next key has already arrived.
            if (pendingCorrection != null && pendingCorrection.stream == stream
                    && after[stream] == pendingCorrection.target) {
                if (!active.includesPending) active.initial[stream] = after[stream];
                continue;
            }
            if (after[stream] >= 0 && active.initial[stream] >= 0 && delta != 0
                    && active.directions.contains(Integer.signum(delta))) {
                active.stream = stream;
                if (active.lastNativeValue != after[stream]) {
                    active.lastNativeValue = after[stream];
                    active.lastNativeChange = SystemClock.uptimeMillis();
                }
                break;
            }
        }
        long quiet = SystemClock.uptimeMillis() - active.lastPress;
        if (active.stream < 0 || quiet < DETECT_MS
                || SystemClock.uptimeMillis() - active.lastNativeChange < DETECT_MS) {
            if (quiet < KEY_TIMEOUT_MS) {
                handler.postDelayed(detectKey, DETECT_MS);
            } else {
                record("key expired without native change");
                gesture = null;
                if (pendingCorrection == null) {
                    overlay.releaseUpdates();
                }
            }
            return;
        }
        gesture = null;
        int stream = active.stream;
        // Start from an observed value. An unacknowledged previous target may
        // have been rejected by the system and must not become the next base.
        plannedSteps = histories[stream].plan(active.initial[stream],
                audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream),
                VolumeStepSettings.steps(context), active.directions);
        plannedGesture = active;
        apply(stream, plannedSteps.target());
    }

    private void observe(int stream, String source) {
        if (closed || !VolumeStepSettings.isEnabled(context) || !inScope(stream)) {
            return;
        }
        try {
            int current = audio.getStreamVolume(stream);
            int previous = observed[stream];
            if (current == previous) {
                return;
            }
            observed[stream] = current;
            previousObserved[stream] = previous;
            changedAt[stream] = SystemClock.uptimeMillis();
            lastVolumeTime = SystemClock.uptimeMillis();
            lastSource = source;
            record(source + " stream=" + stream + " " + previous + " -> " + current);
            if (gesture != null && gesture.stream < 0
                    && current != gesture.initial[stream]
                    && gesture.directions.contains(Integer.signum(current - gesture.initial[stream]))
                    && (pendingCorrection == null || pendingCorrection.target != current
                            || pendingCorrection.stream != stream)) {
                // Remember a native transition even if an immediate opposite key
                // returns to the origin before the next detection tick.
                gesture.stream = stream;
                gesture.lastNativeValue = current;
                gesture.lastNativeChange = SystemClock.uptimeMillis();
            }
            if (stream == AudioManager.STREAM_MUSIC) {
                VolumeKeeperService.refreshNotification();
            }
            // Broadcasts and polling only update what the system reports.
            // Playback, routing, presets and our own writes must not open UI
            // or cause another correction.
            if (gesture == null && pendingCorrection == null) {
                histories[stream].clear();
                overlay.updateActual(stream, current, audio.getStreamMaxVolume(stream));
            }
        } catch (RuntimeException exception) {
            record("volume read failed: " + exception);
        }
    }

    private void apply(int stream, int target) {
        if (closed || !VolumeStepSettings.isEnabled(context)) {
            return;
        }
        try {
            int current = audio.getStreamVolume(stream);
            handler.removeCallbacks(confirmWrite);
            Correction correction = new Correction(stream, target, SystemClock.uptimeMillis());
            correction.steps = plannedSteps;
            correction.gesture = plannedGesture;
            plannedSteps = null;
            plannedGesture = null;
            pendingCorrection = correction;
            if (target != current) {
                if (shizuku.ready()) {
                    record("Shizuku key write stream=" + stream + " target=" + target);
                    shizuku.adjust(stream, target, current, correction.requestedAt + 500,
                            result -> {
                                if (pendingCorrection != correction || closed) {
                                    return;
                                }
                                record("Shizuku result=" + result);
                                if (result < 0) {
                                    pendingCorrection = null;
                                    markUnconfirmed(stream);
                                } else {
                                    confirmCorrection();
                                }
                            });
                    handler.postDelayed(confirmWrite, DETECT_MS);
                    return;
                }
                if (compatibility.ready()) {
                    record("companion key write stream=" + stream + " target=" + target);
                    compatibility.adjust(stream, target, current, correction.requestedAt + 500,
                            result -> {
                                if (pendingCorrection != correction || closed) return;
                                record("companion result=" + result);
                                if (result < 0) {
                                    pendingCorrection = null;
                                    markUnconfirmed(stream);
                                } else {
                                    confirmCorrection();
                                }
                            });
                    handler.postDelayed(confirmWrite, DETECT_MS);
                    return;
                }
                audio.setStreamVolume(stream, target, 0);
            }
            record("key write stream=" + stream + " target=" + target);
            confirmCorrection();
        } catch (RuntimeException exception) {
            pendingCorrection = null;
            markUnconfirmed(stream);
            record("correction failed: " + exception);
        }
    }

    private void confirmCorrection() {
        handler.removeCallbacks(confirmWrite);
        Correction active = pendingCorrection;
        if (active == null || closed) {
            return;
        }
        try {
            int current = audio.getStreamVolume(active.stream);
            if (current == active.target) {
                // Rebase a waiting key before retiring the acknowledgement.
                if (gesture != null && gesture.initial[active.stream] != current
                        && gesture.stream < 0 && !gesture.includesPending) {
                    gesture.initial[active.stream] = current;
                }
                pendingCorrection = null;
                observed[active.stream] = current;
                if (active.steps != null) histories[active.stream].confirm(active.steps);
                record("key confirmed stream=" + active.stream + " value=" + current
                        + " delay=" + (SystemClock.uptimeMillis() - active.requestedAt));
                VolumeKeeperService.refreshNotification();
                showResult(active.stream, current,
                        VolumeOverlayController.CorrectionState.CONFIRMED);
                return;
            }
            if (SystemClock.uptimeMillis() - active.requestedAt >= WRITE_TIMEOUT_MS) {
                pendingCorrection = null;
                record("key write unconfirmed stream=" + active.stream
                        + " target=" + active.target + " value=" + current);
                showResult(active.stream, current,
                        VolumeOverlayController.CorrectionState.UNCONFIRMED);
                return;
            }
            handler.postDelayed(confirmWrite, DETECT_MS);
        } catch (RuntimeException exception) {
            pendingCorrection = null;
            markUnconfirmed(active.stream);
            record("volume acknowledgement failed: " + exception);
        }
    }

    private void markUnconfirmed(int stream) {
        try {
            showResult(stream, audio.getStreamVolume(stream),
                    VolumeOverlayController.CorrectionState.UNCONFIRMED);
        } catch (RuntimeException ignored) {
            // A failed read cannot supply a new actual value to the panel.
            if (gesture == null) {
                overlay.releaseUpdates();
            }
        }
    }

    private void showResult(int stream, int current, VolumeOverlayController.CorrectionState state) {
        // A newer key can already be changing the native volume. Its result
        // will replace this one; keep the previous display until it resolves.
        if (gesture != null) {
            return;
        }
        overlay.releaseUpdates();
        if (context.getSystemService(PowerManager.class).isInteractive()) {
            overlay.show(stream, current, audio.getStreamMaxVolume(stream), state);
            record("key panel stream=" + stream + " value=" + current);
        }
    }

    private boolean inScope(int stream) {
        for (int supported : STREAMS) {
            if (stream == supported) {
                return !VolumeStepSettings.isMediaOnly(context)
                        || stream == AudioManager.STREAM_MUSIC;
            }
        }
        return false;
    }

    private int[] snapshot() {
        int[] values = new int[observed.length];
        Arrays.fill(values, -1);
        for (int stream : STREAMS) {
            try {
                values[stream] = audio.getStreamVolume(stream);
            } catch (RuntimeException ignored) { }
        }
        return values;
    }

    private void captureBaseline() {
        int[] values = snapshot();
        System.arraycopy(values, 0, observed, 0, observed.length);
        System.arraycopy(values, 0, previousObserved, 0, observed.length);
        Arrays.fill(changedAt, 0);
    }

    private void cancelPending() {
        shizuku.invalidate();
        compatibility.invalidate();
        gesture = null;
        plannedSteps = null;
        plannedGesture = null;
        for (VolumeStepHistory history : histories) history.clear();
        pendingCorrection = null;
        handler.removeCallbacks(detectKey);
        handler.removeCallbacks(confirmWrite);
        overlay.dismiss();
    }

    void accessibilityChanged() {
        if (!FineVolumeAccessibilityService.isConnected()) {
            cancelPending();
        }
        overlay.accessibilityChanged();
    }

    void close() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            context.unregisterReceiver(volumeReceiver);
            receiverRegistered = false;
        }
        VolumeStepSettings.preferences(context).unregisterOnSharedPreferenceChangeListener(settingsListener);
        cancelPending();
        shizuku.close();
        compatibility.close();
        if (keyWakeLock.isHeld()) keyWakeLock.release();
    }

    int compatibilityStatus() {
        return compatibility.status();
    }

    int shizukuStatus() {
        return shizuku.status();
    }

    void connectShizuku() {
        shizuku.connect();
    }

    String diagnostics() {
        long now = SystemClock.uptimeMillis();
        String media;
        try {
            media = audio.getStreamVolume(AudioManager.STREAM_MUSIC) + " / "
                    + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    + ", min=" + audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    + ", audio mode=" + audio.getMode()
                    + ", playback active=" + audio.isMusicActive();
        } catch (RuntimeException exception) {
            media = "read failed: " + exception;
        }
        return "native media volume=" + media
                + "\nvolume broadcast receiver registered=" + receiverRegistered
                + "\nvolume polling interval ms=" + POLL_MS
                + "\ncorrection requires key event=true"
                + "\nShizuku=" + shizuku.diagnostics()
                + "\ncompanion=" + compatibility.diagnostics()
                + "\npending correction=" + (pendingCorrection == null ? "none"
                        : pendingCorrection.stream + ":" + pendingCorrection.target)
                + "\nlast key age ms=" + (lastKeyTime == 0 ? "never" : now - lastKeyTime)
                + "\nlast key action=" + lastKeyAction
                + "\nscreen interactive=" + context.getSystemService(PowerManager.class).isInteractive()
                + ", keyguard locked=" + context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                + ", key wake lock held=" + keyWakeLock.isHeld()
                + "\nlast volume age ms=" + (lastVolumeTime == 0 ? "never" : now - lastVolumeTime)
                + "\nlast volume source=" + lastSource
                + "\noverlay=" + overlay.diagnostics()
                + "\nevents:\n" + String.join("\n", events);
    }

    private void record(String value) {
        events.add(SystemClock.uptimeMillis() + " " + value);
        if (Log.isLoggable("FineVolumeAdjustment", Log.DEBUG)) {
            Log.d("FineVolumeAdjustment", value);
        }
        if (events.size() > 60) {
            events.remove(0);
        }
    }

    private static class Gesture {
        final int[] initial;
        final ArrayList<Integer> directions = new ArrayList<>();
        long lastPress;
        long lastNativeChange;
        int lastNativeValue = -1;
        int stream = -1;
        boolean includesPending;
        Gesture(int[] initial) { this.initial = initial; }
    }

    private static final class Correction {
        final int stream;
        final int target;
        final long requestedAt;
        VolumeStepHistory.Plan steps;
        Gesture gesture;
        Correction(int stream, int target, long requestedAt) {
            this.stream = stream;
            this.target = target;
            this.requestedAt = requestedAt;
        }
    }
}
