package worldradio.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import worldradio.WorldRadio;
import worldradio.client.mixin.SoundEngineAccessor;
import worldradio.client.mixin.SoundManagerAccessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One station playing at one place. {@link SourceTracker} moves it to whichever radio is loudest and sets its volume
 * every tick. The distance falloff is ours ({@link worldradio.signal.VolumeCurve}, no attenuation by the engine); the
 * direction is the sound engine's: the stream is mono and sits at the radio, so turning the head is heard at once. A
 * carried portable radio plays at the listener instead (relative, position 0); the engine reads "relative" only when
 * a sound starts, so {@link #move} tells the running channel itself. Its sound path names this instance, and
 * {@link worldradio.client.mixin.SoundBufferLibraryMixin} hands out the live stream for it.
 */
public final class RadioSoundInstance extends AbstractTickableSoundInstance {
    private static final String PATH_PREFIX = "sounds/radio/";
    private static final SoundEvent EVENT = SoundEvent.createVariableRangeEvent(WorldRadio.id("radio"));
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final Map<Integer, RadioSoundInstance> LIVE = new ConcurrentHashMap<>();
    private static final SoundSource CATEGORY = category();

    private final int id = NEXT_ID.incrementAndGet();
    private final String url;
    private volatile RadioAudioStream stream;
    /** Where the station plays from. */
    private Vec3 place = Vec3.ZERO;

    public RadioSoundInstance(String url, Vec3 pos, float volume, boolean atListener) {
        super(EVENT, CATEGORY, SoundInstance.createUnseededRandom());
        this.url = url;
        this.attenuation = Attenuation.NONE;
        this.looping = false;
        this.volume = volume;
        move(pos, atListener);
        LIVE.put(id, this);
    }

    /** The Radio category from {@link worldradio.client.mixin.SoundSourceMixin}, or Jukebox/Note Blocks without it. */
    public static SoundSource category() {
        try {
            return SoundSource.valueOf("RADIO");
        } catch (IllegalArgumentException e) {
            return SoundSource.RECORDS;
        }
    }

    public String url() {
        return url;
    }

    /** Debug line: the stream's read counters, or "no stream" before the sound engine opened it. */
    public String streamStats() {
        RadioAudioStream s = stream;
        return s == null ? "no stream" : s.stats();
    }

    /** Plays from {@code pos} in the world, or with {@code atListener} in both ears alike wherever the player looks. */
    public void move(Vec3 pos, boolean atListener) {
        place = pos;
        Vec3 at = atListener ? Vec3.ZERO : pos;
        x = at.x;
        y = at.y;
        z = at.z;
        if (relative == atListener) return;
        relative = atListener;
        SoundEngine engine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).worldradio$engine();
        ChannelAccess.ChannelHandle handle = ((SoundEngineAccessor) engine).worldradio$channels().get(this);
        if (handle == null) return; // not started yet: the engine reads the fields
        handle.execute(channel -> {
            channel.setSelfPosition(at); // with the switch, or it would sit at the wrong spot until the next tick
            channel.setRelative(atListener);
        });
    }

    public Vec3 place() {
        return place;
    }

    public void setVolume(float volume) {
        this.volume = Math.clamp(volume, 0.0f, 1.0f);
    }

    public void finish() {
        stop();
        LIVE.remove(id);
        RadioAudioStream s = stream;
        if (s != null) s.close();
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** The sound's own factor is 1 (see resolve), and without a sound system resolve is never called. */
    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        sound = new Sound(Identifier.fromNamespaceAndPath(WorldRadio.MOD_ID, "radio/" + id), ConstantFloat.of(1.0f),
                ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(getIdentifier(), null);
    }

    /** Called by the mixin for every streamed sound path; null when it is not one of ours. */
    public static AudioStream streamFor(Identifier path) {
        if (!path.getNamespace().equals(WorldRadio.MOD_ID) || !path.getPath().startsWith(PATH_PREFIX)) return null;
        String rest = path.getPath().substring(PATH_PREFIX.length());
        int dot = rest.indexOf('.');
        try {
            RadioSoundInstance instance = LIVE.get(Integer.parseInt(dot < 0 ? rest : rest.substring(0, dot)));
            if (instance == null) return null;
            RadioAudioStream s = new RadioAudioStream(instance.url);
            RadioAudioStream old = instance.stream;
            instance.stream = s;
            if (old != null) old.close();
            return s;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
