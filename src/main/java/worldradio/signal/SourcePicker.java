package worldradio.signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the one source that plays each station: the loudest one, but a new source only takes over from the playing
 * one when it is louder by at least {@link #HYSTERESIS}, so two equally loud sources do not flicker. Of all stations
 * only the loudest {@code maxStations} play.
 */
public final class SourcePicker {
    public static final double HYSTERESIS = 0.05;

    /** A place a station can be heard from, with its volume at the listener (curve times source factor). */
    public record Candidate(String station, String sourceId, double volume) {
    }

    private final Map<String, String> active = new HashMap<>();

    /** The chosen source per station, loudest station first. Stations not in the result are silent. */
    public Map<String, Candidate> pick(List<Candidate> candidates, int maxStations) {
        Map<String, Candidate> loudest = new HashMap<>();
        Map<String, Candidate> current = new HashMap<>();
        for (Candidate c : candidates) {
            if (c.volume() <= 0) continue;
            loudest.merge(c.station(), c, (a, b) -> b.volume() > a.volume() ? b : a);
            if (c.sourceId().equals(active.get(c.station()))) current.put(c.station(), c);
        }
        List<Candidate> chosen = new ArrayList<>();
        for (Candidate best : loudest.values()) {
            Candidate playing = current.get(best.station());
            chosen.add(playing != null && best.volume() < playing.volume() + HYSTERESIS ? playing : best);
        }
        chosen.sort(Comparator.comparingDouble(Candidate::volume).reversed().thenComparing(Candidate::station));
        Map<String, Candidate> result = new LinkedHashMap<>();
        for (Candidate c : chosen) {
            if (result.size() >= maxStations) break;
            result.put(c.station(), c);
        }
        active.clear();
        result.forEach((station, c) -> active.put(station, c.sourceId()));
        return result;
    }
}
