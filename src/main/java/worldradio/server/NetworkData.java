package worldradio.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import worldradio.WorldRadio;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every transmitter, channel, amplifier and radio of one dimension, loaded or not, with what the network last knew
 * about it. Transmitters keep sending and amplifiers keep relaying when their chunk unloads (a range of 1000 blocks
 * reaches far past any view distance); an entry only goes when the block is broken. {@code antenna} and {@code range}
 * are the antenna blocks and signal range of a transmitter or amplifier; for a radio they are its speakers and its
 * hearing range. A channel has neither: it sends through the transmitters it is connected to.
 */
public final class NetworkData extends SavedData {
    public enum Kind implements StringRepresentable {
        TRANSMITTER, CHANNEL, AMPLIFIER, RECEIVER;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Node(Kind kind, BlockPos pos, String url, String name, boolean enabled, int antenna, int range,
                       float volume) {
        /** Files of 1.x have no kind, only {@code radio}: true for what is now the transmitter, false for an amplifier. */
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.optionalFieldOf("kind").forGetter(n -> Optional.of(n.kind())),
                Codec.BOOL.optionalFieldOf("radio", false).forGetter(n -> n.kind() == Kind.TRANSMITTER),
                BlockPos.CODEC.fieldOf("pos").forGetter(Node::pos),
                Codec.STRING.optionalFieldOf("url", "").forGetter(Node::url),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Node::name),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Node::enabled),
                Codec.INT.optionalFieldOf("antenna", 0).forGetter(Node::antenna),
                Codec.INT.optionalFieldOf("range", 0).forGetter(Node::range),
                Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(Node::volume)
        ).apply(i, (kind, radio, pos, url, name, enabled, antenna, range, volume) -> new Node(
                kind.orElse(radio ? Kind.TRANSMITTER : Kind.AMPLIFIER), pos, url, name, enabled, antenna, range, volume)));

        /** Switched on and tuned: a transmitter or channel with something to send, a radio that wants to play. */
        public boolean tuned() {
            return enabled && !url.isEmpty();
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
