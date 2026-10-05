package worldradio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import worldradio.WorldRadio;

/**
 * The station of a channel block, tuned and switched as in {@link RadioBlockEntity}. {@code range} and {@code antenna}
 * are those of the transmitter it sends through (range 0: it is connected to none), set by
 * {@link worldradio.server.RadioNetwork} for the screen.
 */
public class ChannelBlockEntity extends RadioBlockEntity {
    public ChannelBlockEntity(BlockPos pos, BlockState state) {
        super(WorldRadio.CHANNEL_BLOCK_ENTITY, pos, state);
    }
}
