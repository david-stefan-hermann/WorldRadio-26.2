package worldradio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import worldradio.signal.Antenna;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/worldradio.json}. The range and hearing settings are read by the server (the blocks carry their range to
 * the clients), {@code maxStations} and {@code directionalShare} by the client. A file from 0.1/0.2 with a
 * {@code ranges} list still loads: Gson skips the unknown entry and the file is written back without it. A file from
 * 0.3 (no {@code configVersion}) holds the old default {@code antennaStep} 8, which is raised to the new default.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Values values = new Values();

    /** Gson fills the fields; missing ones keep these defaults. */
    public static final class Values {
        static final int VERSION = 5;
        /** Format of this file; missing in files from 0.1 to 0.3. */
        int configVersion = VERSION;
        /** Range in blocks of a radio or amplifier without antenna blocks. */
        int baseRange = Antenna.BASE_RANGE;
        /** Blocks of range each antenna block on top adds. */
        int antennaStep = Antenna.STEP;
        /** Antenna blocks past this many add no range. */
        int maxAntenna = Antenna.MAX_ANTENNA;
        /** How far a radio without speakers can be heard, in blocks. */
        int hearingBase = 4;
        /** Blocks of hearing range each speaker connected to a radio adds. */
        int speakerStep = 4;
        /** How far a portable radio that is put down can be heard without speakers, in blocks. */
        int portableHearing = 10;
        /** The most speakers can raise a radio's hearing range to. */
        int hearingMax = 64;
        /** How many stations one client plays at the same time (vanilla has 8 streaming channels). */
        int maxStations = 6;
        /** Share of the sound that comes from the station's direction (0 = all at the player, 1 = fully panned). */
        double directionalShare = 0.3;

        public int baseRange() {
            return Math.max(1, baseRange);
        }

        public int antennaStep() {
            return Math.max(0, antennaStep);
        }

        public int maxAntenna() {
            return Math.max(0, maxAntenna);
        }

        public int hearingBase() {
            return Math.max(1, hearingBase);
        }

        public int portableHearing() {
            return Math.max(1, portableHearing);
        }

        public int speakerStep() {
            return Math.max(0, speakerStep);
        }

        public int hearingMax() {
            return Math.max(hearingBase(), hearingMax);
        }

        public double directionalShare() {
            return Math.clamp(directionalShare, 0.0, 1.0);
        }

        public int maxStations() {
            return Math.clamp(maxStations, 1, 8);
        }
    }

    private Config() {
    }

    public static Values get() {
        return values;
    }

    /** Range in blocks with {@code antenna} antenna blocks on top. */
    public static int range(int antenna) {
        return Antenna.range(antenna, values.baseRange(), values.antennaStep(), values.maxAntenna());
    }

    /** A radio's hearing range in blocks with {@code speakers} speakers connected to it. */
    public static int hearing(int speakers) {
        return (int) Math.min(values.hearingMax(), values.hearingBase() + (long) Math.max(speakers, 0) * values.speakerStep());
    }

    /** The same for a portable radio that is put down: it starts further out, speakers add as much. */
    public static int portableHearing(int speakers) {
        int base = values.portableHearing();
        return (int) Math.min(Math.max(values.hearingMax(), base), base + (long) Math.max(speakers, 0) * values.speakerStep());
    }

    /** Speakers past this many add nothing to a radio. */
    public static int maxSpeakers() {
        int step = values.speakerStep();
        return step == 0 ? 0 : (values.hearingMax() - values.hearingBase() + step - 1) / step;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("worldradio.json");
        try {
            if (Files.exists(file)) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                Values read = GSON.fromJson(json, Values.class);
                if (read != null) {
                    // 0.3 wrote its default step of 8 into every file; 0.4 raised the default to 32
                    if (!json.has("configVersion") && read.antennaStep == 8) read.antennaStep = Antenna.STEP;
                    read.configVersion = Values.VERSION;
                    values = read;
                }
            }
            Files.writeString(file, GSON.toJson(values));
        } catch (IOException | RuntimeException e) {
            WorldRadio.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
        }
    }
}
