package worldradio.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import worldradio.WorldRadio;

import java.util.function.Consumer;

/**
 * A radio to carry: it plays its station to the player who has it anywhere in the inventory, as long as that station's
 * signal reaches the player (the client works that out, see the client's SourceTracker). Right-click opens the tuner,
 * sneak + right-click switches it on or off. The setting is the stack's {@link Tuning} component.
 */
public class PortableRadioItem extends Item {
    /** Set by the client entry point; the server side never opens screens. */
    public static Consumer<InteractionHand> screenOpener = hand -> {
    };

    public PortableRadioItem(Properties properties) {
        super(properties);
    }

    public static Tuning tuning(ItemStack stack) {
        return stack.getOrDefault(WorldRadio.TUNING, Tuning.NONE);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            if (level.isClientSide()) screenOpener.accept(hand);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            Tuning old = tuning(stack);
            boolean on = !old.enabled();
            stack.set(WorldRadio.TUNING, new Tuning(old.url(), old.name(), on, old.volume()));
            level.playSound(null, player.blockPosition(), on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.PLAYERS, 0.6f, on ? 1.0f : 0.8f);
            player.sendOverlayMessage(Component.translatable(on ? "worldradio.toggle.on" : "worldradio.toggle.off"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
                                TooltipFlag flag) {
        Tuning tuning = tuning(stack);
        if (tuning.url().isEmpty()) return;
        lines.accept(Component.literal(tuning.name().isEmpty() ? tuning.url() : tuning.name()).withStyle(ChatFormatting.GRAY));
        if (!tuning.enabled()) lines.accept(Component.translatable("worldradio.toggle.off").withStyle(ChatFormatting.DARK_GRAY));
    }
}
