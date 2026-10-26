package roro.stellar.volumesteps.testhost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.IBinder;

public final class PlaybackService extends Service {
    private volatile boolean playing;
    private AudioTrack track;
    private MediaSession session;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("playback", "Silent test playback",
                        NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "playback")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Silent test playback").build());
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(8192)
                .setTransferMode(AudioTrack.MODE_STREAM).build();
        playing = true;
        track.play();
        new Thread(() -> {
            byte[] silence = new byte[4096];
            while (playing) {
                if (track.write(silence, 0, silence.length) < 0) break;
            }
        }, "silent-playback").start();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && intent.getBooleanExtra("media_session", false) && session == null) {
            session = new MediaSession(this, "Volume playback regression");
            session.setPlaybackToLocal(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
            session.setActive(true);
        }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        playing = false;
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        track.stop();
        track.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
