package worldradio.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import worldradio.WorldRadio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One station playing at one place. {@link SourceTracker} moves it to whichever radio or amplifier is loudest and sets
 * its volume and pan every tick. Both are ours: the distance falloff ({@link worldradio.signal.VolumeCurve}) and the
 * partial panning ({@link worldradio.signal.Panner}) go into a stereo stream that sits at the listener (relative,
 * position 0, no attenuation), so OpenAL does not place it at all. Its sound path names this instance, and
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
    /** Where the station plays from; the sound itself sits at the listener. */
    private Vec3 place = Vec3.ZERO;
    private volatile float gainLeft = 1.0f;
    private volatile float gainRight = 1.0f;
    private double pan;

    public RadioSoundInstance(String url, Vec3 pos, float volume) {
        super(EVENT, CATEGORY, SoundInstance.createUnseededRandom());
        this.url = url;
        this.attenuation = Attenuation.NONE;
        this.looping = false;
        this.relative = true;
        this.volume = volume;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        move(pos);
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

    public void move(Vec3 pos) {
        place = pos;
    }

    public Vec3 place() {
        return place;
    }

    /** Sets the pan (-1 left .. +1 right) and the gains it gives with this share of directional sound. */
    public void setPan(double newPan, float[] gains) {
        pan = newPan;
        gainLeft = gains[0];
        gainRight = gains[1];
        RadioAudioStream s = stream;
        if (s != null) s.setGains(gains[0], gains[1]);
    }

    public String panStats() {
        return String.format(java.util.Locale.ROOT, "pan=%.2f gainL=%.3f gainR=%.3f", pan, gainLeft, gainRight);
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
            RadioAudioStream s = new RadioAudioStream(instance.url, instance.gainLeft, instance.gainRight);
            RadioAudioStream old = instance.stream;
            instance.stream = s;
            if (old != null) old.close();
            return s;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
