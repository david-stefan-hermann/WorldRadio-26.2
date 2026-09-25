package worldradio.client.audio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Strips SHOUTcast/Icecast metadata blocks from an audio stream. Every {@code metaInt} audio bytes the server inserts
 * one length byte L and L*16 bytes of text like {@code StreamTitle='Artist - Song';}; the title goes to the listener.
 */
final class IcyInputStream extends FilterInputStream {
    private final int metaInt;
    private final Consumer<String> titleListener;
    private int untilMeta;

    IcyInputStream(InputStream in, int metaInt, Consumer<String> titleListener) {
        super(in);
        this.metaInt = metaInt;
        this.untilMeta = metaInt;
        this.titleListener = titleListener;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (untilMeta == 0) {
            readMeta();
            untilMeta = metaInt;
        }
        int n = in.read(b, off, Math.min(len, untilMeta));
        if (n > 0) untilMeta -= n;
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        byte[] scratch = new byte[(int) Math.min(n, 4096)];
        int read = read(scratch, 0, scratch.length);
        return Math.max(read, 0);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private void readMeta() throws IOException {
        int length = in.read();
        if (length < 0) throw new IOException("stream ended in a metadata block");
        if (length == 0) return;
        byte[] meta = in.readNBytes(length * 16);
        String text = new String(meta, StandardCharsets.UTF_8).trim();
        int startTitle = text.indexOf("StreamTitle='");
        if (startTitle < 0) return;
        int from = startTitle + "StreamTitle='".length();
        int end = text.indexOf("';", from);
        if (end < 0) end = text.lastIndexOf('\'');
        if (end < from) return;
        String title = text.substring(from, end).trim();
        titleListener.accept(title);
    }
}
