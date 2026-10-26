package roro.stellar.volumesteps;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class FineVolumeAccessibilityService extends AccessibilityService {
    private static volatile FineVolumeAccessibilityService connectedService;

    static boolean isConnected() {
        return connectedService != null;
    }

    static FineVolumeAccessibilityService connectedService() {
        return connectedService;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = this;
        VolumeKeeperService.start(this);
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        if (controller != null) {
            controller.accessibilityChanged();
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        if (controller != null) {
            controller.onKey(event);
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (connectedService == this) {
            connectedService = null;
        }
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        if (controller != null) {
            controller.accessibilityChanged();
        }
        super.onDestroy();
    }
}
