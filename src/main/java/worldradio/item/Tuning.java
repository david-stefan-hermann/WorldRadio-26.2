package worldradio.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a portable radio is set to: the station, whether it is switched on and its volume (0..1). */
public record Tuning(String url, String name, boolean enabled, float volume) {
    public static final Tuning NONE = new Tuning("", "", true, 1.0f);

    public static final Codec<Tuning> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("url", "").forGetter(Tuning::url),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Tuning::name),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Tuning::enabled),
            Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(Tuning::volume)
    ).apply(i, Tuning::new));

    public static final StreamCodec<ByteBuf, Tuning> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(512), Tuning::url,
            ByteBufCodecs.stringUtf8(256), Tuning::name,
            ByteBufCodecs.BOOL, Tuning::enabled,
            ByteBufCodecs.FLOAT, Tuning::volume,
            Tuning::new);

    /** Switched on and tuned. */
    public boolean plays() {
        return enabled && !url.isEmpty();
    }
}
