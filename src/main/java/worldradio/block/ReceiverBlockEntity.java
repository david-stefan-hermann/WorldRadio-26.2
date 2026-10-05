package worldradio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.signal.Signal;

import java.util.ArrayList;
import java.util.List;

/**
 * A radio that receives: the station it is set to, on/off and volume as in {@link RadioBlockEntity}; {@code range} is
 * how far it can be heard and {@code antenna} the number of speakers connected to it, and {@code signals} are the
 * stations arriving here. All three are worked out by {@link worldradio.server.RadioNetwork} and synced to the clients
 * for the tuner screen.
 */
public class ReceiverBlockEntity extends RadioBlockEntity {
    private List<Signal> signals = List.of();

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(WorldRadio.RECEIVER_BLOCK_ENTITY, pos, state);
        setRange(Config.hearing(0), 0);
    }

    public List<Signal> signals() {
        return signals;
    }

    /** Switched on, tuned and the station's signal arrives. */
    public boolean plays() {
        return sends() && signals.stream().anyMatch(s -> s.url().equals(url()));
    }

    /** Server side; syncs to the clients only when something changed. */
    public void update(List<Signal> newSignals) {
        if (newSignals.equals(signals)) return;
        signals = List.copyOf(newSignals);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("signals", AmplifierBlockEntity.SIGNAL_CODEC.listOf(), signals);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        signals = new ArrayList<>(in.read("signals", AmplifierBlockEntity.SIGNAL_CODEC.listOf()).orElse(List.of()));
    }
}
