package worldradio.signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which stations reach which amplifiers and radios. A transmitter reaches every block within its own range; an
 * amplifier passes everything it receives on to every block within the amplifier's range, over any number of hops. A
 * radio (the receiver block) is an {@link Amplifier} with range 0: it receives and passes nothing on. A transmitter
 * without a station or with range 0 sends nothing.
 */
public final class SignalGraph {
    public record Radio(long id, int x, int y, int z, int range, String url, String name) {
    }

    public record Amplifier(long id, int x, int y, int z, int range) {
    }

    private SignalGraph() {
    }

    /** Signals per amplifier id; every amplifier has an entry, possibly empty. */
    public static Map<Long, List<Signal>> compute(List<Radio> radios, List<Amplifier> amplifiers) {
        Map<Long, List<Signal>> result = new HashMap<>();
        for (Amplifier amp : amplifiers) result.put(amp.id(), new ArrayList<>());
        int n = amplifiers.size();
        for (Radio radio : radios) {
            if (radio.range() <= 0 || radio.url() == null || radio.url().isEmpty()) continue;
            // Breadth first, so every amplifier keeps the shortest chain of hops from this radio.
            int[] hops = new int[n];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int i = 0; i < n; i++) {
                if (distance(radio.x(), radio.y(), radio.z(), amplifiers.get(i)) <= radio.range()) {
                    hops[i] = 1;
                    if (amplifiers.get(i).range() > 0) queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                int from = queue.poll();
                Amplifier relay = amplifiers.get(from);
                for (int i = 0; i < n; i++) {
                    if (hops[i] != 0) continue;
                    if (distance(relay.x(), relay.y(), relay.z(), amplifiers.get(i)) <= relay.range()) {
                        hops[i] = hops[from] + 1;
                        if (amplifiers.get(i).range() > 0) queue.add(i);
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (hops[i] == 0) continue;
                Amplifier amp = amplifiers.get(i);
                result.get(amp.id()).add(new Signal(radio.id(), radio.x(), radio.y(), radio.z(), radio.url(),
                        radio.name(), distance(radio.x(), radio.y(), radio.z(), amp), hops[i]));
            }
        }
        for (List<Signal> signals : result.values()) {
            signals.sort(Comparator.comparingInt(Signal::hops).thenComparingDouble(Signal::distance)
                    .thenComparingLong(Signal::radioId));
        }
        return result;
    }

    private static double distance(int x, int y, int z, Amplifier amp) {
        double dx = x - amp.x(), dy = y - amp.y(), dz = z - amp.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
