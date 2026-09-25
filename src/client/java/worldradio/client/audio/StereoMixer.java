package worldradio.client.audio;

import java.nio.ByteBuffer;

/**
 * Turns the mono station signal into 16-bit stereo with a left and a right gain (see {@link worldradio.signal.Panner}).
 * The gains are set from the client thread and read by the sound thread; each chunk ramps linearly from the previous
 * pair to the new one, so turning the head does not click.
 */
public final class StereoMixer {
    private volatile float targetLeft = 1.0f;
    private volatile float targetRight = 1.0f;
    private float left = 1.0f;
    private float right = 1.0f;

    public void setGains(float newLeft, float newRight) {
        targetLeft = newLeft;
        targetRight = newRight;
    }

    public float targetLeft() {
        return targetLeft;
    }

    public float targetRight() {
        return targetRight;
    }

    /** Writes {@code frames} stereo frames (little endian, left first) for the mono samples into {@code out}. */
    public void mix(short[] mono, int frames, ByteBuffer out) {
        float toLeft = targetLeft;
        float toRight = targetRight;
        float stepLeft = (toLeft - left) / frames;
        float stepRight = (toRight - right) / frames;
        for (int i = 0; i < frames; i++) {
            float gl = left + stepLeft * (i + 1);
            float gr = right + stepRight * (i + 1);
            out.putShort(clip(mono[i] * gl));
            out.putShort(clip(mono[i] * gr));
        }
        left = toLeft;
        right = toRight;
    }

    private static short clip(float v) {
        return (short) Math.clamp(Math.round(v), Short.MIN_VALUE, Short.MAX_VALUE);
    }
}
