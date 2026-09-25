package worldradio.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import worldradio.WorldRadio;
import worldradio.server.FavouritesData.Favourite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The player's own favourite stations, kept in {@code config/worldradio-favorites.json} on their computer, so the
 * same list is there in every world and on every server. Worlds from 0.1 to 0.4 kept one shared list on the server; a
 * server still sends that list on join, and each of its stations is taken over once (a station removed afterwards
 * does not come back).
 */
public final class FavouritesCache {
    public static final int MAX = 200;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("worldradio-favorites.json");
    private static final List<Favourite> favourites = new ArrayList<>();
    /** Addresses already taken over from a world's old shared list. */
    private static final Set<String> imported = new LinkedHashSet<>();
    private static int version;

    private FavouritesCache() {
    }

    public static void load() {
        favourites.clear();
        imported.clear();
        try {
            if (Files.exists(FILE)) {
                JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                for (JsonElement e : json.getAsJsonArray("favorites")) {
                    JsonObject f = e.getAsJsonObject();
                    favourites.add(new Favourite(string(f, "name"), string(f, "url"), string(f, "country")));
                }
                if (json.has("imported")) for (JsonElement e : json.getAsJsonArray("imported")) imported.add(e.getAsString());
            }
        } catch (IOException | RuntimeException e) {
            WorldRadio.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
        }
        version++;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static void save() {
        JsonObject json = new JsonObject();
        JsonArray list = new JsonArray();
        for (Favourite f : favourites) {
            JsonObject o = new JsonObject();
            o.addProperty("name", f.name());
            o.addProperty("url", f.url());
            o.addProperty("country", f.country());
            list.add(o);
        }
        json.add("favorites", list);
        JsonArray done = new JsonArray();
        imported.forEach(done::add);
        json.add("imported", done);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (IOException e) {
            WorldRadio.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
        }
        version++;
    }

    public static List<Favourite> list() {
        return List.copyOf(favourites);
    }

    public static boolean contains(String url) {
        return favourites.stream().anyMatch(f -> f.url().equals(url));
    }

    public static void add(Favourite favourite) {
        if (contains(favourite.url()) || favourites.size() >= MAX) return;
        favourites.add(favourite);
        save();
    }

    public static void remove(String url) {
        if (favourites.removeIf(f -> f.url().equals(url))) save();
    }

    /** Gives a favourite a new name (blank names are ignored). */
    public static void rename(String url, String name) {
        String clean = name.strip();
        if (clean.isEmpty()) return;
        for (int i = 0; i < favourites.size(); i++) {
            Favourite f = favourites.get(i);
            if (f.url().equals(url) && !f.name().equals(clean)) {
                favourites.set(i, new Favourite(clean.length() > 256 ? clean.substring(0, 256) : clean, f.url(), f.country()));
                save();
            }
        }
    }

    /** A world's old shared list: each station not taken over before is added once. */
    public static void importLegacy(List<Favourite> legacy) {
        boolean changed = false;
        for (Favourite f : legacy) {
            if (!imported.add(f.url())) continue;
            changed = true;
            if (!contains(f.url()) && favourites.size() < MAX) favourites.add(f);
        }
        if (changed) {
            WorldRadio.LOGGER.info("Took over {} favourites from this world's old shared list", legacy.size());
            save();
        }
    }

    /** Goes up with every change, so screens know when to redraw their list. */
    public static int version() {
        return version;
    }
}
