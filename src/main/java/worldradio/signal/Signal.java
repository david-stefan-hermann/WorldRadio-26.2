package worldradio.signal;

/**
 * One station arriving at an amplifier or a radio. {@code hops} counts the links from the transmitter to this block
 * (1 = the transmitter reaches it directly), {@code distance} is the straight line from the transmitter to this block.
 */
public record Signal(long radioId, int radioX, int radioY, int radioZ, String url, String name, double distance,
                     int hops) {
}
