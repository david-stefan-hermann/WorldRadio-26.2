package worldradio.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The conditions an item model definition can ask for ({@code "type": "minecraft:condition"}), to add our own. */
@Mixin(ConditionalItemModelProperties.class)
public interface ConditionalItemModelPropertiesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ConditionalItemModelProperty>> worldradio$idMapper() {
        throw new AssertionError();
    }
}
