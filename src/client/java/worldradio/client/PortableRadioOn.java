package worldradio.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import worldradio.WorldRadio;
import worldradio.item.Tuning;

/**
 * Item model condition {@code worldradio:portable_radio_on}: the portable radio is switched on and tuned, so its item
 * shows the lit model with the antenna pulled out (items/portable_radio.json). Whether a signal arrives is not asked.
 */
public record PortableRadioOn() implements ConditionalItemModelProperty {
    public static final MapCodec<PortableRadioOn> MAP_CODEC = MapCodec.unit(new PortableRadioOn());

    @Override
    public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext context) {
        return stack.getOrDefault(WorldRadio.TUNING, Tuning.NONE).plays();
    }

    @Override
    public MapCodec<PortableRadioOn> type() {
        return MAP_CODEC;
    }
}
