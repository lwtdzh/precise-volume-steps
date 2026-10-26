package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded public AudioManager checks with foreground/background comparison. */
public final class OrdinaryModeProbeInstrumentation extends Instrumentation {
    private Context target;
    private AudioManager audio;
    private UiAutomation automation;
    private final JSONArray observations = new JSONArray();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int saved = -1;
        try {
            target = getTargetContext().getApplicationContext();
            audio = target.getSystemService(AudioManager.class);
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            saved = audio.getStreamVolume(3);
            result.putString("op_package", target.getOpPackageName());
            foreground();
            set(saved);
            int requested = Math.max(audio.getStreamMinVolume(3), saved - 1);
            set(requested);
            record("foreground setStreamVolume", requested);
            set(saved);
            home();
            for (int flags : new int[]{0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE,
                    AudioManager.FLAG_SHOW_UI}) {
                runOnMainSync(() -> audio.setStreamVolume(3, requested, flags));
                Thread.sleep(150);
                record("background setStreamVolume flags=" + flags, requested);
                if (audio.getStreamVolume(3) != saved) {
                    foreground();
                    set(saved);
                    home();
                }
            }
            runOnMainSync(() -> audio.adjustStreamVolume(3, AudioManager.ADJUST_LOWER, 0));
            Thread.sleep(150);
            record("background adjustStreamVolume", -1);
            if (audio.getStreamVolume(3) != saved) {
                foreground();
                set(saved);
                home();
            }
            runOnMainSync(() -> audio.adjustSuggestedStreamVolume(
                    AudioManager.ADJUST_LOWER, 3, 0));
            Thread.sleep(150);
            record("background adjustSuggestedStreamVolume", -1);
        } catch (Exception exception) {
            result.putString("error", exception.toString());
        } finally {
            if (saved >= 0) {
                try {
                    foreground();
                    set(saved);
                    if (audio.getStreamVolume(3) != saved) {
                        result.putString("restore_error", "actual=" + audio.getStreamVolume(3));
                    }
                    home();
                } catch (Exception exception) {
                    result.putString("restore_error", exception.toString());
                }
            }
        }
        result.putString("observations", observations.toString());
        result.putString("input", "public AudioManager calls; no Shizuku or root");
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }

    private void foreground() throws Exception {
        runOnMainSync(() -> target.startActivity(new Intent().setClassName(
                target.getPackageName(), target.getPackageName() + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        Thread.sleep(500);
        // An actual tap on the app title, to compare Vivo's interaction policy.
        shell("input tap 540 135");
    }

    private void home() throws Exception {
        runOnMainSync(() -> target.startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        Thread.sleep(500);
    }

    private void set(int value) throws Exception {
        shell("input tap 540 135");
        runOnMainSync(() -> audio.setStreamVolume(3, value, 0));
        Thread.sleep(150);
    }

    private void record(String action, int requested) throws Exception {
        observations.put(new JSONObject().put("action", action).put("requested", requested)
                .put("actual", audio.getStreamVolume(3)));
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
