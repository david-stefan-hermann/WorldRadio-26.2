package worldradio.client.mixin;

import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Adds {@code RADIO("radio")} at the end of the sound categories, so the options get a "Radio" slider in Music &
 * Sounds (the screen lists {@code SoundSource.values()}) and options.txt stores {@code soundCategory_radio}. Appended
 * last, so the ordinals of the vanilla categories (sent in sound packets) stay the same.
 */
@Mixin(SoundSource.class)
public abstract class SoundSourceMixin {
    @Shadow
    @Final
    @Mutable
    private static SoundSource[] $VALUES;

    @Invoker("<init>")
    private static SoundSource worldradio$create(String constant, int ordinal, String name) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void worldradio$addRadio(CallbackInfo ci) {
        SoundSource[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
        values[values.length - 1] = worldradio$create("RADIO", values.length - 1, "radio");
        $VALUES = values;
    }
}
