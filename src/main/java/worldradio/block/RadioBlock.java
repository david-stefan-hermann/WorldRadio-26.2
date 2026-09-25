package worldradio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import worldradio.server.RadioNetwork;

/**
 * The only real sound source: plays the station stored in its block entity to everyone within its range. Sneak +
 * right-click switches it on or off (the station stays); vanilla only lets that through with an empty hand.
 */
public class RadioBlock extends SignalBlock {
    public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);

    public RadioBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RadioBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) return super.useWithoutItem(state, level, pos, player, hit);
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            toggle(serverLevel, pos, radio, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Switches the radio on or off with a click and a message above the hotbar. */
    public static void toggle(ServerLevel level, BlockPos pos, RadioBlockEntity radio, Player player) {
        boolean on = !radio.enabled();
        radio.setEnabled(on);
        RadioNetwork.get(level).markDirty();
        level.playSound(null, pos, on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.BLOCKS, 0.6f, on ? 1.0f : 0.8f);
        if (player != null) player.sendOverlayMessage(Component.translatable(on ? "worldradio.toggle.on" : "worldradio.toggle.off"));
    }
}
