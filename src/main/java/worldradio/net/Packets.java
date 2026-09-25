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
import worldradio.WorldRadio;
import worldradio.block.RadioBlock;
import worldradio.block.RadioBlockEntity;
import worldradio.server.FavouritesData;
import worldradio.server.FavouritesData.Favourite;
import worldradio.server.RadioNetwork;

import java.util.List;
import java.util.Locale;

/**
 * The packets: tune a radio, switch it on/off, set its volume (client → server), the sound sources of the player's
 * dimension and a world's old shared favourites list, which the client takes over once (server → client).
 */
public final class Packets {
    public static final int MAX_URL = 512;
    /** How close a player must stand to a radio to tune it. */
    public static final double REACH = 8.0;
    /** Radios or amplifiers per dimension the sources packet carries at most. */
    public static final int MAX_SOURCES = 4096;

    private Packets() {
    }

    public record SetStation(BlockPos pos, String url, String name, String country) implements CustomPacketPayload {
        public static final Type<SetStation> TYPE = new Type<>(WorldRadio.id("set_station"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStation> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetStation::pos,
                ByteBufCodecs.stringUtf8(MAX_URL), SetStation::url,
                ByteBufCodecs.stringUtf8(256), SetStation::name,
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

    /** A radio that plays: where it stands, its station, range and volume (0..1). */
    public record RadioSource(BlockPos pos, String url, int range, float volume) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RadioSource> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RadioSource::pos,
                ByteBufCodecs.stringUtf8(MAX_URL), RadioSource::url,
                ByteBufCodecs.VAR_INT, RadioSource::range,
                ByteBufCodecs.FLOAT, RadioSource::volume,
                RadioSource::new);
    }

    /** One station an amplifier re-sends, with its share of the amplifier's volume (times the radio's own volume). */
    public record AmpSignal(String url, float factor) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AmpSignal> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_URL), AmpSignal::url,
                ByteBufCodecs.FLOAT, AmpSignal::factor,
                AmpSignal::new);
    }

    /** An amplifier that re-sends at least one station. */
    public record AmpSource(BlockPos pos, int range, List<AmpSignal> signals) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AmpSource> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AmpSource::pos,
                ByteBufCodecs.VAR_INT, AmpSource::range,
                AmpSignal.CODEC.apply(ByteBufCodecs.list(64)), AmpSource::signals,
                AmpSource::new);
    }

    /**
     * Everything that can be heard in one dimension, loaded or not; the client works out volume and direction itself.
     * Sent whenever it changes and when a player joins or changes dimension.
     */
    public record Sources(Identifier dimension, List<RadioSource> radios, List<AmpSource> amplifiers)
            implements CustomPacketPayload {
        public static final Type<Sources> TYPE = new Type<>(WorldRadio.id("sources"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sources> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Sources::dimension,
                RadioSource.CODEC.apply(ByteBufCodecs.list(MAX_SOURCES)), Sources::radios,
                AmpSource.CODEC.apply(ByteBufCodecs.list(MAX_SOURCES)), Sources::amplifiers,
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
        PayloadTypeRegistry.clientboundPlay().register(FavouritesSync.TYPE, FavouritesSync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sources.TYPE, Sources.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetStation.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REACH * REACH) return;
            if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) return;
            String url = payload.url().trim();
            if (!url.isEmpty() && !isStreamUrl(url)) return;
            radio.setStation(url, clean(payload.name(), 256), clean(payload.country(), 8));
            RadioNetwork.get(level).markDirty();
            WorldRadio.LOGGER.info("Radio at {} tuned to {} ({}) by {}", pos.toShortString(), url, payload.name(),
                    player.getName().getString());
        });
        ServerPlayNetworking.registerGlobalReceiver(SetEnabled.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REACH * REACH) return;
            if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio) || radio.enabled() == payload.enabled()) return;
            RadioBlock.toggle(level, pos, radio, null);
        });
        ServerPlayNetworking.registerGlobalReceiver(SetVolume.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REACH * REACH) return;
            if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio) || Float.isNaN(payload.volume())) return;
            radio.setVolume(payload.volume());
            RadioNetwork.get(level).markDirty();
        });
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
