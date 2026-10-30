package roro.stellar.volumesteps;

import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Set;

/** Optional native dispatch of an index already accepted by AudioService. */
final class BluetoothVolumeSync {
    private final Object audio;
    private final Method getVolume;
    private final Method isMuted;
    private final Method getBehavior;
    private final Method getDevices;
    private final Method isActive;
    private final Method setIndex;
    private final Method convertDevice;
    private final java.lang.reflect.Constructor<?> deviceConstructor;
    private final Set<?> a2dpDevices;
    private final int outputRole;
    private final int variableBehavior;
    private boolean available = true;
    private int armedRoute;
    private int armedOrigin = -1;
    private long armedExpiresAt;

    static BluetoothVolumeSync create(Object audio, Class<?> audioType) {
        try {
            return new BluetoothVolumeSync(audio, audioType);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Older/OEM APIs retain the normal AudioService dispatch.
            return null;
        }
    }

    private BluetoothVolumeSync(Object audio, Class<?> audioType)
            throws ReflectiveOperationException {
        this.audio = audio;
        Class<?> system = Class.forName("android.media.AudioSystem");
        Class<?> attributes = Class.forName("android.media.AudioDeviceAttributes");
        Class<?> deviceInfo = Class.forName("android.media.AudioDeviceInfo");
        Class<?> manager = Class.forName("android.media.AudioManager");
        getVolume = audioType.getMethod("getStreamVolume", int.class);
        isMuted = audioType.getMethod("isStreamMute", int.class);
        getBehavior = audioType.getMethod("getDeviceVolumeBehavior", attributes);
        getDevices = system.getMethod("getDevicesForStream", int.class);
        isActive = system.getMethod("isStreamActive", int.class, int.class);
        setIndex = system.getMethod("setStreamVolumeIndexAS",
                int.class, int.class, boolean.class, int.class);
        convertDevice = deviceInfo.getMethod("convertInternalDeviceToDeviceType", int.class);
        deviceConstructor = attributes.getConstructor(int.class, int.class, String.class);
        a2dpDevices = (Set<?>) system.getField("DEVICE_OUT_ALL_A2DP_SET").get(null);
        outputRole = attributes.getField("ROLE_OUTPUT").getInt(null);
        variableBehavior = manager.getField("DEVICE_VOLUME_BEHAVIOR_VARIABLE").getInt(null);
    }

    boolean canDispatch(int stream, int accepted, long expiresAt) {
        if (!available || stream != 3 || SystemClock.uptimeMillis() >= expiresAt) return false;
        try {
            return eligibleRoute(stream, accepted, expiresAt) != 0;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            available = false;
            Log.d("VolumeSteps", "Optional Bluetooth volume sync unavailable", exception);
            return false;
        }
    }

    boolean arm(int stream, int origin, long expiresAt) {
        if (!available || stream != 3 || SystemClock.uptimeMillis() >= expiresAt) return false;
        try {
            int route = eligibleRoute(stream, origin, expiresAt);
            armedRoute = route;
            armedOrigin = route == 0 ? -1 : origin;
            armedExpiresAt = route == 0 ? 0 : expiresAt;
            return route != 0;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            armedRoute = 0;
            armedOrigin = -1;
            armedExpiresAt = 0;
            available = false;
            return false;
        }
    }

    boolean dispatchArmed(int stream, int origin, int current, long expiresAt) {
        if (!available || stream != 3 || origin != armedOrigin
                || expiresAt > armedExpiresAt || SystemClock.uptimeMillis() >= expiresAt) {
            return false;
        }
        try {
            if ((Integer) getDevices.invoke(null, stream) != armedRoute
                    || (Boolean) isMuted.invoke(audio, stream)
                    || (Integer) getVolume.invoke(audio, stream) != current
                    || SystemClock.uptimeMillis() >= expiresAt) {
                return false;
            }
            int status = (Integer) setIndex.invoke(
                    null, stream, origin, false, armedRoute);
            if (status == 0) return true;
            available = false;
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            available = false;
            return false;
        }
    }

    private int eligibleRoute(int stream, int accepted, long expiresAt)
            throws ReflectiveOperationException {
        int route = (Integer) getDevices.invoke(null, stream);
        // An exact device excludes combined speaker/wireless routes, SCO,
        // LE Audio, hearing aids and any vendor device we cannot identify.
        if (!a2dpDevices.contains(route) || !(Boolean) isActive.invoke(null, stream, 0))
            return 0;
        int publicType = (Integer) convertDevice.invoke(null, route);
        Object device = deviceConstructor.newInstance(outputRole, publicType, "");
        // AudioService includes legacy AVRCP absolute volume in this query.
        // FULL/FIXED/ABSOLUTE routes use a different native gain mapping.
        if ((Integer) getBehavior.invoke(audio, device) != variableBehavior
                || (Boolean) isMuted.invoke(audio, stream)
                || (Integer) getDevices.invoke(null, stream) != route
                || (Integer) getVolume.invoke(audio, stream) != accepted
                || SystemClock.uptimeMillis() >= expiresAt)
            return 0;
        return route;
    }

    void dispatch(int stream, int accepted, long expiresAt) {
        if (!available || stream != 3 || SystemClock.uptimeMillis() >= expiresAt) return;
        try {
            int route = eligibleRoute(stream, accepted, expiresAt);
            if (route == 0) return;
            int status = (Integer) setIndex.invoke(null, stream, accepted, false, route);
            if (status != 0) {
                available = false;
                Log.d("VolumeSteps", "Optional Bluetooth volume sync unavailable: " + status);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Never repeat an uncertain native dispatch. AudioService's
            // original queued write is still responsible for the result.
            available = false;
            Log.d("VolumeSteps", "Optional Bluetooth volume sync unavailable", exception);
        }
    }
}
