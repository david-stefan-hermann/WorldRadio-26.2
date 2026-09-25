package worldradio.signal;

/**
 * One radio station arriving at an amplifier. {@code hops} counts the links from the radio to this amplifier (1 = the
 * radio reaches it directly), {@code distance} is the straight line from the radio to this amplifier, and
 * {@code factor} is the share of the amplifier's volume this signal gets (1.0 alone, 0.5 when it has company).
 */
public record Signal(long radioId, int radioX, int radioY, int radioZ, String url, String name, double distance,
                     int hops, double factor) {
    public Signal withFactor(double newFactor) {
        return new Signal(radioId, radioX, radioY, radioZ, url, name, distance, hops, newFactor);
    }
}
