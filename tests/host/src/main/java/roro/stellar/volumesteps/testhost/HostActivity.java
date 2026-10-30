package roro.stellar.volumesteps.testhost;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

public final class HostActivity extends Activity {
    private boolean consume;
    private int nativeStride = 1;
    private boolean playerKeys;
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setTextSize(24);
        setContentView(status);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        configure(getIntent());
    }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        configure(intent);
    }
    private void configure(Intent intent) {
        setVolumeControlStream(intent.getIntExtra("stream", AudioManager.STREAM_MUSIC));
        consume = intent.getBooleanExtra("consume", false);
        nativeStride = intent.getIntExtra("native_stride", 1);
        playerKeys = nativeStride > 1 || intent.getBooleanExtra("player_keys", false);
        status.setText(consume ? "Other app consumes volume keys" : "Silent media playback");
        if (intent.getBooleanExtra("play", true)) {
            startForegroundService(new Intent(this, PlaybackService.class)
                    .putExtra("media_session", intent.getBooleanExtra("media_session", false))
                    .putExtra("tone", intent.getBooleanExtra("tone", false)));
        } else {
            stopService(new Intent(this, PlaybackService.class));
        }
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (consume && (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            Log.i("VolumeTestHost", "consumed:" + keyCode + ":" + event.getRepeatCount());
            return true;
        }
        if (playerKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            AudioManager audio = getSystemService(AudioManager.class);
            int direction = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1;
            int before = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                    Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                            before + direction * nativeStride));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            Log.i("VolumeTestHost", "player stride=" + nativeStride + " " + before + " -> " + target);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((consume || playerKeys) && (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true;
        return super.onKeyUp(keyCode, event);
    }
}
