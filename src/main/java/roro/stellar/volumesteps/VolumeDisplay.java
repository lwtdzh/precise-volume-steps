package roro.stellar.volumesteps;

final class VolumeDisplay {
    private VolumeDisplay() {}

    static int percent(int current, int maximum) {
        if (maximum <= 0) {
            return 0;
        }
        int bounded = Math.max(0, Math.min(current, maximum));
        return (int) Math.round(bounded * 100.0 / maximum);
    }

    static int settingLevel(int current, int minimum, int maximum, int steps) {
        if (maximum <= minimum || steps <= 0) {
            return 0;
        }
        int bounded = Math.max(minimum, Math.min(current, maximum));
        return (int) Math.round((bounded - minimum) * (double) steps / (maximum - minimum));
    }
}
