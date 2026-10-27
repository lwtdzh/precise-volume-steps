package roro.stellar.volumesteps;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deliberately excludes touch, switches, headsets and ordinary keyboards. */
final class RawVolumeKeyParser {
    static final int UP = 115;
    static final int DOWN = 114;
    static final int POWER = 116;
    private static final Pattern NODE = Pattern.compile("add device \\d+: (/dev/input/event\\d+)");
    private static final Pattern OTHER_TYPES = Pattern.compile("\\b(?:ABS|SW|REL)\\s+\\(");
    private static final Pattern KEYS = Pattern.compile("\\b(?:KEY_[A-Z0-9_]+|BTN_[A-Z0-9_]+)\\b");
    private static final Pattern EVENT = Pattern.compile(
            "^\\[\\s*(\\d+\\.\\d+)\\]\\s+EV_KEY\\s+"
                    + "(KEY_VOLUMEUP|KEY_VOLUMEDOWN|KEY_POWER)\\s+(DOWN|UP|REPEAT)\\s*$");

    static List<String> nodes(String capabilities) {
        ArrayList<String> result = new ArrayList<>();
        boolean volume = false;
        for (String block : capabilities.split("(?=add device )")) {
            Matcher node = NODE.matcher(block);
            if (!node.find() || OTHER_TYPES.matcher(block).find()
                    || !(block.contains("KEY_VOLUMEUP") || block.contains("KEY_VOLUMEDOWN")
                            || block.contains("KEY_POWER"))) continue;
            boolean safe = true;
            Matcher keys = KEYS.matcher(block);
            while (keys.find()) {
                String key = keys.group();
                if (!key.equals("KEY_VOLUMEUP") && !key.equals("KEY_VOLUMEDOWN")
                        && !key.equals("KEY_POWER")) safe = false;
            }
            if (safe) {
                result.add(node.group(1));
                volume |= block.contains("KEY_VOLUMEUP") || block.contains("KEY_VOLUMEDOWN");
            }
        }
        return volume ? result : List.of();
    }

    static Event event(String line) {
        Matcher match = EVENT.matcher(line);
        if (!match.matches()) return null;
        int code = switch (match.group(2)) {
            case "KEY_VOLUMEUP" -> UP;
            case "KEY_VOLUMEDOWN" -> DOWN;
            default -> POWER;
        };
        int value = switch (match.group(3)) {
            case "DOWN" -> 1;
            case "REPEAT" -> 2;
            default -> 0;
        };
        return new Event(code, value, (long) (Double.parseDouble(match.group(1)) * 1000));
    }

    record Event(int code, int value, long time) {}
}
