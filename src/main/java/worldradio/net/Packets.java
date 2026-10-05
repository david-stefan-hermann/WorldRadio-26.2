package worldradio.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import worldradio.WorldRadio;
import worldradio.block.RadioBlock;
import worldradio.block.RadioBlockEntity;
import worldradio.block.ReceiverBlockEntity;
import worldradio.item.PortableRadioItem;
import worldradio.item.Tuning;
import worldradio.server.FavouritesData;
import worldradio.server.FavouritesData.Favourite;
import worldradio.server.RadioNetwork;
import worldradio.signal.Reception;

import java.util.List;
import java.util.Locale;

/**
 * The packets: tune a transmitter, channel or radio, switch it on/off, set a radio's volume, set the portable
 * radio in a hand (client → server); what plays and what sends in the player's dimension and a world's old shared
 * favourites list, which the client takes over once (server → client).
 */
public final class Packets {
    public static final int MAX_URL = 512;
    public static final int MAX_NAME = 256;
    /** How close a player must stand to a block to change it. */
    public static final double REACH = 8.0;
    /** Radios or emitters per dimension the sources packet carries at most. */
    public static final int MAX_SOURCES = 4096;
    /** Stations per amplifier the sources packet carries at most. */
    public static final int MAX_STATIONS = 64;

    private Packets() {
    }

    public record SetStation(BlockPos pos, String url, String name, String country) implements CustomPacketPayload {
        public static final Type<SetStation> TYPE = new Type<>(WorldRadio.id("set_station"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStation> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetStation::pos,
                ByteBufCodecs.stringUtf8(MAX_URL), SetStation::url,
                ByteBufCodecs.stringUtf8(MAX_NAME), SetStation::name,
                ByteBufCodecs.stringUtf8(8), SetStation::country,
                SetStation::new);

        @Override
        public Type<SetStation> type() {
            return TYPE;
        }
    }

    public record SetEnabled(BlockPos pos, boolean enabled) implements CustomPacketPayload {
        public static final Type<SetEnabled> TYPE = new Type<>(WorldRadio.id("set_enabled"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetEnabled> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetEnabled::pos,
                ByteBufCodecs.BOOL, SetEnabled::enabled,
                SetEnabled::new);

        @Override
        public Type<SetEnabled> type() {
            return TYPE;
        }
    }

    public record SetVolume(BlockPos pos, float volume) implements CustomPacketPayload {
        public static final Type<SetVolume> TYPE = new Type<>(WorldRadio.id("set_volume"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetVolume> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetVolume::pos,
                ByteBufCodecs.FLOAT, SetVolume::volume,
                SetVolume::new);

        @Override
        public Type<SetVolume> type() {
            return TYPE;
        }
    }

    /** The new setting of the portable radio the player holds in {@code hand}. */
    public record TunePortable(InteractionHand hand, Tuning tuning) implements CustomPacketPayload {
        public static final Type<TunePortable> TYPE = new Type<>(WorldRadio.id("tune_portable"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TunePortable> CODEC = StreamCodec.composite(
                InteractionHand.STREAM_CODEC, TunePortable::hand,
                Tuning.STREAM_CODEC, TunePortable::tuning,
                TunePortable::new);

        @Override
        public Type<TunePortable> type() {
            return TYPE;
        }
    }

    /** The world's shared favourites of 0.1 to 0.4; the client takes each station over once. */
    public record FavouritesSync(List<Favourite> favourites) implements CustomPacketPayload {
        public static final Type<FavouritesSync> TYPE = new Type<>(WorldRadio.id("favourites"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FavouritesSync> CODEC = StreamCodec.composite(
                Favourite.STREAM_CODEC.apply(ByteBufCodecs.list(FavouritesData.MAX)), FavouritesSync::favourites,
                FavouritesSync::new);

        @Override
        public Type<FavouritesSync> type() {
            return TYPE;
        }
    }

    /** A radio that plays: where it stands, its station, how far it can be heard and its volume (0..1). */
    public record RadioSource(BlockPos pos, String url, int range, float volume) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RadioSource> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RadioSource::pos,
                ByteBufCodecs.stringUtf8(MAX_URL), RadioSource::url,
                ByteBufCodecs.VAR_INT, RadioSource::range,
                ByteBufCodecs.FLOAT, RadioSource::volume,
                RadioSource::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Reception.Station> STATION_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_URL), Reception.Station::url,
            ByteBufCodecs.stringUtf8(MAX_NAME), Reception.Station::name,
            Reception.Station::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Reception.Emitter> EMITTER_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, e -> new BlockPos(e.x(), e.y(), e.z()),
            ByteBufCodecs.VAR_INT, Reception.Emitter::range,
            STATION_CODEC.apply(ByteBufCodecs.list(MAX_STATIONS)), Reception.Emitter::stations,
            (pos, range, stations) -> new Reception.Emitter(pos.getX(), pos.getY(), pos.getZ(), range, stations));

    /**
     * One dimension, loaded or not: the radios that play (the client works out volume and direction itself) and the
     * transmitters and amplifiers with the stations they send (for portable radios). Sent whenever it changes and when
     * a player joins or changes dimension.
     */
    // ponytail: every emitter repeats its stations' addresses and names, and the whole list is resent on any change;
    // a station table plus per-emitter indices (or deltas) if networks grow to hundreds of amplifiers.
    public record Sources(Identifier dimension, List<RadioSource> receivers, List<Reception.Emitter> emitters)
            implements CustomPacketPayload {
        public static final Type<Sources> TYPE = new Type<>(WorldRadio.id("sources"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sources> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Sources::dimension,
                RadioSource.CODEC.apply(ByteBufCodecs.list(MAX_SOURCES)), Sources::receivers,
                EMITTER_CODEC.apply(ByteBufCodecs.list(MAX_SOURCES)), Sources::emitters,
                Sources::new);

        @Override
        public Type<Sources> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(SetStation.TYPE, SetStation.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetEnabled.TYPE, SetEnabled.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetVolume.TYPE, SetVolume.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TunePortable.TYPE, TunePortable.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FavouritesSync.TYPE, FavouritesSync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sources.TYPE, Sources.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetStation.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!(inReach(player, payload.pos()) instanceof RadioBlockEntity radio)) return;
            String url = payload.url().trim();
            if (!url.isEmpty() && !isStreamUrl(url)) return;
            radio.setStation(url, clean(payload.name(), MAX_NAME), clean(payload.country(), 8));
            RadioNetwork.get((ServerLevel) player.level()).markDirty();
            WorldRadio.LOGGER.info("Radio at {} tuned to {} ({}) by {}", payload.pos().toShortString(), url, payload.name(),
                    player.getName().getString());
        });
        ServerPlayNetworking.registerGlobalReceiver(SetEnabled.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!(inReach(player, payload.pos()) instanceof RadioBlockEntity radio) || radio.enabled() == payload.enabled()) return;
            RadioBlock.toggle((ServerLevel) player.level(), payload.pos(), radio, null);
        });
        ServerPlayNetworking.registerGlobalReceiver(SetVolume.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!(inReach(player, payload.pos()) instanceof ReceiverBlockEntity radio) || Float.isNaN(payload.volume())) return;
            radio.setVolume(payload.volume());
            RadioNetwork.get((ServerLevel) player.level()).markDirty();
        });
        ServerPlayNetworking.registerGlobalReceiver(TunePortable.TYPE, (payload, context) -> {
            ItemStack stack = context.player().getItemInHand(payload.hand());
            Tuning tuning = payload.tuning();
            String url = tuning.url().trim();
            if (!(stack.getItem() instanceof PortableRadioItem) || Float.isNaN(tuning.volume())) return;
            if (!url.isEmpty() && !isStreamUrl(url)) return;
            stack.set(WorldRadio.TUNING, new Tuning(url, clean(tuning.name(), MAX_NAME), tuning.enabled(),
                    Math.clamp(tuning.volume(), 0.0f, 1.0f)));
        });
    }

    /** The block entity at {@code pos} when it is loaded and the player stands close enough to change it. */
    private static BlockEntity inReach(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) return null;
        return player.level().getBlockEntity(pos);
    }

    /** On join: the world's old shared list, if it has one, for the client to take over. */
    public static void sendFavourites(ServerPlayer player) {
        List<Favourite> legacy = FavouritesData.get(player.level().getServer()).list();
        if (!legacy.isEmpty()) ServerPlayNetworking.send(player, new FavouritesSync(legacy));
    }

    public static boolean isStreamUrl(String url) {
        if (url.length() > MAX_URL) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://")) && url.length() > 10 && !url.contains(" ");
    }

    private static String clean(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
