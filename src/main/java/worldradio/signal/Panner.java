package worldradio.signal;

/**
 * Splits a station into a part that sits at the player (both ears equally) and a directional part panned towards
 * the place it plays from. OpenAL can only place a mono source fully or not at all, so the mod pans its own stereo
 * signal: the directional share uses equal-power panning, the rest stays at -3 dB in both ears, and the whole is
 * normalised so a source straight ahead plays at 1.0 in each channel. Front and back sound the same.
 */
public final class Panner {
    private static final double CENTRE = Math.sqrt(0.5);

    private Panner() {
    }

    /**
     * Where the source is, from -1 (fully left) over 0 (ahead or behind) to +1 (fully right): the sine of the horizontal
     * angle between the look direction and the way to the source. {@code yawDegrees} is Minecraft's yaw (0 = looking
     * south, +z; 90 = west, -x), {@code dx}/{@code dz} the horizontal offset from the listener to the source.
     */
    public static double pan(double yawDegrees, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-6) return 0;
        double yaw = Math.toRadians(yawDegrees);
        // the listener's right hand: facing south (+z) it points west (-x)
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        return Math.clamp((dx * rightX + dz * rightZ) / length, -1.0, 1.0);
    }

    /** Left and right gain for a pan of -1..1 with {@code share} (0..1) of the signal directional. */
    public static float[] gains(double pan, double share) {
        double s = Math.clamp(share, 0.0, 1.0);
        double theta = (Math.clamp(pan, -1.0, 1.0) + 1) * Math.PI / 4;
        double left = (1 - s) * CENTRE + s * Math.cos(theta);
        double right = (1 - s) * CENTRE + s * Math.sin(theta);
        return new float[]{(float) (left / CENTRE), (float) (right / CENTRE)};
    }
}
