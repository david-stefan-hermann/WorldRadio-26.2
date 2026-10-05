package worldradio.signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a portable radio can pick up at a point: every transmitter and every amplifier with a signal is an
 * {@link Emitter} with the stations it sends; a station arrives with the strength of the best emitter that carries it
 * (see {@link VolumeCurve#reception}).
 */
public final class Reception {
    public record Station(String url, String name) {
    }

    /** A transmitter or amplifier at a block position with its range and the stations it sends. */
    public record Emitter(int x, int y, int z, int range, List<Station> stations) {
    }

    /** A station that can be heard at the point, {@code strength} 0..1. */
    public record Heard(String url, String name, double strength) {
    }

    private Reception() {
    }

    /** The stations arriving at the point (distances count to the block centres), strongest first. */
    public static List<Heard> at(double x, double y, double z, List<Emitter> emitters) {
        Map<String, Heard> best = new HashMap<>();
        for (Emitter e : emitters) {
            double dx = e.x() + 0.5 - x, dy = e.y() + 0.5 - y, dz = e.z() + 0.5 - z;
            double strength = VolumeCurve.reception(Math.sqrt(dx * dx + dy * dy + dz * dz), e.range());
            if (strength <= 0) continue;
            for (Station s : e.stations()) {
                Heard old = best.get(s.url());
                if (old == null || strength > old.strength()) best.put(s.url(), new Heard(s.url(), s.name(), strength));
            }
        }
        List<Heard> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingDouble(Heard::strength).reversed().thenComparing(Heard::name)
                .thenComparing(Heard::url));
        return out;
    }

    /** How well {@code url} arrives at the point, 0 when it does not. */
    public static double strength(double x, double y, double z, String url, List<Emitter> emitters) {
        for (Heard h : at(x, y, z, emitters)) if (h.url().equals(url)) return h.strength();
        return 0.0;
    }
}
