package worldradio.signal;

import java.util.function.IntPredicate;

/**
 * Antenna columns: radios and amplifiers reach {@code baseRange} blocks, and every antenna block stacked straight on top
 * adds {@code step} blocks, up to {@code maxAntenna} blocks. The model shows the result as a level 0..3.
 */
public final class Antenna {
    public static final int BASE_RANGE = 32;
    public static final int STEP = 32;
    public static final int MAX_ANTENNA = 32;

    private Antenna() {
    }

    /** Range in blocks for {@code count} antenna blocks; blocks past {@code maxAntenna} add nothing. */
    public static int range(int count, int baseRange, int step, int maxAntenna) {
        return baseRange + Math.min(Math.max(count, 0), maxAntenna) * step;
    }

    /** {@link #range(int, int, int, int)} with the default config. */
    public static int range(int count) {
        return range(count, BASE_RANGE, STEP, MAX_ANTENNA);
    }

    /**
     * Blockstate level: 0 = nothing to send (a radio without a station, an amplifier without a signal), 1 = base range,
     * 2 = one to three antenna blocks, 3 = four or more.
     */
    public static int level(boolean active, int count) {
        if (!active) return 0;
        if (count <= 0) return 1;
        return count <= 3 ? 2 : 3;
    }

    /**
     * Counts the column from one block above upwards: {@code isAntenna} gets the height above the block (1, 2, ...) and
     * the count ends at the first height where it says no, or after {@code limit} blocks.
     */
    public static int count(IntPredicate isAntenna, int limit) {
        int count = 0;
        while (count < limit && isAntenna.test(count + 1)) count++;
        return count;
    }
}
