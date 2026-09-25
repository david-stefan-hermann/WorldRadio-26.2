package worldradio.client.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.InputStream;

/**
 * Decodes an MP3 byte stream frame by frame (JLayer) into mono 16-bit samples at {@link StationStream#RATE}: stereo is
 * mixed down (a radio is one speaker, and OpenAL only places mono sounds in the world), other sample rates are
 * resampled linearly.
 */
final class Mp3Decoder {
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private short[] mono = new short[2304];
    private short[] out = new short[4096];
    private int inputRate;
    private int channels;
    /** Resampler state: position of the next output sample in input samples, relative to the current frame. */
    private double position;
    private short last;
    private int badFrames;

    Mp3Decoder(InputStream in) {
        this.bitstream = new Bitstream(in);
    }

    /** Decodes the next frame into {@link #samples()}; returns the sample count, or -1 at the end of the stream. */
    int next() throws BitstreamException {
        while (true) {
            Header header = bitstream.readFrame();
            if (header == null) return -1;
            try {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                badFrames = 0;
                channels = decoder.getOutputChannels();
                inputRate = decoder.getOutputFrequency();
                return resample(buffer.getBuffer(), buffer.getBufferLength());
            } catch (DecoderException | ArrayIndexOutOfBoundsException e) {
                if (++badFrames > 50) throw new BitstreamException(BitstreamException.UNKNOWN_ERROR, e);
            } finally {
                bitstream.closeFrame();
            }
        }
    }

    short[] samples() {
        return out;
    }

    int inputRate() {
        return inputRate;
    }

    int channels() {
        return channels;
    }

    private int resample(short[] interleaved, int length) {
        int frames = channels == 2 ? length / 2 : length;
        if (mono.length < frames) mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            mono[i] = channels == 2 ? (short) ((interleaved[2 * i] + interleaved[2 * i + 1]) / 2) : interleaved[i];
        }
        double step = (double) inputRate / StationStream.RATE;
        int needed = (int) Math.ceil(frames / step) + 2;
        if (out.length < needed) out = new short[needed];
        int count = 0;
        while (position < frames - 1) {
            int i = (int) Math.floor(position);
            double frac = position - i;
            int a = i < 0 ? last : mono[i];
            int b = mono[i + 1];
            out[count++] = (short) Math.round(a + (b - a) * frac);
            position += step;
        }
        position -= frames;
        if (frames > 0) last = mono[frames - 1];
        return count;
    }

    void close() {
        try {
            bitstream.close();
        } catch (BitstreamException ignored) {
        }
    }
}
