package roro.stellar.volumesteps;

/** Snapshot used by the setup guide. Helper input is optional and screen-off only. */
final class PermissionGuideState {
    final boolean accessibility;
    final boolean notification;
    final boolean battery;
    final boolean overlay;
    final boolean helperInstalled;
    final boolean helperOffDisplayGranted;

    PermissionGuideState(
            boolean accessibility,
            boolean notification,
            boolean battery,
            boolean overlay,
            boolean helperInstalled,
            boolean helperOffDisplayGranted
    ) {
        this.accessibility = accessibility;
        this.notification = notification;
        this.battery = battery;
        this.overlay = overlay;
        this.helperInstalled = helperInstalled;
        this.helperOffDisplayGranted = helperOffDisplayGranted;
    }

    boolean coreReady() {
        return accessibility && notification && battery && overlay;
    }

    boolean helperOffDisplayReady() {
        return helperInstalled && helperOffDisplayGranted;
    }
}
