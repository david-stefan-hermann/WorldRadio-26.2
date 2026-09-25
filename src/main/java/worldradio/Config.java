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
 * {@code config/worldradio.json}. The range settings are read by the server (radios and amplifiers carry their range to
 * the clients), {@code maxStations} and {@code directionalShare} by the client. A file from 0.1/0.2 with a
 * {@code ranges} list still loads: Gson skips the unknown entry and the file is written back without it. A file from
 * 0.3 (no {@code configVersion}) holds the old default {@code antennaStep} 8, which is raised to the new default.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Values values = new Values();

    /** Gson fills the fields; missing ones keep these defaults. */
    public static final class Values {
        static final int VERSION = 4;
        /** Format of this file; missing in files from 0.1 to 0.3. */
        int configVersion = VERSION;
        /** Range in blocks of a radio or amplifier without antenna blocks. */
        int baseRange = Antenna.BASE_RANGE;
        /** Blocks of range each antenna block on top adds. */
        int antennaStep = Antenna.STEP;
        /** Antenna blocks past this many add no range. */
        int maxAntenna = Antenna.MAX_ANTENNA;
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
