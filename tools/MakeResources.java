import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the repetitive JSON: blockstates (level x facing), item model definitions (the item shows the highest level,
 * so it and the creative tab icon look switched on), loot tables, and the portable radio's item model. The block models and textures are made by
 * tools/MakeTextures.java.
 * Run: java tools/MakeResources.java (from the project folder).
 */
public class MakeResources {
    static final Path ASSETS = Path.of("src/main/resources/assets/worldradio");
    static final Path DATA = Path.of("src/main/resources/data/worldradio");
    static final String[] FACINGS = {"north", "east", "south", "west"};
    static final int[] ROTATIONS = {0, 90, 180, 270};

    public static void main(String[] args) throws IOException {
        for (String item : new String[]{"portable_radio", "guide_book"}) {
            write(ASSETS.resolve("items/" + item + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "worldradio:item/%s"
                      }
                    }
                    """.formatted(item));
            write(ASSETS.resolve("models/item/" + item + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "worldradio:item/%s"
                      }
                    }
                    """.formatted(item));
        }
        // the speaker has a facing and is lit while its radio plays
        StringBuilder speaker = new StringBuilder("{\n  \"variants\": {\n");
        for (int lit = 0; lit < 2; lit++) {
            for (int f = 0; f < 4; f++) {
                speaker.append("    \"facing=").append(FACINGS[f]).append(",lit=").append(lit == 1)
                        .append("\": { \"model\": \"worldradio:block/speaker").append(lit == 1 ? "_on" : "").append('"')
                        .append(ROTATIONS[f] == 0 ? "" : ", \"y\": " + ROTATIONS[f]).append(" }")
                        .append(lit == 1 && f == 3 ? "\n" : ",\n");
            }
        }
        write(ASSETS.resolve("blockstates/speaker.json"), speaker.append("  }\n}\n").toString());
        write(ASSETS.resolve("items/speaker.json"), """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "worldradio:block/speaker_on"
                  }
                }
                """);
        write(DATA.resolve("loot_table/blocks/speaker.json"), lootTable("speaker"));
        for (String block : new String[]{"radio", "channel", "amplifier", "receiver"}) {
            StringBuilder states = new StringBuilder("{\n  \"variants\": {\n");
            for (int level = 0; level <= 3; level++) {
                for (int f = 0; f < 4; f++) {
                    boolean last = level == 3 && f == 3;
                    states.append("    \"facing=").append(FACINGS[f]).append(",level=").append(level)
                            .append("\": { \"model\": \"worldradio:block/").append(block).append('_').append(level)
                            .append('"').append(ROTATIONS[f] == 0 ? "" : ", \"y\": " + ROTATIONS[f]).append(" }")
                            .append(last ? "\n" : ",\n");
                }
            }
            states.append("  }\n}\n");
            write(ASSETS.resolve("blockstates/" + block + ".json"), states.toString());
            write(ASSETS.resolve("items/" + block + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "worldradio:block/%s_3"
                      }
                    }
                    """.formatted(block));
            write(DATA.resolve("loot_table/blocks/" + block + ".json"), lootTable(block));
        }
    }

    static String lootTable(String block) {
        return """
                {
                  "type": "minecraft:block",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [ { "type": "minecraft:item", "name": "worldradio:%s" } ],
                      "conditions": [ { "condition": "minecraft:survives_explosion" } ]
                    }
                  ],
                  "random_sequence": "worldradio:blocks/%1$s"
                }
                """.formatted(block);
    }

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
