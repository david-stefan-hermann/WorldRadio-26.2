package worldradio.server;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.block.AmplifierBlockEntity;
import worldradio.block.ChannelBlockEntity;
import worldradio.block.RadioBlockEntity;
import worldradio.block.ReceiverBlockEntity;
import worldradio.block.SignalBlock;
import worldradio.block.SpeakerBlock;
import worldradio.net.Packets;
import worldradio.server.NetworkData.Kind;
import worldradio.server.NetworkData.Node;
import worldradio.signal.Antenna;
import worldradio.signal.Reception;
import worldradio.signal.Signal;
import worldradio.signal.SignalGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The transmitters, channels, amplifiers and radios of one level, loaded or not (see {@link NetworkData}). Whenever
 * something changes (placed, broken, loaded, new station, switched on/off, a neighbour block) the antenna columns and
 * speaker chains of the loaded ones are counted and the signal graph is worked out again at the end of that tick;
 * every loaded amplifier and radio gets its new signal list, every loaded block its range and display level, and the
 * players of the level get the list of what plays and what sends when it changed. Antenna blocks are vanilla blocks
 * without a hook, so the columns are also rescanned once a second (higher blocks, pistons), and a slow safety
 * recompute catches the rest.
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
    /** The speakers this network switched to their playing look. */
    // ponytail: kept in memory only; a speaker saved lit keeps its look after a restart until its radio is loaded
    // again (they sit next to each other, so that is the same moment). Save it if that ever shows.
    private Set<BlockPos> litSpeakers = new HashSet<>();

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

    /** One of our block entities was loaded or placed. */
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

    public int count(Kind kind) {
        return (int) data.nodes().stream().filter(n -> n.kind() == kind).count();
    }

    /** Radios that make a sound right now, as last told to the clients. */
    public int playingCount() {
        return sent == null ? 0 : sent.receivers().size();
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

    /**
     * Speakers connected to the radio at {@code pos}: those touching it and, from speaker to touching speaker, the
     * whole chain. A portable radio that is put down connects to the speaker it stands on only, the chain goes on from
     * there. Takes loaded blocks only and stops at the number that still adds hearing range.
     */
    public Set<BlockPos> speakers(BlockPos pos) {
        int cap = Config.maxSpeakers();
        boolean portable = level.isLoaded(pos) && level.getBlockState(pos).is(WorldRadio.PORTABLE_RADIO);
        Set<BlockPos> speakers = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        while (!queue.isEmpty() && speakers.size() < cap) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                if (portable && at.equals(pos) && side != Direction.DOWN) continue;
                BlockPos next = at.relative(side);
                if (speakers.size() < cap && level.isLoaded(next) && level.getBlockState(next).is(WorldRadio.SPEAKER)
                        && speakers.add(next)) {
                    queue.add(next);
                }
            }
        }
        return speakers;
    }

    private boolean antennaChanged() {
        for (Node node : data.nodes()) {
            if ((node.kind() == Kind.TRANSMITTER || node.kind() == Kind.AMPLIFIER) && level.isLoaded(node.pos())
                    && node.antenna() != antennaCount(node.pos())) {
                return true;
            }
        }
        return false;
    }

    /** {@code antenna} and {@code range} are for transmitters and amplifiers; a radio brings its own two numbers. */
    private static Node nodeOf(BlockEntity be, int antenna, int range) {
        BlockPos pos = be.getBlockPos().immutable();
        if (be instanceof ReceiverBlockEntity radio) {
            return new Node(Kind.RECEIVER, pos, radio.url(), radio.name(), radio.enabled(), radio.antenna(), radio.range(),
                    radio.volume());
        }
        if (be instanceof ChannelBlockEntity channel) {
            return new Node(Kind.CHANNEL, pos, channel.url(), channel.name(), channel.enabled(), 0, 0, 1.0f);
        }
        if (be instanceof RadioBlockEntity radio) {
            return new Node(Kind.TRANSMITTER, pos, radio.url(), radio.name(), radio.enabled(), antenna, range, 1.0f);
        }
        if (be instanceof AmplifierBlockEntity) return new Node(Kind.AMPLIFIER, pos, "", "", true, antenna, range, 1.0f);
        return null;
    }

    private void recompute() {
        // what every block is now: loaded ones from the world, unloaded ones as last known, with the current config
        Map<BlockPos, Node> nodes = new HashMap<>();
        Map<BlockPos, BlockEntity> entities = new HashMap<>();
        Map<BlockPos, Set<BlockPos>> chains = new HashMap<>();
        for (Node known : List.copyOf(data.nodes())) {
            BlockPos pos = known.pos();
            Node node;
            if (level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                Node now = be == null || be.isRemoved() ? null : nodeOf(be, 0, 0);
                if (now == null) {
                    data.remove(pos); // gone without us hearing of it (e.g. a /fill that skips side effects)
                    continue;
                }
                node = switch (now.kind()) {
                    case RECEIVER -> {
                        Set<BlockPos> chain = speakers(pos);
                        chains.put(pos, chain);
                        ((ReceiverBlockEntity) be).setRange(Config.hearing(chain.size()), chain.size());
                        yield nodeOf(be, 0, 0);
                    }
                    case CHANNEL -> now;
                    case TRANSMITTER, AMPLIFIER -> {
                        int antenna = antennaCount(pos);
                        yield nodeOf(be, antenna, Config.range(antenna));
                    }
                };
                entities.put(pos, be);
            } else {
                int range = switch (known.kind()) {
                    case RECEIVER -> Config.hearing(known.antenna());
                    case CHANNEL -> 0;
                    case TRANSMITTER, AMPLIFIER -> Config.range(known.antenna());
                };
                node = new Node(known.kind(), pos, known.url(), known.name(), known.enabled(), known.antenna(), range,
                        known.volume());
            }
            data.put(node);
            nodes.put(pos, node);
        }

        // a transmitter sends its own station and those of the channels connected to it: the ones touching it and,
        // from channel to touching channel, the whole chain (a switched-off or untuned channel still passes the chain
        // on). Switched off, the transmitter sends nothing.
        List<SignalGraph.Radio> stations = new ArrayList<>();
        Map<BlockPos, Node> hosts = new HashMap<>(); // per connected channel its transmitter, a switched-on one first
        Set<BlockPos> sending = new HashSet<>();
        for (Node node : nodes.values()) {
            if (node.kind() != Kind.TRANSMITTER) continue;
            int before = stations.size();
            Set<String> urls = new HashSet<>();
            if (node.enabled() && urls.add(node.url())) addStation(stations, node, node);
            Set<BlockPos> chain = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(node.pos()));
            while (!queue.isEmpty()) {
                BlockPos at = queue.poll();
                for (Direction side : Direction.values()) {
                    BlockPos next = at.relative(side);
                    Node channel = nodes.get(next);
                    if (channel == null || channel.kind() != Kind.CHANNEL || !chain.add(next)) continue;
                    queue.add(next);
                    Node host = hosts.get(next);
                    if (host == null || node.enabled() && !host.enabled()) hosts.put(next, node);
                    if (!node.enabled() || !channel.tuned()) continue;
                    sending.add(next);
                    // the same station twice is sent once; the sources packet carries 64 stations per transmitter
                    if (stations.size() - before < Packets.MAX_STATIONS && urls.add(channel.url())) {
                        addStation(stations, node, channel);
                    }
                }
            }
            if (entities.get(node.pos()) instanceof RadioBlockEntity radio) {
                radio.setRange(node.range(), node.antenna());
                setLevel(node.pos(), Antenna.level(stations.size() > before, node.antenna()));
            }
        }
        List<SignalGraph.Amplifier> amps = new ArrayList<>();
        List<Node> receivers = new ArrayList<>();
        for (Node node : nodes.values()) {
            BlockPos pos = node.pos();
            switch (node.kind()) {
                case TRANSMITTER -> {
                }
                case CHANNEL -> {
                    // for its screen and its front: the transmitter it sends through, if any
                    Node host = hosts.get(pos);
                    if (entities.get(pos) instanceof ChannelBlockEntity channel) {
                        channel.setRange(host == null ? 0 : host.range(), host == null ? 0 : host.antenna());
                        setLevel(pos, sending.contains(pos) ? 1 : 0);
                    }
                }
                case AMPLIFIER -> amps.add(new SignalGraph.Amplifier(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(), node.range()));
                case RECEIVER -> receivers.add(node);
            }
        }

        // a radio takes part as an amplifier with range 0: it receives and passes nothing on
        List<SignalGraph.Amplifier> listeners = new ArrayList<>(amps);
        for (Node r : receivers) {
            listeners.add(new SignalGraph.Amplifier(r.pos().asLong(), r.pos().getX(), r.pos().getY(), r.pos().getZ(), 0));
        }
        Map<Long, List<Signal>> signals = SignalGraph.compute(stations, listeners);
        for (SignalGraph.Amplifier amp : amps) {
            BlockPos pos = BlockPos.of(amp.id());
            if (!(entities.get(pos) instanceof AmplifierBlockEntity be)) continue;
            List<Signal> received = signals.getOrDefault(amp.id(), List.of());
            int antenna = nodes.get(pos).antenna();
            be.update(received, amp.range(), antenna);
            setLevel(pos, Antenna.level(!received.isEmpty(), antenna));
        }
        List<Packets.RadioSource> playing = new ArrayList<>();
        Set<BlockPos> lit = new HashSet<>();
        for (Node r : receivers) {
            List<Signal> received = signals.getOrDefault(r.pos().asLong(), List.of());
            boolean plays = r.tuned() && received.stream().anyMatch(s -> s.url().equals(r.url()));
            if (plays) lit.addAll(chains.getOrDefault(r.pos(), Set.of()));
            if (entities.get(r.pos()) instanceof ReceiverBlockEntity be) {
                be.update(received);
                setLevel(r.pos(), plays ? 3 : 0);
            }
            if (plays && playing.size() < Packets.MAX_SOURCES) {
                playing.add(new Packets.RadioSource(r.pos(), r.url(), r.range(), r.volume()));
            }
        }
        // the speakers of playing radios show it; those that no longer belong to one go dark
        for (BlockPos pos : litSpeakers) if (!lit.contains(pos)) setLit(pos, false);
        for (BlockPos pos : lit) setLit(pos, true);
        litSpeakers = lit;
        publish(playing, stations, amps, signals);
    }

    private void setLit(BlockPos pos, boolean value) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.is(WorldRadio.SPEAKER) && state.getValue(SpeakerBlock.LIT) != value) {
            level.setBlock(pos, state.setValue(SpeakerBlock.LIT, value), Block.UPDATE_CLIENTS);
        }
    }

    /** {@code source}'s station (the transmitter itself or one of its channels), sent from {@code transmitter}. */
    private static void addStation(List<SignalGraph.Radio> stations, Node transmitter, Node source) {
        if (source.url().isEmpty() || transmitter.range() <= 0) return;
        BlockPos pos = transmitter.pos();
        stations.add(new SignalGraph.Radio(source.pos().asLong(), pos.getX(), pos.getY(), pos.getZ(), transmitter.range(),
                source.url(), source.name()));
    }

    /** What plays and what sends in this level to its players, when it changed. */
    private void publish(List<Packets.RadioSource> playing, List<SignalGraph.Radio> stations, List<SignalGraph.Amplifier> amps,
                         Map<Long, List<Signal>> signals) {
        // one emitter per transmitter with all its stations
        Map<Long, List<Reception.Station>> sending = new LinkedHashMap<>();
        Map<Long, Integer> ranges = new HashMap<>();
        for (SignalGraph.Radio s : stations) {
            long at = BlockPos.asLong(s.x(), s.y(), s.z());
            sending.computeIfAbsent(at, k -> new ArrayList<>()).add(station(s.url(), s.name()));
            ranges.put(at, s.range());
        }
        for (SignalGraph.Amplifier a : amps) {
            List<Signal> received = signals.getOrDefault(a.id(), List.of());
            if (received.isEmpty() || a.range() <= 0) continue;
            List<Reception.Station> list = new ArrayList<>();
            for (Signal s : received.subList(0, Math.min(Packets.MAX_STATIONS, received.size()))) {
                list.add(station(s.url(), s.name()));
            }
            sending.put(a.id(), list);
            ranges.put(a.id(), a.range());
        }
        List<Reception.Emitter> emitters = new ArrayList<>();
        for (Map.Entry<Long, List<Reception.Station>> e : sending.entrySet()) {
            if (emitters.size() >= Packets.MAX_SOURCES) break;
            BlockPos pos = BlockPos.of(e.getKey());
            emitters.add(new Reception.Emitter(pos.getX(), pos.getY(), pos.getZ(), ranges.get(e.getKey()), e.getValue()));
        }
        playing.sort(Comparator.comparingLong(r -> r.pos().asLong()));
        emitters.sort(Comparator.comparingLong(e -> BlockPos.asLong(e.x(), e.y(), e.z())));
        Packets.Sources sources = new Packets.Sources(level.dimension().identifier(), playing, emitters);
        if (sources.equals(sent)) return;
        sent = sources;
        for (ServerPlayer player : level.players()) ServerPlayNetworking.send(player, sources);
    }

    private static Reception.Station station(String url, String name) {
        return new Reception.Station(url, name.length() > Packets.MAX_NAME ? name.substring(0, Packets.MAX_NAME) : name);
    }

    /** The display level of the model; only written when it changes, and only sent to the clients. */
    private void setLevel(BlockPos pos, int value) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(SignalBlock.LEVEL) && state.getValue(SignalBlock.LEVEL) != value) {
            level.setBlock(pos, state.setValue(SignalBlock.LEVEL, value), Block.UPDATE_CLIENTS);
        }
    }
}
