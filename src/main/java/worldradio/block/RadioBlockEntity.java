package worldradio.block;

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

/**
 * The station a radio plays (stream address, display name, country code), whether it is switched on, its range in
 * blocks, the antenna blocks on top of it and its volume (0..1, for everyone who hears it). Switching off keeps the
 * station. The server works out both numbers from the antenna column and its config, so clients need no config.
 */
public class RadioBlockEntity extends BlockEntity {
    private String url = "";
    private String name = "";
    private String country = "";
    private int range;
    private int antenna;
    private boolean enabled = true;
    private float volume = 1.0f;

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(WorldRadio.RADIO_BLOCK_ENTITY, pos, state);
    }

    public String url() {
        return url;
    }

    public String name() {
        return name;
    }

    public String country() {
        return country;
    }

    public int range() {
        return range;
    }

    public int antenna() {
        return antenna;
    }

    public boolean enabled() {
        return enabled;
    }

    public float volume() {
        return volume;
    }

    public void setVolume(float volume) {
        float v = Math.clamp(volume, 0.0f, 1.0f);
        if (this.volume == v) return;
        this.volume = v;
        sync();
    }

    /** Plays only when switched on and tuned. */
    public boolean sends() {
        return enabled && !url.isEmpty();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        sync();
    }

    public void setStation(String url, String name, String country) {
        this.url = url == null ? "" : url;
        this.name = name == null ? "" : name;
        this.country = country == null ? "" : country;
        sync();
    }

    /** Server side; returns whether it changed. */
    public boolean setRange(int range, int antenna) {
        if (this.range == range && this.antenna == antenna) return false;
        this.range = range;
        this.antenna = antenna;
        sync();
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("url", url);
        out.putString("name", name);
        out.putString("country", country);
        out.putInt("range", range);
        out.putInt("antenna", antenna);
        out.putBoolean("enabled", enabled);
        out.putFloat("volume", volume);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        url = in.getStringOr("url", "");
        name = in.getStringOr("name", "");
        country = in.getStringOr("country", "");
        range = in.getIntOr("range", 0);
        antenna = in.getIntOr("antenna", 0);
        enabled = in.getBooleanOr("enabled", true);
        volume = Math.clamp(in.getFloatOr("volume", 1.0f), 0.0f, 1.0f);
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
