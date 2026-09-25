package worldradio.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import worldradio.WorldRadio;
import worldradio.signal.Signal;

import java.util.ArrayList;
import java.util.List;

/**
 * The signals reaching an amplifier, its range and the antenna blocks on top of it. All are worked out by
 * {@link worldradio.server.RadioNetwork} and synced to the clients, which play each signal from here.
 */
public class AmplifierBlockEntity extends BlockEntity {
    public static final Codec<Signal> SIGNAL_CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("radio").forGetter(s -> new BlockPos(s.radioX(), s.radioY(), s.radioZ())),
            Codec.STRING.fieldOf("url").forGetter(Signal::url),
            Codec.STRING.fieldOf("name").forGetter(Signal::name),
            Codec.DOUBLE.fieldOf("distance").forGetter(Signal::distance),
            Codec.INT.fieldOf("hops").forGetter(Signal::hops),
            Codec.DOUBLE.fieldOf("factor").forGetter(Signal::factor)
    ).apply(i, (pos, url, name, distance, hops, factor) -> new Signal(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(),
            url, name, distance, hops, factor)));

    private List<Signal> signals = List.of();
    private int range;
    private int antenna;

    public AmplifierBlockEntity(BlockPos pos, BlockState state) {
        super(WorldRadio.AMPLIFIER_BLOCK_ENTITY, pos, state);
    }

    public List<Signal> signals() {
        return signals;
    }

    public int range() {
        return range;
    }

    public int antenna() {
        return antenna;
    }

    /** Server side; syncs to the clients only when something changed. */
    public void update(List<Signal> newSignals, int newRange, int newAntenna) {
        if (newRange == range && newAntenna == antenna && newSignals.equals(signals)) return;
        signals = List.copyOf(newSignals);
        range = newRange;
        antenna = newAntenna;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("range", range);
        out.putInt("antenna", antenna);
        out.store("signals", SIGNAL_CODEC.listOf(), signals);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        range = in.getIntOr("range", 0);
        antenna = in.getIntOr("antenna", 0);
        signals = new ArrayList<>(in.read("signals", SIGNAL_CODEC.listOf()).orElse(List.of()));
    }

    /** Called when the block is broken or replaced (not when its chunk unloads). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            worldradio.server.RadioNetwork.get(serverLevel).removed(pos);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
