package worldradio.client.mixin;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import worldradio.client.audio.RadioSoundInstance;

import java.util.concurrent.CompletableFuture;

/**
 * Vanilla opens every streamed sound as an .ogg file from the resource packs. A radio sound's path names the playing
 * {@link RadioSoundInstance} instead, and gets that instance's live stream.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void worldradio$radioStream(Identifier location, boolean looping,
                                        CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        AudioStream stream = RadioSoundInstance.streamFor(location);
        if (stream != null) cir.setReturnValue(CompletableFuture.completedFuture(stream));
    }
}
