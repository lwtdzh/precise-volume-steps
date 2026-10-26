package roro.stellar.volumesteps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public final class VolumeKeeperService extends Service {
    private static final String TAG = "VolumeKeeperService";
    private static final String CHANNEL_ID = "volume_steps_background";
    private static final int NOTIFICATION_ID = 2107;

    private static volatile boolean running;
    private static volatile String startError;
    private static volatile VolumeKeeperService instance;
    private volatile VolumeAdjustmentController adjustmentController;

    static void start(Context context) {
        if (!VolumeStepSettings.isEnabled(context)) {
            return;
        }
        try {
            startError = null;
            Intent intent = new Intent(context, VolumeKeeperService.class);
            context.startForegroundService(intent);
        } catch (RuntimeException exception) {
            startError = exception.getClass().getSimpleName();
            Log.w(TAG, "Unable to start background keeper", exception);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, VolumeKeeperService.class));
    }

    static boolean isRunning() {
        return running;
    }

    static String getStartError() {
        return startError;
    }

    static VolumeAdjustmentController controller() {
        return instance == null ? null : instance.adjustmentController;
    }

    static void refreshNotification() {
        if (instance != null && running) {
            instance.getSystemService(NotificationManager.class).notify(
                    NOTIFICATION_ID, instance.buildNotification());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            running = true;
            startError = null;
            adjustmentController = new VolumeAdjustmentController(this);
        } catch (RuntimeException exception) {
            running = false;
            startError = exception.getClass().getSimpleName();
            Log.w(TAG, "Unable to enter foreground", exception);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!VolumeStepSettings.isEnabled(this) || !running) {
            stopSelf();
            return START_NOT_STICKY;
        }
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID,
                buildNotification()
        );
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (adjustmentController != null) {
            adjustmentController.close();
            adjustmentController = null;
        }
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("version=" + getPackageName());
        writer.println("enabled=" + VolumeStepSettings.isEnabled(this)
                + ", steps=" + VolumeStepSettings.steps(this));
        writer.println("accessibility connected=" + FineVolumeAccessibilityService.isConnected());
        writer.println(adjustmentController == null ? "monitor not running"
                : adjustmentController.diagnostics());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.background_channel_description));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        AudioManager audio = getSystemService(AudioManager.class);
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int percent = VolumeDisplay.percent(current, maximum);
        int steps = VolumeStepSettings.steps(this);
        int level = VolumeDisplay.settingLevel(current,
                audio.getStreamMinVolume(AudioManager.STREAM_MUSIC), maximum, steps);
        Intent activityIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String text = getString(R.string.background_notification_text, percent, steps, level);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_volume_notification)
                .setContentTitle(getString(R.string.background_notification_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setProgress(100, percent, false)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }
}
