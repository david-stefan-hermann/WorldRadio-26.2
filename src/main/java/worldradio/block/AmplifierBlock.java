package worldradio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Re-emits every radio signal that reaches it, with its own range (see {@link worldradio.signal.SignalGraph}). */
public class AmplifierBlock extends SignalBlock {
    public AmplifierBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmplifierBlockEntity(pos, state);
    }
}
