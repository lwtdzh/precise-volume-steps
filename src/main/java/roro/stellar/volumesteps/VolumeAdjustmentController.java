package roro.stellar.volumesteps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.app.KeyguardManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
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
    private static final long RAW_SETTLE_MS = 300;
    private static final long BLUETOOTH_MASK_POLL_MS = 5;
    private static final long BLUETOOTH_MASK_ARM_MS = POLL_MS + 100;
    private static final AudioAttributes MEDIA_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    private final Context context;
    private final AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VolumeOverlayController overlay;
    private final ShizukuVolumeBridge shizuku;
    private final ShizukuKeyBridge rawKeys;
    private final RootVolumeBridge root;
    private final RawVolumePress rawPress = new RawVolumePress();
    private final CompatibilityVolumeBridge compatibility;
    private final int[] observed = new int[12];
    private final int[] previousObserved = new int[12];
    private final long[] changedAt = new long[12];
    private final long[] confirmedAt = new long[12];
    private final PowerManager.WakeLock keyWakeLock;
    private final VolumeStepHistory[] histories = new VolumeStepHistory[12];
    private final VolumeStepHistory[] previousHistories = new VolumeStepHistory[12];
    private VolumeStepHistory.Plan plannedSteps;
    private Gesture plannedGesture;
    private final ArrayList<String> events = new ArrayList<>();
    private Gesture gesture;
    private Correction pendingCorrection;
    private final Runnable detectKey = this::detectKeyAdjustment;
    private final Runnable confirmWrite = this::confirmCorrection;
    private final Runnable watchBluetoothStep = () -> {
        Gesture active = gesture;
        if (active != null) maskRawBluetoothNativeStep(active);
    };
    private long lastKeyTime;
    private long lastDownTime = -1;
    private int lastDownCode;
    private String lastKeyAction = "none";
    private long lastVolumeTime;
    private long lastBluetoothMaskArm;
    private int lastBluetoothMaskOrigin = -1;
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
            refreshAssistantInputPrewarm();
            refreshPrivilegedBluetoothMask();
            handler.postDelayed(this, POLL_MS);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (preferences, key) -> {
                if ("steps".equals(key) || "media_only".equals(key)
                        || "operating_mode".equals(key) || "shizuku_enabled".equals(key)) {
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
        compatibility = new CompatibilityVolumeBridge(context,
                VolumeKeeperService::refreshNotification,
                (code, value, observedAfter, observedAt) ->
                        onExplicitKey("compatibility-dump", code, value,
                                observedAfter, observedAt, true, -1));
        rawKeys = new ShizukuKeyBridge(context,
                (node, code, value, time, maskedOrigin) ->
                        onExplicitKey(node, code, value, time, time, false, maskedOrigin),
                VolumeKeeperService::refreshNotification);
        root = new RootVolumeBridge(context,
                (node, code, value, time, maskedOrigin) ->
                        onExplicitKey("root:" + node, code, value, time, time,
                                false, maskedOrigin),
                VolumeKeeperService::refreshNotification);
        root.connect();
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
        // Raw input owns noninteractive presses when available. The firmware may
        // also send accessibility callbacks for the same press.
        if (nonInteractive() && (privilegedInputReady() || companionInputOwnsScreenOff())) return;
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
            } else if (gesture != null && gesture.ordinaryIdle) {
                gesture.released = true;
                gesture.lastPress = SystemClock.uptimeMillis();
                lastKeyTime = gesture.lastPress;
                lastKeyAction = "ordinary idle up";
                handler.removeCallbacks(detectKey);
                handler.postDelayed(detectKey, DETECT_MS);
            }
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        lastDownTime = event.getDownTime();
        lastDownCode = code;
        beginKey(direction, event.getRepeatCount(), event.getEventTime(), false, false);
    }

    private boolean nonInteractive() {
        return !context.getSystemService(PowerManager.class).isInteractive()
                || context.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void onExplicitKey(String node, int code, int value,
                               long observedAfter, long observedAt,
                               boolean companionInput, int maskedOrigin) {
        if (closed || !VolumeStepSettings.isEnabled(context)) return;
        long now = SystemClock.uptimeMillis();
        String source = companionInput ? "companion input" : "raw input";
        record(source + " code=" + code + " value=" + value
                + " observed interval ms=" + (observedAt - observedAfter)
                + " age ms=" + (now - observedAt) + " noninteractive=" + nonInteractive()
                + (companionInput ? "" : " mask arm age ms="
                + (lastBluetoothMaskArm == 0 ? "never" : now - lastBluetoothMaskArm)
                + " origin=" + lastBluetoothMaskOrigin
                + " result=" + maskedOrigin));
        if (code == RawVolumeKeyParser.POWER) {
            if (value == 1) {
                rawPress.down(node, code, observedAt, now, false);
                if (gesture != null && gesture.raw) cancelPending();
            } else if (value == 0) {
                rawPress.up(node, code, observedAt, now);
            }
            return;
        }
        boolean sourceReady = companionInput ? compatibility.inputReady()
                : node.startsWith("root:") ? root.inputReady() : rawKeys.ready();
        boolean eligible = nonInteractive() && sourceReady
                && audio.getMode() == AudioManager.MODE_NORMAL;
        if (value == 1) {
            if (!rawPress.down(node, code, observedAt, now, eligible)) {
                if (gesture != null && gesture.raw) cancelPending();
                return;
            }
            lastKeyTime = now;
            lastKeyAction = "raw down";
            beginKey(code == RawVolumeKeyParser.UP ? 1 : -1, 0,
                    companionInput ? observedAfter : observedAt,
                    true, companionInput);
            if (gesture != null && maskedOrigin >= 0
                    && gesture.maskOrigin == maskedOrigin) {
                gesture.nativeStepMasked = true;
                record("privileged input masked native Bluetooth step at " + maskedOrigin);
            }
            Gesture held = gesture;
            handler.postDelayed(() -> {
                if (gesture == held && held != null && held.raw && !held.released) {
                    record("raw press expired without release");
                    cancelPending();
                }
            }, 10000);
        } else if (value == 2) {
            rawPress.repeat(node, code);
        } else if (value == 0) {
            int count = rawPress.up(node, code, observedAt, now);
            Gesture active = gesture;
            if (active == null || !active.raw) return;
            if (count == 0 || !eligible) { cancelPending(); return; }
            int direction = code == RawVolumeKeyParser.UP ? 1 : -1;
            for (int n = 1; n < count; n++) active.directions.add(direction);
            active.released = true;
            handler.removeCallbacks(watchBluetoothStep);
            active.lastPress = now;
            active.releaseSnapshot = snapshot();
            lastKeyTime = now;
            lastKeyAction = "raw up";
            holdKeyWakeLock();
            record("raw release direction=" + direction + " presses=" + count);
            handler.removeCallbacks(detectKey);
            finishRawRelease(active);
        }
    }

    private void beginKey(int direction, int repeat, long eventTime, boolean raw,
                          boolean companionInput) {
        holdKeyWakeLock();
        record((raw ? "raw key " : "key ") + direction + " repeat=" + repeat
                + " event age ms=" + (SystemClock.uptimeMillis() - eventTime));
        overlay.holdUpdates();
        shizuku.invalidate();
        root.invalidate();
        compatibility.invalidate();
        int pressOrigin = raw ? RawVolumeBaseline.beforeKey(
                audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                observed[AudioManager.STREAM_MUSIC], previousObserved[AudioManager.STREAM_MUSIC],
                changedAt[AudioManager.STREAM_MUSIC], eventTime, direction) : -1;
        if (gesture == null) {
            if (pendingCorrection != null && pendingCorrection.gesture != null) {
                // A newer press supersedes an unconfirmed write. Recompute the
                // complete gesture from its original readback, not an intermediate value.
                gesture = new Gesture(pendingCorrection.gesture.initial.clone());
                gesture.directions.addAll(pendingCorrection.gesture.directions);
                gesture.paths = pendingCorrection.gesture.paths;
                gesture.stream = pendingCorrection.stream;
                gesture.includesPending = true;
            } else {
                gesture = new Gesture(snapshot());
                gesture.ordinaryScreenOff = !raw && ordinaryScreenOffBaseEligible();
                gesture.ordinaryIdle = !raw && ordinaryIdleFallbackEligible();
                for (int stream : STREAMS) {
                    if (raw) {
                        gesture.initial[stream] = RawVolumeBaseline.beforeKey(
                                gesture.initial[stream], observed[stream], previousObserved[stream],
                                changedAt[stream], eventTime, direction);
                    } else {
                        // An accessibility callback can arrive after the native
                        // adjustment and its observation. Ignore our own prior
                        // acknowledgement and changes preceding the KeyEvent.
                        int current = gesture.initial[stream];
                        int initial = AccessibilityVolumeBaseline.beforeKey(
                                current, observed[stream],
                                changedAt[stream] > confirmedAt[stream] ? previousObserved[stream] : -1,
                                changedAt[stream], eventTime, direction);
                        gesture.initial[stream] = initial;
                        if (initial != current && previousHistories[stream] != null) {
                            gesture.paths[stream] = previousHistories[stream].copy();
                        }
                    }
                }
            }
            if (!raw) handler.postDelayed(detectKey, DETECT_MS);
        }
        gesture.raw = raw;
        gesture.mode = VolumeStepSettings.mode(context);
        gesture.companionInput = companionInput;
        gesture.released = !raw;
        if (raw) gesture.releaseSnapshot = null;
        gesture.lastDirection = direction;
        gesture.directions.add(direction);
        gesture.lastPress = SystemClock.uptimeMillis();
        if (raw) {
            gesture.maskPending = false;
            gesture.maskFailed = false;
            gesture.maskOrigin = pressOrigin;
            maskRawBluetoothNativeStep(gesture);
        }
    }

    private void maskRawBluetoothNativeStep(Gesture active) {
        handler.removeCallbacks(watchBluetoothStep);
        int stream = AudioManager.STREAM_MUSIC;
        if (closed || active != gesture || active.released || active.maskPending || active.maskFailed
                || !explicitInputReady(active) || !nonInteractive()
                || audio.getMode() != AudioManager.MODE_NORMAL || !audio.isMusicActive()
                || !isWirelessMediaRoute() || !inScope(stream)
                || active.maskOrigin < 0) {
            return;
        }
        int current = audio.getStreamVolume(stream);
        if (!RawCorrectionTiming.shouldApplyBeforeSettle(
                true, true, true, active.maskOrigin, current, active.lastDirection)) {
            // Shell input can arrive before AudioService's native write, while
            // screen-off broadcasts are suppressed. Watch only this held key.
            if (SystemClock.uptimeMillis() - active.lastPress < KEY_TIMEOUT_MS)
                handler.postDelayed(watchBluetoothStep, BLUETOOTH_MASK_POLL_MS);
            return;
        }
        active.maskPending = true;
        active.maskRequestedAt = SystemClock.uptimeMillis();
        record("mask native Bluetooth playback step stream=" + stream
                + " " + current + " -> " + active.maskOrigin);
        java.util.function.IntConsumer callback = result -> {
            if (active != gesture) return;
            active.maskPending = false;
            active.maskFailed = result < 0;
            active.nativeStepMasked = result == active.maskOrigin;
            record("mask native Bluetooth playback result=" + result);
            if (active.released) finishRawRelease(active);
            else if (!active.maskFailed) maskRawBluetoothNativeStep(active);
        };
        if (active.companionInput) compatibility.adjust(stream, active.maskOrigin, current,
                active.maskRequestedAt + 500, result -> {
                    callback.accept(result);
                });
        else if (active.mode == OperatingMode.ROOT)
            root.adjust(stream, active.maskOrigin, current, active.maskRequestedAt + 500, callback);
        else shizuku.adjust(stream, active.maskOrigin, current, active.maskRequestedAt + 500, callback);
    }

    private void finishRawRelease(Gesture active) {
        if (active != gesture || !active.released || active.releaseHandled) return;
        if (active.maskPending
                && SystemClock.uptimeMillis() - active.maskRequestedAt < 500) {
            handler.postDelayed(() -> finishRawRelease(active), DETECT_MS);
            return;
        }
        active.maskPending = false;
        active.releaseSnapshot = snapshot();
        if (applyRawBluetoothPlaybackAtRelease(active)) return;
        handler.removeCallbacks(detectKey);
        handler.postDelayed(detectKey, DETECT_MS);
    }

    private boolean applyRawBluetoothPlaybackAtRelease(Gesture active) {
        if (!explicitInputReady(active) || !nonInteractive()
                || audio.getMode() != AudioManager.MODE_NORMAL || !audio.isMusicActive()
                || !isWirelessMediaRoute() || active.releaseSnapshot == null) {
            return false;
        }
        for (int stream : STREAMS) {
            if (stream != AudioManager.STREAM_MUSIC || !inScope(stream)
                    || !RawCorrectionTiming.shouldApplyAtRelease(
                    true, true, true, active.nativeStepMasked,
                    active.initial[stream],
                    active.releaseSnapshot[stream],
                    active.lastDirection)) {
                continue;
            }
            active.stream = stream;
            active.lastNativeValue = active.releaseSnapshot[stream];
            active.lastNativeChange = active.lastPress;
            active.correctionStabilizeUntil = active.lastPress + RAW_SETTLE_MS;
            active.releaseHandled = true;
            plannedSteps = active.paths[stream].plan(active.initial[stream],
                    audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream),
                    VolumeStepSettings.steps(context), active.directions);
            plannedGesture = active;
            gesture = null;
            record("raw Bluetooth playback correction at release stream=" + stream
                    + " native=" + active.releaseSnapshot[stream]
                    + " target=" + plannedSteps.target());
            apply(stream, plannedSteps.target());
            return true;
        }
        return false;
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
            if (ordinaryIdleFallbackEligible()) {
                holdKeyWakeLock();
                overlay.holdUpdates();
                shizuku.invalidate();
                root.invalidate();
                compatibility.invalidate();
                gesture = new Gesture(current);
                gesture.directions.add(direction);
                gesture.lastPress = now;
                gesture.ordinaryIdle = true;
                gesture.ordinaryScreenOff = true;
                gesture.released = true;
                lastKeyTime = now;
                lastKeyAction = "ordinary idle key up recovered";
                record("locked/off idle key up recovered direction=" + direction);
                handler.removeCallbacks(detectKey);
                handler.postDelayed(detectKey, DETECT_MS);
                return;
            }
            record("unmatched key up without recent native change");
            return;
        }
        holdKeyWakeLock();
        overlay.holdUpdates();
        shizuku.invalidate();
        root.invalidate();
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
        if (active.raw && !active.released) return;
        if (active.ordinaryIdle && !active.released) return;
        if (active.raw && (!explicitInputReady(active) || !nonInteractive()
                || audio.getMode() != AudioManager.MODE_NORMAL)) {
            cancelPending();
            return;
        }
        // Firmware can dispatch the native key after its release. The Bluetooth
        // playback path above corrects an already-observed release snapshot and
        // keeps this same deadline as a guard against a later overwrite.
        if (active.raw && SystemClock.uptimeMillis() - active.lastPress < RAW_SETTLE_MS) {
            handler.postDelayed(detectKey, DETECT_MS);
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
        long now = SystemClock.uptimeMillis();
        long quiet = now - active.lastPress;
        if (active.ordinaryScreenOff && (quiet < RAW_SETTLE_MS
                || active.stream >= 0
                && now - active.lastNativeChange < RAW_SETTLE_MS)) {
            // Physical Vivo wake keys can keep issuing native coarse writes
            // hundreds of milliseconds after accessibility delivers the key.
            // Wait for both the callback and observed native writes to settle.
            handler.postDelayed(detectKey, DETECT_MS);
            return;
        }
        if (active.stream < 0 || quiet < DETECT_MS
                || now - active.lastNativeChange < DETECT_MS) {
            if (active.raw && active.stream < 0 && quiet >= RAW_SETTLE_MS) {
                applyRawIdleKey(active);
                return;
            }
            if (active.ordinaryIdle && active.stream < 0
                    && quiet >= RAW_SETTLE_MS) {
                applyOrdinaryIdleKey(active);
                return;
            }
            if (quiet >= KEY_TIMEOUT_MS) {
                record("key expired without native change");
                gesture = null;
                if (pendingCorrection == null) {
                    overlay.releaseUpdates();
                }
                return;
            }
            handler.postDelayed(detectKey, DETECT_MS);
            return;
        }
        gesture = null;
        int stream = active.stream;
        // Start from an observed value. An unacknowledged previous target may
        // have been rejected by the system and must not become the next base.
        plannedSteps = active.paths[stream].plan(active.initial[stream],
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
            if (gesture != null && gesture.raw && !gesture.released) {
                maskRawBluetoothNativeStep(gesture);
            }
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
            if (gesture == null && pendingCorrection == null) {
                int inferred = inferredOrdinaryDirection(stream, previous, current);
                if (inferred != 0) {
                    beginInferredOrdinaryKey(stream, previous, current, inferred);
                } else {
                    // A later accessibility callback can establish that this was a
                    // native key adjustment. Retain its preceding rounding path.
                    previousHistories[stream] = histories[stream].copy();
                    histories[stream].clear();
                    overlay.updateActual(stream, current, audio.getStreamMaxVolume(stream));
                }
            }
        } catch (RuntimeException exception) {
            record("volume read failed: " + exception);
        }
    }

    private int inferredOrdinaryDirection(int stream, int before, int after) {
        return ScreenOffVolumeTransition.direction(
                stream == AudioManager.STREAM_MUSIC,
                VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT,
                CompatibilityVolumeBridge.supportedDevice(),
                compatibility.ready(),
                !context.getSystemService(PowerManager.class).isInteractive(),
                audio.isMusicActive(),
                audio.getMode() == AudioManager.MODE_NORMAL,
                isWirelessMediaRoute(),
                before,
                after,
                audio.getStreamMinVolume(stream),
                audio.getStreamMaxVolume(stream));
    }

    private boolean isWirelessMediaRoute() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return audio.isBluetoothA2dpOn();
        }
        for (AudioDeviceInfo device : audio.getAudioDevicesForAttributes(MEDIA_ATTRIBUTES)) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private void refreshPrivilegedBluetoothMask() {
        if (!VolumeStepSettings.isEnabled(context) || !nonInteractive()
                || audio.getMode() != AudioManager.MODE_NORMAL || !audio.isMusicActive()
                || !isWirelessMediaRoute() || !inScope(AudioManager.STREAM_MUSIC)) {
            return;
        }
        try {
            int origin = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            long now = SystemClock.uptimeMillis();
            if (gesture != null || pendingCorrection != null
                    || observed[AudioManager.STREAM_MUSIC] != origin
                    || now - changedAt[AudioManager.STREAM_MUSIC] < POLL_MS) {
                return;
            }
            long expiresAt = SystemClock.uptimeMillis() + BLUETOOTH_MASK_ARM_MS;
            lastBluetoothMaskOrigin = origin;
            lastBluetoothMaskArm = now;
            OperatingMode mode = VolumeStepSettings.mode(context);
            if (mode == OperatingMode.ROOT) root.armBluetoothMask(origin, expiresAt);
            else if (mode == OperatingMode.SHIZUKU)
                rawKeys.armBluetoothMask(origin, expiresAt);
        } catch (RuntimeException exception) {
            record("Bluetooth mask arm failed: " + exception);
        }
    }

    private void refreshAssistantInputPrewarm() {
        boolean enabled = false;
        try {
            enabled = VolumeStepSettings.isEnabled(context)
                    && VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT
                    && compatibility.ready()
                    && audio.getMode() == AudioManager.MODE_NORMAL
                    && audio.isMusicActive()
                    && isWirelessMediaRoute()
                    && inScope(AudioManager.STREAM_MUSIC);
        } catch (RuntimeException exception) {
            record("Assistant input prewarm check failed: " + exception);
        }
        compatibility.setInputPrewarm(enabled);
    }

    private void beginInferredOrdinaryKey(int stream, int before, int after, int direction) {
        long now = SystemClock.uptimeMillis();
        holdKeyWakeLock();
        overlay.holdUpdates();
        shizuku.invalidate();
        root.invalidate();
        compatibility.invalidate();
        Gesture inferred = new Gesture(snapshot());
        inferred.initial[stream] = before;
        inferred.stream = stream;
        inferred.lastNativeValue = after;
        inferred.lastNativeChange = now;
        inferred.lastPress = now;
        inferred.directions.add(direction);
        inferred.ordinaryScreenOff = true;
        gesture = inferred;
        lastKeyTime = now;
        lastKeyAction = direction > 0 ? "inferred up" : "inferred down";
        record("ordinary screen-off native key inferred direction=" + direction
                + " stream=" + stream + " " + before + " -> " + after);
        handler.postDelayed(detectKey, DETECT_MS);
    }

    /** Only a released raw key can adjust media when firmware made no change. */
    private void applyRawIdleKey(Gesture active) {
        int stream = AudioManager.STREAM_MUSIC;
        if (!inScope(stream) || audio.getMode() != AudioManager.MODE_NORMAL
                || !nonInteractive() || !explicitInputReady(active)) {
            record("raw idle key skipped stream=" + stream
                    + " mode=" + audio.getMode() + " a2dp=" + audio.isBluetoothA2dpOn());
            gesture = null;
            if (pendingCorrection == null) {
                overlay.releaseUpdates();
            }
            return;
        }
        int origin = active.initial[stream];
        int current = audio.getStreamVolume(stream);
        if (current != origin && (pendingCorrection == null
                || pendingCorrection.stream != stream || current != pendingCorrection.target)) {
            record("raw idle key discarded after external change");
            cancelPending();
            return;
        }
        VolumeStepHistory.Plan plan = active.paths[stream].plan(origin,
                audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream),
                VolumeStepSettings.steps(context), active.directions);
        plannedSteps = plan;
        plannedGesture = active;
        gesture = null;
        record("raw idle key adjust presses=" + active.directions.size()
                + " stream=" + stream + " " + origin + " -> " + plan.target());
        apply(stream, plan.target());
    }

    private boolean ordinaryScreenOffBaseEligible() {
        return VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT
                && CompatibilityVolumeBridge.supportedDevice()
                && compatibility.ready()
                && !context.getSystemService(PowerManager.class).isInteractive()
                && audio.getMode() == AudioManager.MODE_NORMAL
                && !isWirelessMediaRoute()
                && inScope(AudioManager.STREAM_MUSIC);
    }

    private boolean explicitInputReady(Gesture active) {
        return active.mode == VolumeStepSettings.mode(context)
                && (active.companionInput ? companionInputOwnsScreenOff()
                : active.mode == OperatingMode.ROOT ? root.inputReady() : rawKeys.ready());
    }

    private boolean privilegedInputReady() {
        return root.inputReady() || rawKeys.ready();
    }

    private boolean companionInputOwnsScreenOff() {
        return compatibility.inputReady()
                && !context.getSystemService(PowerManager.class).isInteractive();
    }

    private boolean ordinaryIdleFallbackEligible() {
        return ordinaryScreenOffBaseEligible() && !audio.isMusicActive();
    }

    private void applyOrdinaryIdleKey(Gesture active) {
        int stream = AudioManager.STREAM_MUSIC;
        int origin = active.initial[stream];
        int current = audio.getStreamVolume(stream);
        if (!ordinaryIdleFallbackEligible() || current != origin) {
            record("ordinary idle key discarded current=" + current + " origin=" + origin);
            cancelPending();
            return;
        }
        VolumeStepHistory.Plan plan = active.paths[stream].plan(origin,
                audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream),
                VolumeStepSettings.steps(context), active.directions);
        plannedSteps = plan;
        plannedGesture = active;
        gesture = null;
        record("ordinary idle key adjust stream=" + stream
                + " " + origin + " -> " + plan.target());
        apply(stream, plan.target());
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
            if (plannedGesture != null) {
                correction.stabilizeUntil = plannedGesture.correctionStabilizeUntil;
            }
            plannedSteps = null;
            plannedGesture = null;
            pendingCorrection = correction;
            if (target != current) {
                dispatchCorrection(correction, current, 0);
                return;
            }
            record("key write stream=" + stream + " target=" + target);
            confirmCorrection();
        } catch (RuntimeException exception) {
            pendingCorrection = null;
            markUnconfirmed(stream);
            record("correction failed: " + exception);
        }
    }

    private void dispatchCorrection(Correction correction, int current, int attempt) {
        if (closed || pendingCorrection != correction) {
            return;
        }
        if (root.ready()) {
            record("Root key write stream=" + correction.stream + " target=" + correction.target);
            root.adjust(correction.stream, correction.target, current, correction.requestedAt + 500,
                    result -> handleBackendResult("Root", correction, result, attempt));
            handler.postDelayed(confirmWrite, DETECT_MS);
            return;
        }
        if (shizuku.ready()) {
            record("Shizuku key write stream=" + correction.stream + " target=" + correction.target);
            shizuku.adjust(correction.stream, correction.target, current, correction.requestedAt + 500,
                    result -> handleBackendResult("Shizuku", correction, result, attempt));
            handler.postDelayed(confirmWrite, DETECT_MS);
            return;
        }
        if (VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT && compatibility.ready()) {
            record("companion key write stream=" + correction.stream + " target=" + correction.target);
            compatibility.adjust(correction.stream, correction.target, current, correction.requestedAt + 500,
                    result -> handleBackendResult("companion", correction, result, attempt));
            handler.postDelayed(confirmWrite, DETECT_MS);
            return;
        }
        // The firmware can ignore this app's own background write after the helper
        // process has just been recreated. Give the provider connection a bounded
        // moment to recover instead of issuing a write that is bound to fail.
        boolean background = !context.getSystemService(PowerManager.class).isInteractive()
                || context.getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (background && CompatibilityVolumeBridge.supportedDevice() && attempt < 4) {
            record("waiting for volume backend attempt=" + attempt
                    + " stream=" + correction.stream);
            handler.postDelayed(() -> {
                if (pendingCorrection == correction) {
                    dispatchCorrection(correction, current, attempt + 1);
                }
            }, 150);
            return;
        }
        try {
            audio.setStreamVolume(correction.stream, correction.target, 0);
            record("key write stream=" + correction.stream + " target=" + correction.target);
            confirmCorrection();
        } catch (RuntimeException exception) {
            pendingCorrection = null;
            markUnconfirmed(correction.stream);
            record("correction failed: " + exception);
        }
    }

    private void handleBackendResult(String backend, Correction correction, int result, int attempt) {
        if (pendingCorrection != correction || closed) return;
        record(backend + " result=" + result);
        if (result >= 0) {
            confirmCorrection();
            return;
        }
        if (result == -1) {
            try {
                int latest = audio.getStreamVolume(correction.stream);
                if (latest == correction.target) {
                    confirmCorrection();
                    return;
                }
                if (canRetryRejectedCorrection(correction, latest, attempt)) {
                    record(backend + " rejected stale expected value; retry current="
                            + latest + " target=" + correction.target);
                    dispatchCorrection(correction, latest, attempt + 1);
                    return;
                }
            } catch (RuntimeException exception) {
                record(backend + " retry read failed: " + exception);
            }
        }
        pendingCorrection = null;
        markUnconfirmed(correction.stream);
    }

    private boolean canRetryRejectedCorrection(Correction correction, int current, int attempt) {
        if (attempt >= 1 || current == correction.target
                || SystemClock.uptimeMillis() > correction.requestedAt + 500
                || correction.gesture == null) {
            return false;
        }
        int origin = correction.gesture.initial[correction.stream];
        int direction = Integer.signum(current - origin);
        return direction != 0 && correction.gesture.directions.contains(direction);
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
                long now = SystemClock.uptimeMillis();
                if (RawCorrectionTiming.shouldHoldConfirmation(
                        now, active.stabilizeUntil, gesture != null)) {
                    handler.postDelayed(confirmWrite,
                            Math.min(DETECT_MS, active.stabilizeUntil - now));
                    return;
                }
                // Rebase a waiting key before retiring the acknowledgement.
                if (gesture != null && gesture.initial[active.stream] != current
                        && gesture.stream < 0 && !gesture.includesPending) {
                    gesture.initial[active.stream] = current;
                }
                pendingCorrection = null;
                observed[active.stream] = current;
                confirmedAt[active.stream] = SystemClock.uptimeMillis();
                if (active.steps != null) histories[active.stream].confirm(active.steps);
                record("key confirmed stream=" + active.stream + " value=" + current
                        + " delay=" + (SystemClock.uptimeMillis() - active.requestedAt));
                VolumeKeeperService.refreshNotification();
                showResult(active.stream, current,
                        VolumeOverlayController.CorrectionState.CONFIRMED);
                return;
            }
            int origin = active.gesture == null ? current
                    : active.gesture.initial[active.stream];
            int direction = Integer.signum(current - origin);
            boolean matchingDirection = active.gesture != null && direction != 0
                    && active.gesture.directions.contains(direction);
            long now = SystemClock.uptimeMillis();
            if (RawCorrectionTiming.shouldRetryAfterSettle(
                    now, active.stabilizeUntil, active.stabilizationRetried,
                    gesture != null, origin, current, active.target, matchingDirection)) {
                active.stabilizationRetried = true;
                active.stabilizeUntil = 0;
                record("late native write after early correction stream=" + active.stream
                        + " value=" + current + " target=" + active.target);
                dispatchCorrection(active, current, 0);
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
        Arrays.fill(previousHistories, null);
    }

    private void cancelPending() {
        shizuku.invalidate();
        root.invalidate();
        compatibility.invalidate();
        gesture = null;
        plannedSteps = null;
        plannedGesture = null;
        rawPress.cancel();
        for (VolumeStepHistory history : histories) history.clear();
        Arrays.fill(previousHistories, null);
        pendingCorrection = null;
        handler.removeCallbacks(detectKey);
        handler.removeCallbacks(confirmWrite);
        handler.removeCallbacks(watchBluetoothStep);
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
        rawKeys.close();
        root.close();
        compatibility.close();
        if (keyWakeLock.isHeld()) keyWakeLock.release();
    }

    int compatibilityStatus() {
        return compatibility.status();
    }

    int shizukuStatus() {
        if (shizuku.ready() && !rawKeys.ready()) return R.string.shizuku_keys_unavailable;
        return shizuku.status();
    }

    void connectShizuku() {
        shizuku.connect();
        rawKeys.connect();
        root.connect();
        compatibility.refreshInput();
    }

    String rootStatus() { return root.status(); }
    boolean rootInputReady() { return root.inputReady(); }

    void setupRoot(java.util.function.Consumer<String> result) {
        root.authorizeAndSetup(result);
    }

    void refreshCompatibilityInput() {
        compatibility.refreshInput();
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
                + "\nRoot=" + root.diagnostics()
                + "\nraw volume keys=" + rawKeys.diagnostics()
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

    private class Gesture {
        final int[] initial;
        final ArrayList<Integer> directions = new ArrayList<>();
        long lastPress;
        long lastNativeChange;
        int lastNativeValue = -1;
        int stream = -1;
        boolean includesPending;
        boolean raw;
        boolean companionInput;
        OperatingMode mode;
        boolean ordinaryScreenOff;
        boolean ordinaryIdle;
        boolean released;
        int lastDirection;
        int[] releaseSnapshot;
        long correctionStabilizeUntil;
        int maskOrigin = -1;
        long maskRequestedAt;
        boolean maskPending;
        boolean maskFailed;
        boolean nativeStepMasked;
        boolean releaseHandled;
        VolumeStepHistory[] paths = new VolumeStepHistory[histories.length];
        Gesture(int[] initial) {
            this.initial = initial;
            for (int i = 0; i < paths.length; i++) paths[i] = histories[i].copy();
        }
    }

    private static final class Correction {
        final int stream;
        final int target;
        final long requestedAt;
        VolumeStepHistory.Plan steps;
        Gesture gesture;
        long stabilizeUntil;
        boolean stabilizationRetried;
        Correction(int stream, int target, long requestedAt) {
            this.stream = stream;
            this.target = target;
            this.requestedAt = requestedAt;
        }
    }
}
