package worldradio.signal;

/**
 * Loudness around a sound source with range R: from 1.0 at the block down to {@link #EDGE} (0.2) at R, then down to
 * silence over the tail, a quarter of R but at least {@link #TAIL} blocks. With R/4 the fade keeps the slope it had
 * inside the range, so a large range ends as smoothly as it started instead of dropping 0.2 to 0 in a few steps.
 */
public final class VolumeCurve {
    public static final double TAIL = 10.0;
    public static final double HEARING_TAIL = 2.0;
    public static final double EDGE = 0.2;

    private VolumeCurve() {
    }

    public static double volume(double distance, double range) {
        return volume(distance, range, tail(range));
    }

    /**
     * Loudness around a radio with hearing range R: the same curve, but the fade past R is only a quarter of R or
     * {@link #HEARING_TAIL} blocks, so a radio without speakers (R = 4) is a room's worth of sound and not 14 blocks.
     */
    public static double hearing(double distance, double range) {
        return volume(distance, range, Math.max(HEARING_TAIL, range / 4.0));
    }

    private static double volume(double distance, double range, double tail) {
        if (range <= 0 || distance < 0) return distance < 0 ? 1.0 : 0.0;
        if (distance <= range) return 1.0 - (1.0 - EDGE) * distance / range;
        if (distance <= range + tail) return EDGE * (1.0 - (distance - range) / tail);
        return 0.0;
    }

    /** How well a signal with range R arrives: full inside the range, then down to nothing over the tail. */
    public static double reception(double distance, double range) {
        if (range <= 0) return 0.0;
        if (distance <= range) return 1.0;
        double tail = tail(range);
        return distance <= range + tail ? 1.0 - (distance - range) / tail : 0.0;
    }

    /** Length of the fade past the range. */
    public static double tail(double range) {
        return Math.max(TAIL, range / 4.0);
    }

    /** How far away the source can still be heard at all. */
    public static double audibleRange(double range) {
        return range + tail(range);
    }
}
