package roro.stellar.volumesteps;

/** Only volume/power events from independently discovered key-only devices. */
oneway interface IVolumeKeyListener {
    void onKey(String node, int code, int value, long eventTime, int maskedOrigin);
    void onStatus(int devices, String error);
}
