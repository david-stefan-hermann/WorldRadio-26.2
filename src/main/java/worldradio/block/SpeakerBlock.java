package worldradio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import worldradio.server.RadioNetwork;

/**
 * Makes a radio heard further: every speaker touching a radio, or touching another speaker of such a chain, adds to
 * that radio's hearing range (found by {@link RadioNetwork#speakers}). The sound still comes from the radio.
 */
public class SpeakerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);

    /** Set by the network while a radio this speaker is connected to plays: the front shows the moving ring. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public SpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<SpeakerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** A speaker anywhere in a chain changes its radio, which need not be a neighbour: the network recounts. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) RadioNetwork.get(serverLevel).markDirty();
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        RadioNetwork.get(level).markDirty();
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
