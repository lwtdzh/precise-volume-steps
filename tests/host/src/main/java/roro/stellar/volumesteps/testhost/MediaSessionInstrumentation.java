package roro.stellar.volumesteps.testhost;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;

/** Controls a selected test player while adopting shell media-control permission. */
public final class MediaSessionInstrumentation extends Instrumentation {
    private Bundle args;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        UiAutomation automation = getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        try {
            automation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);
            Context context = getTargetContext();
            String packageName = args.getString("package");
            String action = args.getString("action", "state");
            MediaController selected = null;
            for (MediaController controller : context.getSystemService(
                    MediaSessionManager.class).getActiveSessions(null)) {
                if (packageName.equals(controller.getPackageName())) {
                    selected = controller;
                    break;
                }
            }
            if (selected == null) throw new IllegalStateException("Session not found");
            MediaController.TransportControls controls = selected.getTransportControls();
            switch (action) {
                case "play":
                    controls.play();
                    break;
                case "pause":
                    controls.pause();
                    break;
                case "seek":
                    controls.seekTo(Long.parseLong(args.getString("position")));
                    break;
                case "state":
                    break;
                default:
                    throw new IllegalArgumentException("Unknown action " + action);
            }
            Thread.sleep(150);
            PlaybackState state = selected.getPlaybackState();
            result.putInt("state", state == null ? -1 : state.getState());
            result.putLong("position", state == null ? -1 : state.getPosition());
            result.putString("package", selected.getPackageName());
        } catch (Exception exception) {
            result.putString("error", exception.toString());
        } finally {
            automation.dropShellPermissionIdentity();
        }
        finish(result.containsKey("error") ? 1 : 0, result);
    }
}
