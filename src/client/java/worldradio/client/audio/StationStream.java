package worldradio.client.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * One internet radio station, streamed and decoded on its own daemon thread. Resolves .m3u/.pls playlists, follows
 * redirects (also http → https), strips ICY metadata (keeping the current title) and pushes mono PCM into every
 * registered {@link PcmBuffer}. Reconnects with a growing pause after errors until {@link #close()}.
 */
public final class StationStream {
    public static final int RATE = 48_000;
    /** Set by the mod to its logger; plain stdout for the stand-alone probe. */
    public static Consumer<String> log = System.out::println;
    public static String userAgent = "WorldRadio (BaconCakeFactory)";

    public enum State { CONNECTING, PLAYING, RETRYING, UNSUPPORTED, CLOSED }

    private final String url;
    private final List<PcmBuffer> consumers = new CopyOnWriteArrayList<>();
    private final Thread thread;
    private volatile boolean closed;
    private volatile State state = State.CONNECTING;
    private volatile String title = "";
    private volatile String icyName = "";
    private volatile String detail = "";
    private volatile InputStream current;
    private volatile long samplesOut;

    public StationStream(String url) {
        this.url = url;
        this.thread = new Thread(this::run, "WorldRadio stream " + url);
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void close() {
        closed = true;
        state = State.CLOSED;
        InputStream in = current;
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        thread.interrupt();
    }

    public void addConsumer(PcmBuffer buffer) {
        consumers.add(buffer);
    }

    public void removeConsumer(PcmBuffer buffer) {
        consumers.remove(buffer);
    }

    public int consumerCount() {
        return consumers.size();
    }

    public String url() {
        return url;
    }

    public State state() {
        return state;
    }

    /** The ICY stream title ("Artist - Song"), empty when the station sends none. */
    public String title() {
        return title;
    }

    /** The station name the server announces (icy-name), empty when it sends none. */
    public String icyName() {
        return icyName;
    }

    /** Why the stream is not playing (codec, HTTP status, error), for the screen. */
    public String detail() {
        return detail;
    }

    public long samplesOut() {
        return samplesOut;
    }

    private void run() {
        int failures = 0;
        while (!closed) {
            try {
                state = State.CONNECTING;
                play();
                failures = 0;
                if (!closed) detail = "stream ended";
            } catch (UnsupportedStreamException e) {
                state = State.UNSUPPORTED;
                detail = e.getMessage();
                log.accept("Radio: stream " + url + " unsupported: " + e.getMessage());
                return;
            } catch (Exception e) {
                if (closed) break;
                failures++;
                detail = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                log.accept("Radio: stream " + url + " failed (" + detail + ")");
            }
            if (closed) break;
            state = State.RETRYING;
            try {
                Thread.sleep(Math.min(30_000L, 2_000L * (1L << Math.min(failures, 4))));
            } catch (InterruptedException e) {
                break;
            }
        }
        state = State.CLOSED;
        log.accept("Radio: stream " + url + " closed");
    }

    private void play() throws Exception {
        HttpURLConnection connection = open(url, 0);
        String type = String.valueOf(connection.getContentType()).toLowerCase(Locale.ROOT);
        InputStream raw = new BufferedInputStream(connection.getInputStream(), 16 * 1024);
        current = raw;
        try {
            int metaInt = parseInt(connection.getHeaderField("icy-metaint"));
            String name = connection.getHeaderField("icy-name");
            if (name != null && !name.isBlank()) icyName = name.trim();
            InputStream audio = metaInt > 0 ? new IcyInputStream(raw, metaInt, t -> title = t) : raw;
            Mp3Decoder decoder = new Mp3Decoder(audio);
            boolean first = true;
            try {
                while (!closed) {
                    int count = decoder.next();
                    if (count < 0) return;
                    if (first) {
                        first = false;
                        state = State.PLAYING;
                        detail = "";
                        log.accept("Radio: stream " + url + " connected (" + type + ", " + decoder.inputRate() + " Hz, "
                                + decoder.channels() + " ch)");
                    }
                    short[] samples = decoder.samples();
                    for (PcmBuffer consumer : consumers) consumer.write(samples, count);
                    samplesOut += count;
                }
            } finally {
                decoder.close();
            }
        } finally {
            current = null;
            raw.close();
            connection.disconnect();
        }
    }

    /** Opens the audio connection, following redirects and playlists; rejects formats the decoder cannot play. */
    private HttpURLConnection open(String address, int depth) throws Exception {
        if (depth > 6) throw new IOException("too many redirects or nested playlists");
        URL target = URI.create(address.trim()).toURL();
        String protocol = target.getProtocol();
        if (!protocol.equals("http") && !protocol.equals("https")) throw new UnsupportedStreamException("not an http(s) address");
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Icy-MetaData", "1");
        connection.setRequestProperty("Accept", "*/*");
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null) throw new IOException("redirect without a location");
            return open(target.toURI().resolve(location).toString(), depth + 1);
        }
        if (status >= 400) {
            connection.disconnect();
            throw new IOException("HTTP " + status);
        }
        String type = String.valueOf(connection.getContentType()).toLowerCase(Locale.ROOT);
        String path = target.getPath().toLowerCase(Locale.ROOT);
        if (type.contains("mpegurl") && (path.endsWith(".m3u8") || type.contains("vnd.apple"))) {
            connection.disconnect();
            throw new UnsupportedStreamException("HLS streams are not supported");
        }
        if (type.contains("mpegurl") || type.contains("scpls") || path.endsWith(".m3u") || path.endsWith(".pls")) {
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
            }
            connection.disconnect();
            String entry = firstUrl(body);
            if (entry == null) throw new IOException("playlist without a stream address");
            return open(entry, depth + 1);
        }
        if (type.contains("aac") || type.contains("mp4") || type.contains("ogg") || type.contains("opus")
                || type.contains("flac") || type.contains("text/html")) {
            connection.disconnect();
            throw new UnsupportedStreamException("codec not supported (" + type + ")");
        }
        return connection;
    }

    /** First http(s) address in an .m3u or .pls playlist. */
    static String firstUrl(String playlist) {
        for (String line : playlist.split("\\r?\\n")) {
            String s = line.trim();
            int eq = s.indexOf('=');
            if (s.toLowerCase(Locale.ROOT).startsWith("file") && eq > 0) s = s.substring(eq + 1).trim();
            if (s.startsWith("http://") || s.startsWith("https://")) return s;
        }
        return null;
    }

    private static int parseInt(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static final class UnsupportedStreamException extends Exception {
        UnsupportedStreamException(String message) {
            super(message);
        }
    }
}
