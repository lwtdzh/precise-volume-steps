package roro.stellar.volumesteps.compatibility;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real helper-UID DUMP capability and physical mapper-state probe; no audio writes. */
public final class InputDumpInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            Context helper = getTargetContext();
            if (Process.myUid() != helper.getApplicationInfo().uid
                    || !"com.vivo.finevolumesteps.helper".equals(helper.getPackageName())) {
                throw new IllegalStateException("Must run in actual helper UID");
            }
            boolean granted = helper.checkSelfPermission("android.permission.DUMP")
                    == PackageManager.PERMISSION_GRANTED;
            if (!granted) throw new SecurityException("DUMP not granted to actual helper");
            int duration = Integer.parseInt(arguments.getString("seconds", "0"));
            if (duration < 0 || duration > 600) throw new IllegalArgumentException("Duration");
            IBinder input = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "input");
            if (input == null) throw new IllegalStateException("No input Binder");
            File directory = new File(helper.getExternalFilesDir(null),
                    "input-dump-" + System.currentTimeMillis());
            if (!directory.mkdirs()) throw new IllegalStateException("Cannot create evidence directory");
            result.putString("path", directory.getAbsolutePath());
            String first = dump(input, reader);
            if (!first.contains("INPUT MANAGER") || first.contains("Permission Denial")) {
                throw new SecurityException("Input dump unavailable: " + first);
            }
            try (FileOutputStream file = new FileOutputStream(new File(directory, "first-input.txt"))) {
                file.write(first.getBytes(StandardCharsets.UTF_8));
            }
            List<String> physical = physicalMapperStates(first);
            if (physical.size() != 2) throw new IllegalStateException("Physical mapper count: " + physical.size());
            List<String> hub = physicalHubStates(first);
            if (hub.size() != 2) throw new IllegalStateException("Physical EventHub count: " + hub.size());
            long began = SystemClock.uptimeMillis();
            int samples = 0;
            long maximum = 0;
            long sum = 0;
            try (PrintWriter file = new PrintWriter(new File(directory, "states.ndjson"),
                    StandardCharsets.UTF_8)) {
                JSONObject ready = new JSONObject().put("kind", "ready")
                        .put("uid", Process.myUid()).put("dump_granted", granted)
                        .put("input", "read-only EventHub scan state and InputReader mapper state")
                        .put("uptime_ms", began).put("mapper_states", new JSONArray(physical))
                        .put("hub_states", new JSONArray(hub));
                file.println(ready);
                file.flush();
                new File(directory, "ready").createNewFile();
                do {
                    long before = SystemClock.uptimeMillis();
                    String text = dump(input, reader);
                    long after = SystemClock.uptimeMillis();
                    long cost = after - before;
                    file.println(new JSONObject().put("kind", "sample")
                            .put("before_ms", before).put("after_ms", after)
                            .put("dump_ms", cost)
                            .put("mapper_states", new JSONArray(physicalMapperStates(text)))
                            .put("hub_states", new JSONArray(physicalHubStates(text))));
                    file.flush();
                    samples++;
                    maximum = Math.max(maximum, cost);
                    sum += cost;
                    if (new File(directory, "stop").exists()) break;
                    SystemClock.sleep(Math.max(1, 25 - cost));
                } while (SystemClock.uptimeMillis() - began < duration * 1000L);
                file.println(new JSONObject().put("kind", "complete").put("samples", samples));
            }
            result.putInt("uid", Process.myUid());
            result.putInt("samples", samples);
            result.putLong("maximum_dump_ms", maximum);
            result.putLong("mean_dump_ms", sum / Math.max(1, samples));
            new File(directory, "complete").createNewFile();
        } catch (Exception error) {
            result.putString("error", error.toString());
        } finally {
            reader.shutdownNow();
        }
        finish(result.containsKey("error") ? 1 : 0, result);
    }

    private String dump(IBinder service, ExecutorService reader) throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        Future<byte[]> read = reader.submit(() -> {
            try (ParcelFileDescriptor.AutoCloseInputStream input =
                         new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 2 * 1024 * 1024) {
                        throw new IllegalStateException("Oversized dump");
                    }
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            }
        });
        try {
            service.dumpAsync(pipe[1].getFileDescriptor(), new String[0]);
            pipe[1].close();
            return new String(read.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        } finally {
            pipe[0].close();
            pipe[1].close();
            read.cancel(true);
        }
    }

    private List<String> physicalMapperStates(String dump) {
        List<String> states = new ArrayList<>();
        for (String device : dump.split("(?m)^  Device ")) {
            String name = device.split("\\n", 2)[0];
            boolean up = name.contains(": gpio-keys");
            boolean down = name.contains(": mtk-pmic-keys");
            if ((!up && !down) || !device.contains("IsExternal: false")
                    || !device.contains("SysfsRootPath:     /sys/devices/platform/")) continue;
            int start = device.indexOf("KeyDowns:");
            int end = device.indexOf("MetaState:", start);
            if (start < 0 || end < start) throw new IllegalStateException("Missing mapper key state");
            states.add(name.trim() + "\n" + device.substring(start, end).trim());
        }
        return states;
    }

    private List<String> physicalHubStates(String dump) {
        int start = dump.indexOf("Event Hub State:");
        int end = dump.indexOf("Input Reader State", start);
        if (start < 0 || end < start) throw new IllegalStateException("Missing EventHub section");
        List<String> states = new ArrayList<>();
        for (String device : dump.substring(start, end).split("(?m)^    (?=\\d+: )")) {
            String name = device.split("\\n", 2)[0];
            if (!name.matches("\\d+: (gpio-keys|mtk-pmic-keys)")) continue;
            if (!device.contains("\n      Enabled: true\n")
                    || !device.contains("\n      SysfsDevicePath: /sys/devices/platform/")
                    || !device.contains("\n      Path: /dev/input/event")) {
                throw new IllegalStateException("Unverified physical EventHub: " + name);
            }
            int key = device.indexOf("\n      KeyState (pressed): ");
            if (key < 0) throw new IllegalStateException("Missing scan state: " + name);
            int nextLine = device.indexOf('\n', key + 1);
            if (nextLine < 0) nextLine = device.length();
            // Preserve the unparsed firmware text. This probe does not infer a
            // direction from mapper counts or invent original KeyEvent times.
            states.add(name + "\n" + device.substring(key + 1, nextLine).trim());
        }
        return states;
    }
}
