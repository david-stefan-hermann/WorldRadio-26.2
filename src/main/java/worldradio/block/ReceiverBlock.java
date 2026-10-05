package worldradio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The radio players listen to ("Radio" in the game; the id {@code worldradio:radio} stays with the transmitter): it
 * picks one of the stations whose signal reaches it and plays it to everyone within its hearing range, which grows
 * with the {@link SpeakerBlock}s connected to it. Sneak + right-click switches it like the transmitter.
 */
public class ReceiverBlock extends RadioBlock {
    public static final MapCodec<ReceiverBlock> CODEC = simpleCodec(ReceiverBlock::new);

    public ReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ReceiverBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReceiverBlockEntity(pos, state);
    }

    @Override
    protected boolean takesAntenna() {
        return false;
    }
}
