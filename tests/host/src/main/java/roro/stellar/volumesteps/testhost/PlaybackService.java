package roro.stellar.volumesteps.testhost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.IBinder;

public final class PlaybackService extends Service {
    private volatile boolean playing;
    private volatile int toneAmplitude = 1;
    private AudioTrack track;
    private MediaSession session;
    private AudioFocusRequest focus;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("playback", "Silent test playback",
                        NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "playback")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Silent test playback").build());
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (track == null) return;
                    if (change < 0) track.pause();
                    else if (playing) track.play();
                }).build();
        if (getSystemService(AudioManager.class).requestAudioFocus(focus)
                != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopSelf();
            return;
        }
        track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(8192)
                .setTransferMode(AudioTrack.MODE_STREAM).build();
        playing = true;
        track.play();
        new Thread(() -> {
            byte[] pcm = new byte[4096];
            double phase = 0;
            double phaseStep = 2 * Math.PI * 440 / 16000;
            while (playing) {
                int amplitude = toneAmplitude;
                for (int offset = 0; offset < pcm.length; offset += 2) {
                    short sample = (short) Math.round(Math.sin(phase) * amplitude);
                    pcm[offset] = (byte) sample;
                    pcm[offset + 1] = (byte) (sample >> 8);
                    phase += phaseStep;
                    if (phase >= 2 * Math.PI) phase -= 2 * Math.PI;
                }
                if (track.write(pcm, 0, pcm.length) < 0) break;
            }
        }, "silent-playback").start();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        toneAmplitude = intent != null && intent.getBooleanExtra("tone", false) ? 2048 : 1;
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
        if (track != null) {
            track.stop();
            track.release();
        }
        if (focus != null) getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
