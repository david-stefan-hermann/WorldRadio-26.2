package worldradio.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import worldradio.WorldRadio;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every radio and amplifier of one dimension, loaded or not, with what the network last knew about it. Radios keep
 * playing and amplifiers keep relaying when their chunk unloads (a range of 1000 blocks reaches far past any view
 * distance); an entry only goes when the block is broken.
 */
public final class NetworkData extends SavedData {
    public record Node(boolean radio, BlockPos pos, String url, String name, boolean enabled, int antenna, int range,
                       float volume) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("radio").forGetter(Node::radio),
                BlockPos.CODEC.fieldOf("pos").forGetter(Node::pos),
                Codec.STRING.optionalFieldOf("url", "").forGetter(Node::url),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Node::name),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Node::enabled),
                Codec.INT.optionalFieldOf("antenna", 0).forGetter(Node::antenna),
                Codec.INT.optionalFieldOf("range", 0).forGetter(Node::range),
                Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(Node::volume)
        ).apply(i, Node::new));

        /** A radio that is switched on and tuned. */
        public boolean sends() {
            return radio && enabled && !url.isEmpty();
        }
    }

    private static final Codec<NetworkData> CODEC = Node.CODEC.listOf().fieldOf("nodes")
            .xmap(NetworkData::new, data -> List.copyOf(data.nodes.values())).codec();
    public static final SavedDataType<NetworkData> TYPE = new SavedDataType<>(WorldRadio.id("network"),
            NetworkData::new, CODEC, null);

    private final Map<BlockPos, Node> nodes = new HashMap<>();

    public NetworkData() {
    }

    private NetworkData(List<Node> list) {
        for (Node node : list) nodes.put(node.pos(), node);
    }

    public static NetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public Node get(BlockPos pos) {
        return nodes.get(pos);
    }

    public void put(Node node) {
        Node old = nodes.put(node.pos(), node);
        if (!node.equals(old)) setDirty();
    }

    public void remove(BlockPos pos) {
        if (nodes.remove(pos) != null) setDirty();
    }
}
