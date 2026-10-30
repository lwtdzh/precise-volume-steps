package roro.stellar.volumesteps;

/** Fixed helper permission grant shared by the UI and the transient shell service. */
final class CompatibilityGrantContract {
    static final String HELPER_PACKAGE = "com.vivo.finevolumesteps.helper";
    static final String PERMISSION = "android.permission.DUMP";
    static final String ADB_COMMAND = "adb shell pm grant " + HELPER_PACKAGE + " " + PERMISSION;

    private CompatibilityGrantContract() {}

    static String[] shellCommand() {
        return new String[]{"/system/bin/pm", "grant", HELPER_PACKAGE, PERMISSION};
    }
}
