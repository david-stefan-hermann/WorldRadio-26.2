package worldradio.client.audio;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The vanilla streaming interface over one {@link PcmBuffer}, as stereo panned by a {@link StereoMixer} (OpenAL never
 * places a stereo buffer in the world, so the direction is all ours). Never blocks the sound thread: it always hands
 * out a quarter second (real audio first, silence for whatever is missing), so OpenAL never runs dry and stops the
 * sound. Small buffers keep the delay short: vanilla queues four at the start.
 */
public final class RadioAudioStream implements AudioStream {
    public static final AudioFormat FORMAT = new AudioFormat(StationStream.RATE, 16, 2, true, false);
    private static final int CHUNK = StationStream.RATE / 4;

    private final String url;
    private final PcmBuffer buffer;
    private final StereoMixer mixer = new StereoMixer();
    private final short[] samples = new short[CHUNK];
    private boolean closed;
    private volatile long reads;
    private volatile long realSamples;

    public RadioAudioStream(String url, float gainLeft, float gainRight) {
        this.url = url;
        this.buffer = StreamPool.acquire(url);
        mixer.setGains(gainLeft, gainRight);
    }

    /** Called from the client thread; the next chunk ramps to these gains. */
    public void setGains(float left, float right) {
        mixer.setGains(left, right);
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int size) {
        realSamples += buffer.read(samples);
        reads++;
        ByteBuffer out = ByteBuffer.allocateDirect(CHUNK * 4).order(ByteOrder.LITTLE_ENDIAN);
        mixer.mix(samples, CHUNK, out);
        out.flip();
        return out;
    }

    /** How often the sound engine pulled a chunk, and how many of the samples handed out were real audio. */
    public String stats() {
        return "reads=" + reads + " real=" + realSamples;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        StreamPool.release(url, buffer);
    }
}
