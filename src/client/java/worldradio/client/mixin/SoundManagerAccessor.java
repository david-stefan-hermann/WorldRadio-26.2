package worldradio.client.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The way to a playing sound's channel, see {@link SoundEngineAccessor}. */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("soundEngine")
    SoundEngine worldradio$engine();
}
