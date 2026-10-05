package worldradio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The portable radio put down (sneak + right-click with the item): a radio like the {@link ReceiverBlock} in a small
 * case, with the same tuner and hearing range; of the speakers around it only the one it stands on connects (see
 * {@code RadioNetwork.speakers}). It takes the
 * item's setting and gives it back when it is broken, which a bare hand does at once (see {@link ReceiverBlockEntity}).
 */
public class PortableRadioBlock extends ReceiverBlock {
    public static final MapCodec<PortableRadioBlock> CODEC = simpleCodec(PortableRadioBlock::new);
    /** The case, 12 wide, 7 high and 6 deep, for a front to the north or south and to the east or west. */
    private static final VoxelShape ALONG_X = box(2, 0, 5, 14, 7, 11), ALONG_Z = box(5, 0, 2, 11, 7, 14);

    public PortableRadioBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PortableRadioBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_X : ALONG_Z;
    }
}
