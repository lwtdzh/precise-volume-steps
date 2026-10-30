package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.os.Build;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

/** Grants only this application's standard permissions, preserving other services. */
final class PrivilegedSetup {
    static final String PACKAGE = "roro.stellar.volumesteps";
    static final String ACCESSIBILITY = PACKAGE + "/" + PACKAGE + ".FineVolumeAccessibilityService";

    static String run(int user) {
        if (user < 0 || user > 21474) throw new IllegalArgumentException("Invalid Android user");
        String u = Integer.toString(user);
        StringBuilder report = new StringBuilder();
        if (Build.VERSION.SDK_INT >= 33)
            grant(report, "通知", "pm", "grant", "--user", u, PACKAGE,
                    "android.permission.POST_NOTIFICATIONS");
        grant(report, "音量面板", "cmd", "appops", "set", "--user", u, PACKAGE,
                "SYSTEM_ALERT_WINDOW", "allow");
        grant(report, "后台运行", "cmd", "appops", "set", "--user", u, PACKAGE,
                "RUN_IN_BACKGROUND", "allow");
        grant(report, "后台启动", "cmd", "appops", "set", "--user", u, PACKAGE,
                "RUN_ANY_IN_BACKGROUND", "allow");
        grant(report, "电池优化", "cmd", "deviceidle", "whitelist", "+" + PACKAGE);
        Result existing = command("settings", "--user", u, "get", "secure",
                "enabled_accessibility_services");
        if (existing.ok) {
            String merged = mergeAccessibility(existing.text);
            Result write = command("settings", "--user", u, "put", "secure",
                    "enabled_accessibility_services", merged);
            Result enable = command("settings", "--user", u, "put", "secure",
                    "accessibility_enabled", "1");
            Result read = command("settings", "--user", u, "get", "secure",
                    "enabled_accessibility_services");
            boolean confirmed = read.ok && containsOwnService(read.text);
            report.append("无障碍：").append(write.ok && enable.ok && confirmed
                    ? "已写入系统设置，请等待服务连接" : "请到系统设置手动开启").append('\n');
        } else {
            report.append("无障碍：请到系统设置手动开启\n");
        }
        report.append("厂商自启动、后台高耗电限制：请按权限指南检查。\n");
        return report.toString();
    }

    static String mergeAccessibility(String existing) {
        LinkedHashSet<String> services = new LinkedHashSet<>();
        if (existing != null && !"null".equals(existing.trim())) {
            for (String item : existing.trim().split(":")) {
                if (!item.isEmpty()) services.add(item);
            }
        }
        if (!containsOwnService(existing)) services.add(ACCESSIBILITY);
        return String.join(":", services);
    }

    static boolean containsOwnService(String value) {
        ComponentName own = ComponentName.unflattenFromString(ACCESSIBILITY);
        if (value != null)
            for (String item : value.trim().split(":"))
                if (own.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }

    private static void grant(StringBuilder report, String label, String... args) {
        Result result = command(args);
        report.append(label).append(result.ok ? "：已执行\n" : "：需手动授权\n");
    }

    private static Result command(String... args) {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
            java.io.InputStream input = process.getInputStream();
            java.util.concurrent.FutureTask<String> output = new java.util.concurrent.FutureTask<>(() -> {
                try (input; java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > 16384) throw new IOException("Permission output too large");
                        bytes.write(buffer, 0, count);
                    }
                    return new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
                }
            });
            Thread reader = new Thread(output, "permission-command-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, "");
            }
            String text = output.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0)
                android.util.Log.w("FineVolumeSetup", args[0] + " failed: " + text);
            return new Result(process.exitValue() == 0, text);
        } catch (IOException | RuntimeException | InterruptedException
                 | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            android.util.Log.w("FineVolumeSetup", "Fixed permission command failed", exception);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new Result(false, "");
        } finally {
            if (process != null) {
                process.destroy();
                try { process.getInputStream().close(); } catch (IOException ignored) { }
            }
        }
    }

    private record Result(boolean ok, String text) {}
}
