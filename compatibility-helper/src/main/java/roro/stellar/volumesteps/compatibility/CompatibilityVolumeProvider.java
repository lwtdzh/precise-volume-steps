package roro.stellar.volumesteps.compatibility;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

/** Held by the main app's foreground service; no activity, overlay, or shell. */
public final class CompatibilityVolumeProvider extends ContentProvider {
    private static final String CLIENT = "roro.stellar.volumesteps";
    private static final int STALE = -1;
    private static final int FAILED = -2;
    private AudioManager audio;
    private InputDumpMonitor input;
    private boolean receiverRegistered;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            input.screenChanged(!Intent.ACTION_SCREEN_OFF.equals(intent.getAction()));
        }
    };
    private final IVolumeCompatibility.Stub binder = new IVolumeCompatibility.Stub() {
        @Override public synchronized int adjust(int stream, int target, int expectedBefore,
                                                 long expiresAt) {
            enforceClient();
            if (stream != 3 && stream != 2 && stream != 4 && stream != 5
                    && stream != 1 && stream != 0) {
                throw new IllegalArgumentException("Unsupported stream");
            }
            // Use our own application identity for the public AudioManager call.
            long identity = Binder.clearCallingIdentity();
            try {
                long remaining = expiresAt - SystemClock.uptimeMillis();
                if (remaining <= 0 || remaining > 1000
                        || audio.getStreamVolume(stream) != expectedBefore) {
                    return STALE;
                }
                int safe = Math.max(audio.getStreamMinVolume(stream),
                        Math.min(audio.getStreamMaxVolume(stream), target));
                if (SystemClock.uptimeMillis() >= expiresAt
                        || audio.getStreamVolume(stream) != expectedBefore) {
                    return STALE;
                }
                audio.setStreamVolume(stream, safe, 0);
                return audio.getStreamVolume(stream);
            } catch (RuntimeException exception) {
                return FAILED;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override public boolean registerInputListener(ICompatibilityKeyListener listener) {
            enforceClient();
            long identity = Binder.clearCallingIdentity();
            try {
                return input.register(listener);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override public void unregisterInputListener(ICompatibilityKeyListener listener) {
            enforceClient();
            long identity = Binder.clearCallingIdentity();
            try {
                input.unregister(listener);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override public boolean inputAvailable() {
            enforceClient();
            return input.available();
        }

        @Override public boolean inputActive() {
            enforceClient();
            return input.active();
        }

        @Override public void setInputPrewarm(boolean enabled) {
            enforceClient();
            long identity = Binder.clearCallingIdentity();
            try {
                input.setPrewarm(enabled);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    };

    @Override public boolean onCreate() {
        audio = getContext().getSystemService(AudioManager.class);
        input = new InputDumpMonitor(getContext());
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(screen, filter, Context.RECEIVER_EXPORTED);
        } else {
            // The project supports API 28-32, where receiver flags do not exist.
            //noinspection UnspecifiedRegisterReceiverFlag
            getContext().registerReceiver(screen, filter);
        }
        receiverRegistered = true;
        return true;
    }

    @Override public void shutdown() {
        if (receiverRegistered) {
            getContext().unregisterReceiver(screen);
            receiverRegistered = false;
        }
        if (input != null) input.close();
        super.shutdown();
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        getContext().enforceCallingPermission(
                "roro.stellar.volumesteps.permission.ADJUST_COMPATIBILITY_VOLUME",
                "Signed Precise Volume Steps permission required");
        if (!"connect".equals(method)) throw new IllegalArgumentException("Unsupported method");
        Bundle result = new Bundle();
        result.putBinder("volume", binder);
        return result;
    }

    private void enforceClient() {
        int caller = Binder.getCallingUid();
        try {
            if (caller != getContext().getPackageManager().getApplicationInfo(CLIENT, 0).uid
                    || getContext().getPackageManager().checkSignatures(
                            caller, getContext().getApplicationInfo().uid)
                    != PackageManager.SIGNATURE_MATCH) {
                throw new SecurityException(
                        "Only the signed Precise Volume Steps client is allowed");
            }
        } catch (PackageManager.NameNotFoundException exception) {
            throw new SecurityException("Precise Volume Steps is not installed");
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
