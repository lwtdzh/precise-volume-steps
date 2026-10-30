package roro.stellar.volumesteps.compatibility;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict parser for the two built-in key devices in InputManager's EventHub dump. */
final class InputDumpKeyState {
    static final String PMIC = "mtk-pmic-keys";
    static final String GPIO = "gpio-keys";
    static final String POWER = "KEY_POWER";
    static final String DOWN = "KEY_VOLUMEDOWN";
    static final String UP = "KEY_VOLUMEUP";

    private InputDumpKeyState() {}

    static Map<String, Set<String>> parse(String dump) {
        int start = dump.indexOf("Event Hub State:");
        int end = dump.indexOf("Input Reader State", start);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Missing EventHub section");
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String device : dump.substring(start, end).split("(?m)^    (?=\\d+: )")) {
            String first = device.split("\\n", 2)[0];
            String name = first.replaceFirst("^\\d+: ", "");
            if (!PMIC.equals(name) && !GPIO.equals(name)) continue;
            if (!device.contains("\n      Enabled: true\n")
                    || !device.contains("\n      Path: /dev/input/event")
                    || !device.contains("\n      SysfsDevicePath: /sys/devices/platform/")) {
                throw new IllegalArgumentException("Unverified physical device: " + name);
            }
            String marker = "\n      KeyState (pressed): ";
            int key = device.indexOf(marker);
            if (key < 0) throw new IllegalArgumentException("Missing key state: " + name);
            int lineEnd = device.indexOf('\n', key + marker.length());
            String value = device.substring(key + marker.length(),
                    lineEnd < 0 ? device.length() : lineEnd).trim();
            Set<String> pressed = new LinkedHashSet<>();
            if (!"<none>".equals(value)) {
                for (String item : value.split(",\\s*")) {
                    if (!allowed(name, item)) {
                        throw new IllegalArgumentException("Unexpected key on " + name);
                    }
                    pressed.add(item);
                }
            }
            if (result.put(name, Set.copyOf(pressed)) != null) {
                throw new IllegalArgumentException("Duplicate physical device: " + name);
            }
        }
        if (result.size() != 2 || !result.containsKey(PMIC) || !result.containsKey(GPIO)) {
            throw new IllegalArgumentException("Physical device set unavailable");
        }
        return Map.copyOf(result);
    }

    static int code(String key) {
        return switch (key) {
            case DOWN -> 114;
            case UP -> 115;
            case POWER -> 116;
            default -> throw new IllegalArgumentException("Unsupported key");
        };
    }

    static List<Edge> changes(Map<String, Set<String>> oldState,
                              Map<String, Set<String>> newState,
                              Set<String> reported,
                              long observedAfter, long observedAt) {
        ArrayList<Edge> result = new ArrayList<>();
        for (String device : new String[] {PMIC, GPIO}) {
            for (String key : oldState.get(device)) {
                String identity = device + ":" + key;
                if (!newState.get(device).contains(key) && reported.remove(identity)) {
                    result.add(new Edge(device, code(key), 0, observedAfter, observedAt));
                }
            }
            for (String key : newState.get(device)) {
                if (!oldState.get(device).contains(key)) {
                    reported.add(device + ":" + key);
                    result.add(new Edge(device, code(key), 1, observedAfter, observedAt));
                }
            }
        }
        return List.copyOf(result);
    }

    static List<Edge> initial(Map<String, Set<String>> state, Set<String> reported,
                              long observedAfter, long observedAt) {
        ArrayList<Edge> result = new ArrayList<>();
        for (String device : new String[] {PMIC, GPIO}) {
            for (String key : state.get(device)) {
                reported.add(device + ":" + key);
                if (POWER.equals(key)) {
                    result.add(new Edge(device, code(key), 1, observedAfter, observedAt));
                }
            }
        }
        return List.copyOf(result);
    }

    static List<Edge> releases(Set<String> reported, long observedAt) {
        ArrayList<Edge> result = new ArrayList<>();
        for (String value : new ArrayList<>(reported)) {
            int split = value.indexOf(':');
            result.add(new Edge(value.substring(0, split),
                    code(value.substring(split + 1)), 0, observedAt, observedAt));
        }
        reported.clear();
        return List.copyOf(result);
    }

    private static boolean allowed(String device, String key) {
        return PMIC.equals(device) ? POWER.equals(key) || DOWN.equals(key) : UP.equals(key);
    }

    record Edge(String device, int code, int value, long observedAfter, long observedAt) {}
}
