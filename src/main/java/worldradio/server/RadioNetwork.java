package worldradio.server;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import worldradio.Config;
import worldradio.block.AmplifierBlockEntity;
import worldradio.block.RadioBlockEntity;
import worldradio.block.SignalBlock;
import worldradio.net.Packets;
import worldradio.server.NetworkData.Node;
import worldradio.signal.Antenna;
import worldradio.signal.Signal;
import worldradio.signal.SignalGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The radios and amplifiers of one level, loaded or not (see {@link NetworkData}). Whenever something changes (placed,
 * broken, loaded, new station, switched on/off, a neighbour block) the antenna columns of the loaded ones are counted
 * and the signal graph is worked out again at the end of that tick; every loaded amplifier gets its new signal list,
 * every loaded block its range and display level, and the players of the level get the list of sound sources when it
 * changed. Antenna blocks are vanilla blocks without a hook, so the columns are also rescanned once a second (higher
 * blocks, pistons), and a slow safety recompute catches the rest.
 */
public final class RadioNetwork {
    private static final Map<ServerLevel, RadioNetwork> NETWORKS = new HashMap<>();
    private static final int RESCAN_INTERVAL = 20;
    private static final int SAFETY_INTERVAL = 40;

    private final ServerLevel level;
    private final NetworkData data;
    private boolean dirty = true;
    private int ticks;
    private Packets.Sources sent;

    private RadioNetwork(ServerLevel level) {
        this.level = level;
        this.data = NetworkData.get(level);
    }

    public static RadioNetwork get(ServerLevel level) {
        return NETWORKS.computeIfAbsent(level, RadioNetwork::new);
    }

    public static void clear() {
        NETWORKS.clear();
    }

    /** A radio or amplifier block entity was loaded or placed. */
    public void add(BlockEntity be) {
        Node node = nodeOf(be, 0, 0);
        if (node != null) {
            Node known = data.get(node.pos());
            // keep the antenna and range the network knew until the next recompute counts them
            data.put(known == null ? node : nodeOf(be, known.antenna(), known.range()));
        }
        dirty = true;
    }

    /** Its chunk unloaded: the node stays and keeps sending with what was known last. */
    public void unloaded(BlockEntity be) {
        dirty = true;
    }

    /** The block was broken or replaced. */
    public void removed(BlockPos pos) {
        data.remove(pos);
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public int radioCount() {
        return (int) data.nodes().stream().filter(Node::radio).count();
    }

    public int amplifierCount() {
        return (int) data.nodes().stream().filter(n -> !n.radio()).count();
    }

    public void tick() {
        ticks++;
        if (ticks % SAFETY_INTERVAL == 0) dirty = true;
        else if (!dirty && ticks % RESCAN_INTERVAL == 0 && antennaChanged()) dirty = true;
        if (!dirty) return;
        dirty = false;
        recompute();
    }

    /** Sends the current sources to a player who joined or came into this level. */
    public void sendTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, sent != null ? sent
                : new Packets.Sources(level.dimension().identifier(), List.of(), List.of()));
    }

    /** Antenna blocks straight above {@code pos}, up to the build height or the first unloaded block. */
    public int antennaCount(BlockPos pos) {
        BlockPos.MutableBlockPos at = pos.mutable();
        int limit = level.getMaxY() - pos.getY();
        return Antenna.count(dy -> {
            at.setY(pos.getY() + dy);
            return level.isLoaded(at) && SignalBlock.isAntenna(level.getBlockState(at));
        }, limit);
    }

    private boolean antennaChanged() {
        for (Node node : data.nodes()) {
            if (level.isLoaded(node.pos()) && node.antenna() != antennaCount(node.pos())) return true;
        }
        return false;
    }

    private static Node nodeOf(BlockEntity be, int antenna, int range) {
        BlockPos pos = be.getBlockPos().immutable();
        if (be instanceof RadioBlockEntity radio) {
            return new Node(true, pos, radio.url(), radio.name(), radio.enabled(), antenna, range, radio.volume());
        }
        if (be instanceof AmplifierBlockEntity) return new Node(false, pos, "", "", true, antenna, range, 1.0f);
        return null;
    }

    private void recompute() {
        List<SignalGraph.Radio> radioNodes = new ArrayList<>();
        List<SignalGraph.Amplifier> ampNodes = new ArrayList<>();
        Map<Long, AmplifierBlockEntity> ampEntities = new HashMap<>();
        Map<Long, Integer> ampAntenna = new HashMap<>();
        for (Node known : List.copyOf(data.nodes())) {
            BlockPos pos = known.pos();
            Node node = known;
            if (level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                boolean matches = known.radio() ? be instanceof RadioBlockEntity : be instanceof AmplifierBlockEntity;
                if (!matches || be.isRemoved()) {
                    data.remove(pos); // gone without us hearing of it (e.g. a /fill that skips side effects)
                    continue;
                }
                int antenna = antennaCount(pos);
                node = nodeOf(be, antenna, Config.range(antenna));
                if (be instanceof RadioBlockEntity radio) {
                    radio.setRange(node.range(), antenna);
                    setLevel(pos, Antenna.level(radio.sends(), antenna));
                } else {
                    ampEntities.put(pos.asLong(), (AmplifierBlockEntity) be);
                }
            } else {
                // unloaded: the last antenna count, with the current config
                node = new Node(known.radio(), pos, known.url(), known.name(), known.enabled(), known.antenna(),
                        Config.range(known.antenna()), known.volume());
            }
            data.put(node);
            if (node.radio()) {
                // a switched-off radio keeps its station but sends nothing
                radioNodes.add(new SignalGraph.Radio(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(), node.range(),
                        node.sends() ? node.url() : "", node.name()));
            } else {
                ampNodes.add(new SignalGraph.Amplifier(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(), node.range()));
                ampAntenna.put(pos.asLong(), node.antenna());
            }
        }
        Map<Long, List<Signal>> signals = SignalGraph.compute(radioNodes, ampNodes);
        for (Map.Entry<Long, AmplifierBlockEntity> e : ampEntities.entrySet()) {
            List<Signal> received = signals.getOrDefault(e.getKey(), List.of());
            int antenna = ampAntenna.get(e.getKey());
            e.getValue().update(received, Config.range(antenna), antenna);
            setLevel(BlockPos.of(e.getKey()), Antenna.level(!received.isEmpty(), antenna));
        }
        publish(radioNodes, ampNodes, signals);
    }

    /** The sound sources of this level to its players, when they changed. */
    private void publish(List<SignalGraph.Radio> radios, List<SignalGraph.Amplifier> amps, Map<Long, List<Signal>> signals) {
        Map<Long, Float> volumes = new HashMap<>();
        for (Node node : data.nodes()) if (node.radio()) volumes.put(node.pos().asLong(), node.volume());
        List<Packets.RadioSource> radioSources = new ArrayList<>();
        for (SignalGraph.Radio r : radios) {
            if (r.url().isEmpty() || radioSources.size() >= Packets.MAX_SOURCES) continue;
            radioSources.add(new Packets.RadioSource(BlockPos.of(r.id()), r.url(), r.range(), volumes.getOrDefault(r.id(), 1.0f)));
        }
        List<Packets.AmpSource> ampSources = new ArrayList<>();
        for (SignalGraph.Amplifier a : amps) {
            List<Signal> received = signals.getOrDefault(a.id(), List.of());
            if (received.isEmpty() || ampSources.size() >= Packets.MAX_SOURCES) continue;
            List<Packets.AmpSignal> list = new ArrayList<>();
            for (Signal s : received.subList(0, Math.min(64, received.size()))) {
                list.add(new Packets.AmpSignal(s.url(), (float) s.factor() * volumes.getOrDefault(s.radioId(), 1.0f)));
            }
            ampSources.add(new Packets.AmpSource(BlockPos.of(a.id()), a.range(), list));
        }
        radioSources.sort(java.util.Comparator.comparingLong(r -> r.pos().asLong()));
        ampSources.sort(java.util.Comparator.comparingLong(a -> a.pos().asLong()));
        Packets.Sources sources = new Packets.Sources(level.dimension().identifier(), radioSources, ampSources);
        if (sources.equals(sent)) return;
        sent = sources;
        for (ServerPlayer player : level.players()) ServerPlayNetworking.send(player, sources);
    }

    /** The display level of the model; only written when it changes, and only sent to the clients. */
    private void setLevel(BlockPos pos, int value) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(SignalBlock.LEVEL) && state.getValue(SignalBlock.LEVEL) != value) {
            level.setBlock(pos, state.setValue(SignalBlock.LEVEL, value), Block.UPDATE_CLIENTS);
        }
    }
}
