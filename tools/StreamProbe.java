import worldradio.client.audio.PcmBuffer;
import worldradio.client.audio.StationStream;

/**
 * Streams each address given (default: Kiss FM Berlin and Radio Dismuke) with the mod's own decoder outside
 * Minecraft, for up to 20 s until 5 s of audio are decoded, and prints state, title, sample count and loudness.
 * Run by tools/stream-probe.sh.
 */
public class StreamProbe {
    public static void main(String[] args) throws Exception {
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
            stream.close();
        }
        System.out.println("StreamProbe: " + ok + "/" + urls.length + " streams decoded");
    }
}
