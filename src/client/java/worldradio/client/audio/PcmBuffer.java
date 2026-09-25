package worldradio.client.audio;

/**
 * A bounded ring buffer of mono 16-bit samples between one station's decoder thread and one playing sound. When the
 * reader falls behind, the oldest samples are dropped; when the writer falls behind, the reader gets silence and waits
 * until {@link #PRIME} samples are buffered again, so a short network hiccup is one gap, not a stutter.
 */
public final class PcmBuffer {
    public static final int CAPACITY = StationStream.RATE * 3;
    public static final int PRIME = StationStream.RATE / 2;

    private final short[] ring = new short[CAPACITY];
    private int start;
    private int size;
    private boolean primed;

    public synchronized void write(short[] samples, int count) {
        for (int i = 0; i < count; i++) {
            if (size == CAPACITY) {
                start = (start + 1) % CAPACITY;
                size--;
            }
            ring[(start + size) % CAPACITY] = samples[i];
            size++;
        }
    }

    /** Fills {@code out} completely: buffered samples first, silence for the rest. Returns the real sample count. */
    public synchronized int read(short[] out) {
        if (!primed && size >= PRIME) primed = true;
        int n = primed ? Math.min(size, out.length) : 0;
        for (int i = 0; i < n; i++) out[i] = ring[(start + i) % CAPACITY];
        start = (start + n) % CAPACITY;
        size -= n;
        for (int i = n; i < out.length; i++) out[i] = 0;
        if (primed && n < out.length) primed = false;
        return n;
    }

    public synchronized int available() {
        return size;
    }

    public synchronized void clear() {
        start = 0;
        size = 0;
        primed = false;
    }
}
