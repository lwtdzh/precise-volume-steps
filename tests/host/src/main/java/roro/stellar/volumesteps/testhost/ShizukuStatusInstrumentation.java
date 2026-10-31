package roro.stellar.volumesteps.testhost;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real Shizuku authorization readback with the volume keeper deliberately off. */
public final class ShizukuStatusInstrumentation extends Instrumentation {
    private static final String MAIN = "roro.stellar.volumesteps";
    private final List<JSONObject> tests = new ArrayList<>();
    private String expected;
    private int expectedUid;
    private SharedPreferences prefs;
    private boolean enabled, legacy, guide;
    private String mode;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        expected = arguments.getString("expected", "authorized");
        expectedUid = Integer.parseInt(arguments.getString("expected_uid", "2000"));
        if (!"goldfish".equals(android.os.Build.HARDWARE)
                && !"ranchu".equals(android.os.Build.HARDWARE)) {
            Bundle result = new Bundle();
            result.putString("error", "Use a disposable emulator");
            finish(1, result);
            return;
        }
        prefs = getTargetContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("volume_step_settings", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean("enabled", false);
        mode = prefs.getString("operating_mode", null);
        legacy = prefs.getBoolean("shizuku_enabled", false);
        guide = prefs.getBoolean("permission_guide_shown_v1", false);
        // Instrumentation is created before Application.onCreate. Disable here
        // so startup cannot enqueue a foreground service that this test cancels.
        prefs.edit().putBoolean("enabled", false).putString("operating_mode", "SHIZUKU")
                .putBoolean("shizuku_enabled", true)
                .putBoolean("permission_guide_shown_v1", true).commit();
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity activity = null;
        boolean passed = false;
        try {
            getTargetContext().stopService(new Intent().setClassName(MAIN,
                    MAIN + ".VolumeKeeperService"));
            activity = startActivitySync(new Intent().setClassName(MAIN, MAIN + ".MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Class<?> shizuku = Class.forName("rikka.shizuku.Shizuku", true,
                    getTargetContext().getClassLoader());
            Method ping = shizuku.getMethod("pingBinder");
            if (!"stopped".equals(expected)) {
                long deadline = System.currentTimeMillis() + 5000;
                while (!(boolean) ping.invoke(null) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }
            }
            boolean running = (boolean) ping.invoke(null);
            check("server readback matches requested fixture", running != "stopped".equals(expected));
            if (running) {
                int permission = (int) shizuku.getMethod("checkSelfPermission").invoke(null);
                check("real server permission matches requested fixture",
                        (permission == android.content.pm.PackageManager.PERMISSION_GRANTED)
                                == "authorized".equals(expected));
                check("server UID matches requested root or ADB identity",
                        (int) shizuku.getMethod("getUid").invoke(null) == expectedUid);
            }
            Activity main = activity;
            String[] text = {""};
            for (int attempt = 0; attempt < 30; attempt++) {
                runOnMainSync(() -> {
                    TextView status = main.findViewById(getTargetContext().getResources()
                            .getIdentifier("shizuku_status", "id", MAIN));
                    text[0] = status.getText().toString();
                });
                if ("authorized".equals(expected) ? text[0].contains("Shizuku 已授权")
                        : "stopped".equals(expected) ? text[0].contains("Shizuku 未运行")
                        : text[0].contains("等待 Shizuku 授权")) break;
                Thread.sleep(100);
            }
            result.putString("status", text[0]);
            if ("authorized".equals(expected)) {
                check("grant is visible while correction is off", text[0].contains("Shizuku 已授权"));
                check("grant does not imply a connected volume backend",
                        text[0].contains("音量修正未开启") && !text[0].contains("已连接"));
            } else {
                check("missing grant or server is shown while keeper is off",
                        text[0].contains("stopped".equals(expected)
                                ? "Shizuku 未运行" : "等待 Shizuku 授权"));
                check("unavailable authorization is never labeled granted",
                        !text[0].contains("Shizuku 已授权"));
            }
            Class<?> keeper = Class.forName(MAIN + ".VolumeKeeperService", true,
                    getTargetContext().getClassLoader());
            Method controller = keeper.getDeclaredMethod("controller");
            controller.setAccessible(true);
            check("status inspection leaves volume keeper off", controller.invoke(null) == null);
            passed = tests.stream().allMatch(test -> test.optBoolean("pass"));
        } catch (Exception exception) {
            result.putString("error", exception.toString());
        } finally {
            if (activity != null) activity.finish();
            SharedPreferences.Editor restore = prefs.edit().putBoolean("enabled", enabled)
                    .putBoolean("shizuku_enabled", legacy).putBoolean("permission_guide_shown_v1", guide);
            if (mode == null) restore.remove("operating_mode");
            else restore.putString("operating_mode", mode);
            restore.commit();
            if (enabled) getTargetContext().startForegroundService(
                    new Intent().setClassName(MAIN, MAIN + ".VolumeKeeperService"));
            result.putString("tests", new JSONArray(tests).toString());
            finish(passed ? 0 : 1, result);
        }
    }

    private void check(String name, boolean condition) throws Exception {
        JSONObject test = new JSONObject();
        test.put("test", name);
        test.put("pass", condition);
        tests.add(test);
    }
}
