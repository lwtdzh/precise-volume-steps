package roro.stellar.volumesteps;

enum OperatingMode {
    ASSISTANT, SHIZUKU, ROOT;

    static OperatingMode stored(String value, boolean legacyShizuku) {
        if (value != null) {
            try { return valueOf(value); } catch (IllegalArgumentException ignored) { }
        }
        return legacyShizuku ? SHIZUKU : ASSISTANT;
    }
}
