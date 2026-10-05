package worldradio.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.net.Packets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The content of the guide book, as data: chapters of sections, which the client's GuideBookScreen wraps and breaks
 * into pages. Texts are translation keys {@code worldradio.book.<key>} in the language files; every key the book uses
 * is listed by {@link #keys()}, which the screen checks against the loaded language in the dev environment.
 */
public final class GuideBook {
    /** What a recipe's tag ingredient is shown as; the page says that any kind will do. */
    private static final Map<String, String> STAND_INS = Map.of("#minecraft:planks", "minecraft:oak_planks",
            "#minecraft:wool", "minecraft:white_wool");

    private GuideBook() {
    }

    public static String key(String name) {
        return WorldRadio.MOD_ID + ".book." + name;
    }

    public sealed interface Section permits Heading, Text, Announcement, ItemLine, Recipe, Picture, Break {
        default List<String> keys() {
            return List.of();
        }
    }

    public record Heading(String key) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    public record Text(String key, Object... args) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** A line as an announcer would say it, set in italics. */
    public record Announcement(String key) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** Item icons with a text beside them. */
    public record ItemLine(List<ItemStack> icons, String key) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** A crafting grid: nine stacks, row by row (empty where nothing goes), and what it makes. */
    public record Recipe(List<ItemStack> grid, ItemStack result) implements Section {
    }

    /** A build picture, 96 pixels square (textures/gui/book/, made by tools/MakeBook.java). */
    public record Picture(Identifier texture) implements Section {
    }

    /** What follows starts a new page. */
    public record Break() implements Section {
    }

    /** A chapter with its tab: the blocks' tabs are on the left of the book, the topics' on the right. */
    public record Chapter(String title, ItemStack icon, boolean block, List<Section> sections) {
    }

    /**
     * Built when the book opens: item stacks need the registries. The numbers are those of this game's config, which
     * is the server's own in single player (as on the screens' range line).
     */
    public static List<Chapter> chapters() {
        Config.Values config = Config.get();
        Heading crafting = new Heading(key("heading.crafting")), use = new Heading(key("heading.use"));
        Break turn = new Break(); // every block: what it is and its recipe on the first page, its use on the second
        return List.of(
                new Chapter(key("start"), new ItemStack(WorldRadio.GUIDE_BOOK_ITEM), false, List.of(
                        new Text(key("start.text")), new Announcement(key("start.motto")),
                        new Heading(key("heading.steps")),
                        new ItemLine(List.of(new ItemStack(WorldRadio.RADIO_ITEM)), key("start.step1")),
                        new ItemLine(List.of(vanilla("lightning_rod")), key("start.step2")),
                        new ItemLine(List.of(new ItemStack(WorldRadio.RECEIVER_ITEM)), key("start.step3")))),
                new Chapter("block.worldradio.radio", new ItemStack(WorldRadio.RADIO_ITEM), true, List.of(
                        new Text(key("transmitter.text")), new Announcement(key("transmitter.motto")),
                        crafting, recipe("radio"),
                        turn, use, new Text(key("transmitter.use")), new Text(key("transmitter.more")))),
                new Chapter("block.worldradio.channel", new ItemStack(WorldRadio.CHANNEL_ITEM), true, List.of(
                        new Text(key("channel.text")), new Announcement(key("channel.motto")),
                        crafting, recipe("channel"),
                        turn, picture("channels"),
                        use, new Text(key("channel.use"), Packets.MAX_STATIONS))),
                new Chapter("block.worldradio.amplifier", new ItemStack(WorldRadio.AMPLIFIER_ITEM), true, List.of(
                        new Text(key("amplifier.text")), new Announcement(key("amplifier.motto")),
                        crafting, recipe("amplifier"),
                        turn, picture("relay"),
                        use, new Text(key("amplifier.use")))),
                new Chapter("block.worldradio.receiver", new ItemStack(WorldRadio.RECEIVER_ITEM), true, List.of(
                        new Text(key("receiver.text")), new Announcement(key("receiver.motto")),
                        crafting, recipe("receiver"), new Text(key("receiver.any")),
                        turn, use, new Text(key("receiver.use"), config.hearingBase()), new Text(key("receiver.silent")),
                        new Heading(key("heading.direction")), new Text(key("receiver.direction")))),
                new Chapter("block.worldradio.speaker", new ItemStack(WorldRadio.SPEAKER_ITEM), true, List.of(
                        new Text(key("speaker.text"), config.speakerStep(), config.hearingMax()),
                        new Announcement(key("speaker.motto")),
                        crafting, recipe("speaker"), new Text(key("speaker.any")),
                        turn, picture("speakers"),
                        use, new Text(key("speaker.use")))),
                new Chapter("item.worldradio.portable_radio", new ItemStack(WorldRadio.PORTABLE_RADIO_ITEM), true, List.of(
                        new Text(key("portable.text")), new Announcement(key("portable.motto")),
                        crafting, recipe("portable_radio"), new Text(key("portable.item")),
                        turn, use, new Text(key("portable.use")),
                        new Heading(key("heading.place")), new Text(key("portable.place"), config.hearingBase()))),
                new Chapter(key("range"), vanilla("lightning_rod"), false, List.of(
                        new Text(key("range.text"), config.baseRange(), config.antennaStep(), config.maxAntenna(),
                                Config.range(config.maxAntenna())),
                        new Announcement(key("range.motto")),
                        picture("tower"),
                        new Heading(key("heading.antenna")),
                        new ItemLine(List.of(vanilla("lightning_rod"), vanilla("iron_bars"), vanilla("iron_chain"),
                                vanilla("end_rod")), key("range.blocks")),
                        new Text(key("range.place")),
                        new Heading(key("heading.display")), new Text(key("range.display")),
                        new Text(key("range.server")))),
                new Chapter(key("stations"), vanilla("spyglass"), false, List.of(
                        new Text(key("stations.text")), new Announcement(key("stations.motto")),
                        new Heading(key("heading.favorites")), new Text(key("stations.favorites")),
                        new Heading(key("heading.browse")), new Text(key("stations.browse")),
                        new Heading(key("heading.url")), new Text(key("stations.url")),
                        new Heading(key("heading.streams")), new Text(key("stations.streams")))),
                new Chapter(key("listening"), vanilla("note_block"), false, List.of(
                        new Text(key("listening.text")), new Announcement(key("listening.motto")),
                        new Heading(key("heading.volume")), new Text(key("listening.volume")),
                        new Heading(key("heading.overlap")), new Text(key("listening.overlap"), config.maxStations()))));
    }

    /** Every translation key the book shows. */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Chapter chapter : chapters()) {
            keys.add(chapter.title());
            chapter.sections().forEach(s -> keys.addAll(s.keys()));
        }
        return keys;
    }

    /** By id: several vanilla items are no plain fields of Items any more (the copper and colour families). */
    private static ItemStack vanilla(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name)));
    }

    private static Picture picture(String name) {
        return new Picture(WorldRadio.id("textures/gui/book/" + name + ".png"));
    }

    // ponytail: reads the mod's own recipe files; a tag shows its stand-in, datapack overrides are not shown
    /**
     * The recipe data/worldradio/recipe/&lt;name&gt;.json as a crafting grid: shapeless ingredients fill the fields in
     * order, a shaped pattern is centred. The client has no recipe manager since 1.21.2, hence the file.
     */
    public static Recipe recipe(String name) {
        JsonObject json;
        try {
            json = JsonParser.parseString(Files.readString(FabricLoader.getInstance().getModContainer(WorldRadio.MOD_ID)
                    .orElseThrow().findPath("data/worldradio/recipe/" + name + ".json").orElseThrow(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        if (json.has("pattern")) {
            JsonArray pattern = json.getAsJsonArray("pattern");
            JsonObject keys = json.getAsJsonObject("key");
            int h = pattern.size(), w = pattern.get(0).getAsString().length();
            for (int y = 0; y < h; y++) {
                String row = pattern.get(y).getAsString();
                for (int x = 0; x < w; x++) {
                    String symbol = String.valueOf(row.charAt(x));
                    if (keys.has(symbol)) {
                        grid.set((y + (3 - h) / 2) * 3 + x + (3 - w) / 2, stack(keys.get(symbol)));
                    }
                }
            }
        } else {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            for (int i = 0; i < ingredients.size(); i++) {
                grid.set(i, stack(ingredients.get(i)));
            }
        }
        return new Recipe(grid, stack(json.getAsJsonObject("result").get("id")));
    }

    private static ItemStack stack(JsonElement id) {
        String name = id.getAsString();
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(STAND_INS.getOrDefault(name, name))));
    }
}
