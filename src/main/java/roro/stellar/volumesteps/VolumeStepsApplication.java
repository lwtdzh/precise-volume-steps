package roro.stellar.volumesteps;

import android.app.Application;

public final class VolumeStepsApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        VolumeKeeperService.start(this);
    }
}
