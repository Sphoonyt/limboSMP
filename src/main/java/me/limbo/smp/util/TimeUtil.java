package me.limbo.smp.util;

public final class TimeUtil {
    private TimeUtil() {}

    public static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    public static String seconds(long ms) {
        return String.format("%.1fs", Math.max(0, ms) / 1000.0);
    }
}
