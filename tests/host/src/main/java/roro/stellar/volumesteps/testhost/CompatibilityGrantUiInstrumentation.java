package roro.stellar.volumesteps.testhost;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises the missing-DUMP method chooser without enabling a vendor backend. */
public final class CompatibilityGrantUiInstrumentation extends Instrumentation {
    private static final String MAIN = "roro.stellar.volumesteps";
    private static final String HELPER = "com.vivo.finevolumesteps.helper";
    private static final String DUMP = "android.permission.DUMP";
    private static final String COMMAND =
            "adb shell pm grant com.vivo.finevolumesteps.helper android.permission.DUMP";

    private final List<JSONObject> tests = new ArrayList<>();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity activity = null;
        try {
            Context target = getTargetContext();
            target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", Context.MODE_PRIVATE)
                    .edit().putBoolean("permission_guide_shown_v1", true).commit();
            activity = startActivitySync(new Intent()
                    .setClassName(MAIN, MAIN + ".MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            UiAutomation automation = getUiAutomation(
                    UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

            showMethods(activity);
            AccessibilityNodeInfo chooser = waitForText(automation, "助手熄屏授权");
            check("missing grant shows method chooser", chooser != null);
            check("chooser prints complete USB ADB command",
                    containsText(automation.getRootInActiveWindow(), COMMAND));
            check("chooser offers command copy",
                    findText(automation.getRootInActiveWindow(), "复制 ADB 授权命令") != null);
            check("chooser offers Shizuku grant",
                    findText(automation.getRootInActiveWindow(), "使用 Shizuku 授权助手") != null);

            click(automation, "复制 ADB 授权命令");
            ClipData clip = target.getSystemService(ClipboardManager.class).getPrimaryClip();
            check("copy action writes exact fixed command",
                    clip != null && clip.getItemCount() == 1
                            && COMMAND.contentEquals(clip.getItemAt(0).coerceToText(target)));

            click(automation, "使用 Shizuku 授权助手");
            AccessibilityNodeInfo active = null;
            boolean openShizuku = false;
            for (int attempt = 0; attempt < 20; attempt++) {
                active = automation.getRootInActiveWindow();
                openShizuku = containsTextIgnoreCase(active, "打开 Shizuku");
                if (openShizuku) break;
                Thread.sleep(100);
            }
            boolean permissionDialog = containsText(active, "Allow all the time")
                    || containsText(active, "允许");
            check("Shizuku action opens recovery or authorization",
                    openShizuku || permissionDialog);
            if (!openShizuku && !permissionDialog) {
                result.putString("active_ui", collectText(active));
            }
            check("method inspection leaves helper DUMP revoked",
                    target.getPackageManager().checkPermission(DUMP, HELPER)
                            != PackageManager.PERMISSION_GRANTED);

            result.putString("tests", new JSONArray(tests).toString());
            finish(allPassed() ? 0 : 1, result);
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            result.putString("tests", new JSONArray(tests).toString());
            finish(1, result);
        } finally {
            if (activity != null) activity.finish();
        }
    }

    private void showMethods(Activity activity) throws Exception {
        Method method = activity.getClass().getDeclaredMethod("showCompatibilityGrantMethods");
        method.setAccessible(true);
        runOnMainSync(() -> {
            try {
                method.invoke(activity);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private AccessibilityNodeInfo waitForText(UiAutomation automation, String text)
            throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo found = findText(automation.getRootInActiveWindow(), text);
            if (found != null) return found;
            Thread.sleep(100);
        }
        return null;
    }

    private void click(UiAutomation automation, String text) throws Exception {
        AccessibilityNodeInfo node = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo label = findExactText(
                    automation.getRootInActiveWindow(), text);
            while (label != null && !label.isClickable()) label = label.getParent();
            node = label;
            if (node != null) break;
            Thread.sleep(100);
        }
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            throw new IllegalStateException("Cannot click " + text);
        }
    }

    private boolean containsText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        CharSequence value = node.getText();
        if (value != null && value.toString().contains(text)) return true;
        for (int index = 0; index < node.getChildCount(); index++) {
            if (containsText(node.getChild(index), text)) return true;
        }
        return false;
    }

    private boolean containsTextIgnoreCase(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        CharSequence value = node.getText();
        if (value != null && value.toString().toLowerCase(Locale.ROOT)
                .contains(text.toLowerCase(Locale.ROOT))) return true;
        for (int index = 0; index < node.getChildCount(); index++) {
            if (containsTextIgnoreCase(node.getChild(index), text)) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence value = node.getText();
        if (value != null && value.toString().contains(text)) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findText(node.getChild(index), text);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findExactText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence value = node.getText();
        if (value != null && value.toString().equals(text)) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findExactText(node.getChild(index), text);
            if (found != null) return found;
        }
        return null;
    }

    private String collectText(AccessibilityNodeInfo node) {
        if (node == null) return "<no active window>";
        StringBuilder result = new StringBuilder();
        collectText(node, result);
        return result.toString();
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder result) {
        CharSequence value = node.getText();
        if (value != null && value.length() > 0) {
            if (result.length() > 0) result.append(" | ");
            result.append(value);
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) collectText(child, result);
        }
    }

    private void check(String name, boolean passed) throws Exception {
        tests.add(new JSONObject().put("test", name).put("pass", passed));
    }

    private boolean allPassed() {
        for (JSONObject test : tests) {
            if (!test.optBoolean("pass")) return false;
        }
        return !tests.isEmpty();
    }
}
