package worldradio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import worldradio.WorldRadio;
import worldradio.server.RadioNetwork;
import worldradio.signal.Antenna;

/**
 * Radio and amplifier share this: a facing, a display level 0..3 that the server sets from the station/signals and the
 * antenna column on top (see {@link Antenna#level}), and right-click to open the block's screen (on the client, see
 * {@link #screenOpener}), with or without an item in hand.
 */
public abstract class SignalBlock extends BaseEntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Blocks that count as antenna when stacked on top; packs can add more. */
    public static final TagKey<Block> ANTENNA = TagKey.create(Registries.BLOCK, WorldRadio.id("antenna"));

    /** Set by the client entry point; the server side never opens screens. */
    public static ScreenOpener screenOpener = (level, pos) -> {
    };

    @FunctionalInterface
    public interface ScreenOpener {
        void open(Level level, BlockPos pos);
    }

    protected SignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * An antenna block clicked onto the top face is placed there (the first block of a column); any other item falls
     * through to {@link #useWithoutItem} and opens the screen.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() == Direction.UP && stack.getItem() instanceof BlockItem item
                && isAntenna(item.getBlock().defaultBlockState())) {
            return InteractionResult.PASS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) screenOpener.open(level, pos);
        else onOpened(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    /** Server side of the right-click (the client opens the screen itself). */
    protected void onOpened(Level level, BlockPos pos, Player player) {
    }

    /** The first antenna block placed on top (or taken away) counts at once; the network rescans higher ones itself. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation,
                                   boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) RadioNetwork.get(serverLevel).markDirty();
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) {
            RadioNetwork.get(serverLevel).markDirty();
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    public static boolean isAntenna(BlockState state) {
        return state.is(ANTENNA);
    }
}
