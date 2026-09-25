package worldradio.signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which radio stations reach which amplifiers. A radio reaches an amplifier within its own range; an amplifier passes
 * everything it receives on to every amplifier within the amplifier's range, over any number of hops. A radio without
 * a station sends nothing; radios or amplifiers with range 0 would take no part (the network never builds them).
 */
public final class SignalGraph {
    public record Radio(long id, int x, int y, int z, int range, String url, String name) {
    }

    public record Amplifier(long id, int x, int y, int z, int range) {
    }

    private SignalGraph() {
    }

    /** Signals per amplifier id; every amplifier that takes part has an entry, possibly empty. */
    public static Map<Long, List<Signal>> compute(List<Radio> radios, List<Amplifier> amplifiers) {
        List<Amplifier> active = new ArrayList<>();
        Map<Long, List<Signal>> result = new HashMap<>();
        for (Amplifier amp : amplifiers) {
            result.put(amp.id(), new ArrayList<>());
            if (amp.range() > 0) active.add(amp);
        }
        int n = active.size();
        for (Radio radio : radios) {
            if (radio.range() <= 0 || radio.url() == null || radio.url().isEmpty()) continue;
            // Breadth first, so every amplifier keeps the shortest chain of hops from this radio.
            int[] hops = new int[n];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int i = 0; i < n; i++) {
                if (distance(radio.x(), radio.y(), radio.z(), active.get(i)) <= radio.range()) {
                    hops[i] = 1;
                    queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                int from = queue.poll();
                Amplifier relay = active.get(from);
                for (int i = 0; i < n; i++) {
                    if (hops[i] != 0) continue;
                    if (distance(relay.x(), relay.y(), relay.z(), active.get(i)) <= relay.range()) {
                        hops[i] = hops[from] + 1;
                        queue.add(i);
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (hops[i] == 0) continue;
                Amplifier amp = active.get(i);
                result.get(amp.id()).add(new Signal(radio.id(), radio.x(), radio.y(), radio.z(), radio.url(),
                        radio.name(), distance(radio.x(), radio.y(), radio.z(), amp), hops[i], 1.0));
            }
        }
        for (Map.Entry<Long, List<Signal>> entry : result.entrySet()) {
            List<Signal> signals = entry.getValue();
            double factor = factor(signals.size());
            signals.replaceAll(s -> s.withFactor(factor));
            signals.sort(Comparator.comparingInt(Signal::hops).thenComparingDouble(Signal::distance)
                    .thenComparingLong(Signal::radioId));
        }
        return result;
    }

    /** One signal plays at full volume, two or more share the amplifier at half volume each. */
    public static double factor(int signals) {
        return signals >= 2 ? 0.5 : 1.0;
    }

    private static double distance(int x, int y, int z, Amplifier amp) {
        double dx = x - amp.x(), dy = y - amp.y(), dz = z - amp.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
