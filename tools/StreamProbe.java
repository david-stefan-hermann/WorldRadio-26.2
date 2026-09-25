import worldradio.client.audio.PcmBuffer;
import worldradio.client.audio.StationStream;
import worldradio.client.audio.StereoMixer;
import worldradio.signal.Panner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Streams each address given (default: Kiss FM Berlin and Radio Dismuke) with the mod's own decoder outside
 * Minecraft, for up to 20 s until 5 s of audio are decoded, and prints state, title, sample count and loudness.
 * With {@code --pan <p>} (p = -1..1) it also writes 2 s through the mod's stereo mixer at the default 30 % directional
 * share and prints the RMS of each channel. Run by tools/stream-probe.sh.
 */
public class StreamProbe {
    public static void main(String[] args) throws Exception {
        Double pan = null;
        if (args.length >= 2 && args[0].equals("--pan")) {
            pan = Double.parseDouble(args[1]);
            args = Arrays.copyOfRange(args, 2, args.length);
        }
        String[] urls = args.length > 0 ? args : new String[]{
                "http://stream.kissfm.de/kissfm/mp3-128/internetradio/",
                "http://stream2.early1900s.org:8000/"};
        int ok = 0;
        for (String url : urls) {
            StationStream stream = new StationStream(url);
            PcmBuffer buffer = new PcmBuffer();
            stream.addConsumer(buffer);
            long start = System.currentTimeMillis();
            stream.start();
            long want = StationStream.RATE * 5L;
            while (System.currentTimeMillis() - start < 20_000 && stream.samplesOut() < want
                    && stream.state() != StationStream.State.UNSUPPORTED) {
                Thread.sleep(200);
            }
            short[] chunk = new short[StationStream.RATE / 4];
            int real = buffer.read(chunk);
            double sum = 0;
            for (short s : chunk) sum += (double) s * s;
            double rms = Math.sqrt(sum / chunk.length);
            long seconds = stream.samplesOut() / StationStream.RATE;
            boolean pass = stream.samplesOut() >= want && rms > 50;
            if (pass) ok++;
            System.out.printf("%s %s: state=%s decoded=%d samples (%d s at %d Hz mono) in %d ms, rms=%.0f, name='%s', title='%s', detail='%s'%n",
                    pass ? "PASS" : "FAIL", url, stream.state(), stream.samplesOut(), seconds, StationStream.RATE,
                    System.currentTimeMillis() - start, rms, stream.icyName(), stream.title(), stream.detail());
            if (pan != null) stereo(buffer, pan);
            stream.close();
        }
        System.out.println("StreamProbe: " + ok + "/" + urls.length + " streams decoded");
    }

    /** 2 s of the buffered audio through the stereo mixer, as the sound thread would pull it. */
    private static void stereo(PcmBuffer buffer, double pan) {
        float[] gains = Panner.gains(pan, 0.3);
        StereoMixer mixer = new StereoMixer();
        mixer.setGains(gains[0], gains[1]);
        short[] mono = new short[StationStream.RATE / 4];
        double sumL = 0, sumR = 0;
        long frames = 0;
        for (int chunk = 0; chunk < 8; chunk++) {
            buffer.read(mono);
            ByteBuffer out = ByteBuffer.allocate(mono.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            mixer.mix(mono, mono.length, out);
            out.flip();
            while (out.remaining() >= 4) {
                double l = out.getShort(), r = out.getShort();
                if (chunk > 0) { sumL += l * l; sumR += r * r; frames++; } // chunk 0 ramps from 1.0 to the gains
            }
        }
        double rmsL = Math.sqrt(sumL / frames), rmsR = Math.sqrt(sumR / frames);
        System.out.printf("  stereo pan=%.1f gainL=%.3f gainR=%.3f: rmsL=%.0f rmsR=%.0f (L/R %.2f)%n", pan, gains[0], gains[1],
                rmsL, rmsR, rmsL / rmsR);
    }
}
