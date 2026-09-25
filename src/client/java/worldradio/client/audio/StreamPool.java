package worldradio.client.audio;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * One {@link StationStream} per station address, shared by every sound that plays it. A stream nobody listens to is
 * closed after a grace period, so walking from a radio to its amplifier (or retuning back) does not reconnect.
 */
public final class StreamPool {
    private static final long GRACE_MS = 10_000;
    private static final Map<String, StationStream> STREAMS = new HashMap<>();
    private static final Map<String, Long> IDLE_SINCE = new HashMap<>();

    private StreamPool() {
    }

    public static synchronized PcmBuffer acquire(String url) {
        StationStream stream = STREAMS.computeIfAbsent(url, u -> {
            StationStream s = new StationStream(u);
            s.start();
            return s;
        });
        IDLE_SINCE.remove(url);
        PcmBuffer buffer = new PcmBuffer();
        stream.addConsumer(buffer);
        return buffer;
    }

    public static synchronized void release(String url, PcmBuffer buffer) {
        StationStream stream = STREAMS.get(url);
        if (stream == null) return;
        stream.removeConsumer(buffer);
        if (stream.consumerCount() == 0) IDLE_SINCE.putIfAbsent(url, System.currentTimeMillis());
    }

    /** The running stream for an address, if any (for the station screen's status line). */
    public static synchronized StationStream find(String url) {
        return STREAMS.get(url);
    }

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = IDLE_SINCE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            StationStream stream = STREAMS.get(e.getKey());
            if (stream == null || stream.consumerCount() > 0) {
                it.remove();
            } else if (now - e.getValue() > GRACE_MS) {
                stream.close();
                STREAMS.remove(e.getKey());
                it.remove();
            }
        }
    }

    public static synchronized void closeAll() {
        STREAMS.values().forEach(StationStream::close);
        STREAMS.clear();
        IDLE_SINCE.clear();
    }
}
