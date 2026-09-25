package worldradio.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.net.Packets;
import worldradio.signal.Panner;
import worldradio.signal.SourcePicker;
import worldradio.signal.SourcePicker.Candidate;
import worldradio.signal.VolumeCurve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Decides every client tick what the player hears: each radio and each signal of each amplifier the server listed
 * for this dimension (loaded or not, see {@link Packets.Sources}) is a candidate with its curve volume at the camera; per station only the loudest place plays (with hysteresis), and of
 * the stations only the loudest few. A station that moves from one place to another keeps its sound and stream.
 */
public final class SourceTracker {
    /** The latest source list from the server. */
    private static volatile Packets.Sources sources;
    private static final Map<String, Playing> PLAYING = new HashMap<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();
    private static final SourcePicker PICKER = new SourcePicker();
    /** Ticks a new sound gets to show up in the sound engine before it counts as lost. */
    private static final int START_GRACE = 40;
    private static long ticks;

    private record Playing(RadioSoundInstance sound, long started) {
    }

    private SourceTracker() {
    }

    public static void setSources(Packets.Sources list) {
        sources = list;
    }

    /** What plays right now: station address → position of the place it plays from. */
    public static Map<String, Vec3> playing() {
        Map<String, Vec3> out = new HashMap<>();
        PLAYING.forEach((url, p) -> out.put(url, p.sound().place()));
        return out;
    }

    /** Debug line for a playing station: whether the sound engine has it, and what its stream handed out. */
    public static String debug(String url) {
        Playing p = PLAYING.get(url);
        if (p == null) return "not playing";
        return "active=" + Minecraft.getInstance().getSoundManager().isActive(p.sound()) + " " + p.sound().streamStats()
                + " category=" + p.sound().getSource().getName() + " " + p.sound().panStats();
    }

    public static String panOf(String url) {
        Playing p = PLAYING.get(url);
        return p == null ? "not playing" : p.sound().panStats();
    }

    public static float volumeOf(String url) {
        Playing p = PLAYING.get(url);
        return p == null ? 0 : p.sound().getVolume();
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        StreamPool.tick();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            stopAll();
            sources = null;
            return;
        }
        Vec3 ear = minecraft.gameRenderer.mainCamera().position();
        float yaw = minecraft.gameRenderer.mainCamera().yRot();
        double share = Config.get().directionalShare();
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Vec3> places = new HashMap<>();
        Packets.Sources list = sources;
        if (list != null && list.dimension().equals(level.dimension().identifier())) {
            for (Packets.RadioSource radio : list.radios()) {
                Vec3 centre = Vec3.atCenterOf(radio.pos());
                double volume = VolumeCurve.volume(centre.distanceTo(ear), radio.range());
                if (volume <= 0) continue;
                String id = "r" + radio.pos().asLong();
                candidates.add(new Candidate(radio.url(), id, volume * radio.volume()));
                places.put(id, centre);
            }
            for (Packets.AmpSource amp : list.amplifiers()) {
                Vec3 centre = Vec3.atCenterOf(amp.pos());
                double volume = VolumeCurve.volume(centre.distanceTo(ear), amp.range());
                if (volume <= 0) continue;
                String id = "a" + amp.pos().asLong();
                places.put(id, centre);
                for (Packets.AmpSignal signal : amp.signals()) {
                    candidates.add(new Candidate(signal.url(), id, volume * signal.factor()));
                }
            }
        }
        Map<String, Candidate> chosen = PICKER.pick(candidates, Config.get().maxStations());

        for (Iterator<Map.Entry<String, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Playing> e = it.next();
            Playing p = e.getValue();
            boolean lost = ticks - p.started() > START_GRACE && !minecraft.getSoundManager().isActive(p.sound());
            if (!chosen.containsKey(e.getKey()) || lost) {
                minecraft.getSoundManager().stop(p.sound());
                p.sound().finish();
                it.remove();
                if (lost) {
                    RETRY_AFTER.put(e.getKey(), ticks + 100);
                    WorldRadio.LOGGER.info("Radio: sound for {} was dropped by the sound engine, retrying", e.getKey());
                }
            }
        }
        for (Candidate c : chosen.values()) {
            Vec3 place = places.get(c.sourceId());
            Playing p = PLAYING.get(c.station());
            double pan = Panner.pan(yaw, place.x - ear.x, place.z - ear.z);
            float[] gains = Panner.gains(pan, share);
            if (p != null) {
                p.sound().move(place);
                p.sound().setVolume((float) c.volume());
                p.sound().setPan(pan, gains);
                continue;
            }
            if (RETRY_AFTER.getOrDefault(c.station(), 0L) > ticks) continue;
            RadioSoundInstance sound = new RadioSoundInstance(c.station(), place, (float) c.volume());
            sound.setPan(pan, gains);
            minecraft.getSoundManager().play(sound);
            PLAYING.put(c.station(), new Playing(sound, ticks));
            WorldRadio.LOGGER.info("Radio: playing {} from {}", c.station(), c.sourceId());
        }
    }

    public static void stopAll() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Playing p : PLAYING.values()) {
            minecraft.getSoundManager().stop(p.sound());
            p.sound().finish();
        }
        PLAYING.clear();
    }
}
