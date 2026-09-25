package worldradio.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import worldradio.WorldRadio;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's shared favourite stations of 0.1 to 0.4. Favourites belong to each player since 0.5 (kept on their
 * computer); this list is only read, so players can take its stations over.
 */
public final class FavouritesData extends SavedData {
    public static final int MAX = 200;

    public record Favourite(String name, String url, String country) {
        public static final Codec<Favourite> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Favourite::name),
                Codec.STRING.fieldOf("url").forGetter(Favourite::url),
                Codec.STRING.optionalFieldOf("country", "").forGetter(Favourite::country)
        ).apply(i, Favourite::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Favourite> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Favourite::name,
                ByteBufCodecs.stringUtf8(512), Favourite::url,
                ByteBufCodecs.stringUtf8(8), Favourite::country,
                Favourite::new);
    }

    private static final Codec<FavouritesData> CODEC = Favourite.CODEC.listOf().fieldOf("favourites")
            .xmap(FavouritesData::new, data -> data.favourites).codec();
    public static final SavedDataType<FavouritesData> TYPE = new SavedDataType<>(WorldRadio.id("favourites"),
            FavouritesData::new, CODEC, null);

    private final List<Favourite> favourites;

    public FavouritesData() {
        this(List.of());
    }

    private FavouritesData(List<Favourite> favourites) {
        this.favourites = new ArrayList<>(favourites);
    }

    public static FavouritesData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Favourite> list() {
        return List.copyOf(favourites);
    }

}
