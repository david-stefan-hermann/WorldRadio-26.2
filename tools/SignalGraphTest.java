import worldradio.signal.Antenna;
import worldradio.signal.Panner;
import worldradio.signal.Signal;
import worldradio.signal.SignalGraph;
import worldradio.signal.SignalGraph.Amplifier;
import worldradio.signal.SignalGraph.Radio;
import worldradio.signal.SourcePicker;
import worldradio.signal.SourcePicker.Candidate;
import worldradio.signal.VolumeCurve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain-Java checks for the signal package; run by tools/test-signal.sh. */
public class SignalGraphTest {
    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        Radio kiss = new Radio(1, 0, 0, 0, 32, "http://kiss", "Kiss FM");
        Radio dismuke = new Radio(2, 100, 0, 0, 32, "http://dismuke", "Dismuke");

        // direct: amplifier 20 blocks away, radio range 32
        Map<Long, List<Signal>> r = SignalGraph.compute(List.of(kiss), List.of(amp(10, 20, 0, 32)));
        check("direct: one signal", r.get(10L).size() == 1);
        check("direct: hops 1, distance 20, factor 1", r.get(10L).get(0).hops() == 1
                && r.get(10L).get(0).distance() == 20 && r.get(10L).get(0).factor() == 1.0);

        // two hops: A at 30 (reached), B at 90 (only via A, amp range 64)
        r = SignalGraph.compute(List.of(kiss), List.of(amp(10, 30, 0, 64), amp(11, 90, 0, 64)));
        check("two hops: B gets it with hops 2", r.get(11L).size() == 1 && r.get(11L).get(0).hops() == 2);
        check("two hops: distance is to the radio (90)", r.get(11L).get(0).distance() == 90);

        // chain of five, 30 blocks apart, amp range 32
        List<Amplifier> chain = new ArrayList<>();
        for (int i = 0; i < 5; i++) chain.add(amp(20 + i, 30 * (i + 1), 0, 32));
        r = SignalGraph.compute(List.of(kiss), chain);
        check("chain of five: last has hops 5", r.get(24L).size() == 1 && r.get(24L).get(0).hops() == 5);

        // cycle of three amplifiers close together
        r = SignalGraph.compute(List.of(kiss), List.of(amp(30, 10, 0, 32), amp(31, 20, 0, 32), amp(32, 15, 5, 32)));
        check("cycle: terminates, each amp one signal", r.get(30L).size() == 1 && r.get(31L).size() == 1
                && r.get(32L).size() == 1);
        check("cycle: all direct (hops 1)", r.get(30L).get(0).hops() == 1 && r.get(31L).get(0).hops() == 1);

        // two radios on one amplifier
        r = SignalGraph.compute(List.of(kiss, dismuke), List.of(amp(40, 50, 0, 32)));
        check("two radios: out of range of both (50 > 32)", r.get(40L).isEmpty());
        r = SignalGraph.compute(List.of(new Radio(1, 0, 0, 0, 64, "http://kiss", "Kiss"), new Radio(2, 100, 0, 0, 64,
                "http://dismuke", "Dismuke")), List.of(amp(40, 50, 0, 32)));
        check("two radios: two signals at 0.5", r.get(40L).size() == 2 && r.get(40L).get(0).factor() == 0.5
                && r.get(40L).get(1).factor() == 0.5);

        // out of range
        r = SignalGraph.compute(List.of(kiss), List.of(amp(50, 33, 0, 64)));
        check("out of range: 33 > 32", r.get(50L).isEmpty());
        r = SignalGraph.compute(List.of(kiss), List.of(amp(50, 32, 0, 64)));
        check("in range: exactly 32", r.get(50L).size() == 1);

        // range 0 (never built by the network since round 3, but the graph still skips it)
        r = SignalGraph.compute(List.of(kiss), List.of(amp(60, 30, 0, 0), amp(61, 50, 0, 64)));
        check("range 0: receives nothing", r.get(60L).isEmpty());
        check("range 0: does not relay", r.get(61L).isEmpty());
        r = SignalGraph.compute(List.of(new Radio(1, 0, 0, 0, 0, "http://kiss", "Kiss")), List.of(amp(62, 5, 0, 32)));
        check("radio range 0: sends nothing", r.get(62L).isEmpty());
        r = SignalGraph.compute(List.of(new Radio(1, 0, 0, 0, 32, "", "")), List.of(amp(63, 5, 0, 32)));
        check("radio without station: sends nothing", r.get(63L).isEmpty());

        // antenna columns: 32 + 32 per block, capped at 32 blocks
        check("antenna 0 = 32", Antenna.range(0) == 32);
        check("antenna 1 = 64", Antenna.range(1) == 64);
        check("antenna 3 = 128", Antenna.range(3) == 128);
        check("antenna 4 = 160", Antenna.range(4) == 160);
        check("antenna 40 = 1056 (cap)", Antenna.range(40) == 1056);
        check("antenna: own config (base 16, step 4, cap 2)", Antenna.range(5, 16, 4, 2) == 24);
        check("level: station, no antenna = 1", Antenna.level(true, 0) == 1);
        check("level: station, 1 antenna = 2", Antenna.level(true, 1) == 2);
        check("level: station, 3 antenna = 2", Antenna.level(true, 3) == 2);
        check("level: station, 4 antenna = 3", Antenna.level(true, 4) == 3);
        check("level: no station = 0 (even with antenna)", Antenna.level(false, 5) == 0);
        boolean[] column = {false, true, true, true, false, true}; // height 1..3 antenna, 4 stone, 5 antenna
        check("count: stops at the first other block", Antenna.count(dy -> dy < column.length && column[dy], 100) == 3);
        check("count: nothing on top = 0", Antenna.count(dy -> false, 100) == 0);
        check("count: stops at the limit (build height)", Antenna.count(dy -> true, 7) == 7);

        // curve
        check("curve 0 = 1.0", near(VolumeCurve.volume(0, 32), 1.0));
        check("curve R/2 = 0.6", near(VolumeCurve.volume(16, 32), 0.6));
        check("curve R = 0.2", near(VolumeCurve.volume(32, 32), 0.2));
        check("curve R+5 = 0.1", near(VolumeCurve.volume(37, 32), 0.1));
        check("curve range 64: R/2 = 0.6, R = 0.2", near(VolumeCurve.volume(32, 64), 0.6) && near(VolumeCurve.volume(64, 64), 0.2));
        check("curve R+10 = 0", near(VolumeCurve.volume(42, 32), 0.0));
        check("curve R+11 = 0", near(VolumeCurve.volume(43, 32), 0.0));
        // a large range fades over R/4 (72 blocks at 288) with the same slope as inside the range: no cliff at R
        check("curve 288: 0.2 at R, 0.1 at R+36, 0 at R+72", near(VolumeCurve.volume(288, 288), 0.2)
                && near(VolumeCurve.volume(324, 288), 0.1) && near(VolumeCurve.volume(360, 288), 0.0));
        double before = VolumeCurve.volume(287, 288) - VolumeCurve.volume(288, 288);
        double after = VolumeCurve.volume(288, 288) - VolumeCurve.volume(289, 288);
        check("curve 288: same slope either side of R", Math.abs(before - after) < 1e-9);
        check("curve 1056: audible to 1320", VolumeCurve.volume(1319, 1056) > 0 && VolumeCurve.volume(1320, 1056) == 0
                && near(VolumeCurve.audibleRange(1056), 1320));

        // loudest source per station, with hysteresis
        SourcePicker picker = new SourcePicker();
        Map<String, Candidate> p = picker.pick(List.of(c("kiss", "radio", 0.7), c("kiss", "amp", 0.5)), 6);
        check("pick: loudest source", p.size() == 1 && p.get("kiss").sourceId().equals("radio"));
        p = picker.pick(List.of(c("kiss", "radio", 0.7), c("kiss", "amp", 0.73)), 6);
        check("pick: +0.03 does not take over", p.get("kiss").sourceId().equals("radio"));
        p = picker.pick(List.of(c("kiss", "radio", 0.7), c("kiss", "amp", 0.76)), 6);
        check("pick: +0.06 takes over", p.get("kiss").sourceId().equals("amp"));
        p = picker.pick(List.of(c("kiss", "radio", 0.0), c("kiss", "amp", 0.0)), 6);
        check("pick: silent sources play nothing", p.isEmpty());
        List<Candidate> many = new ArrayList<>();
        for (int i = 0; i < 9; i++) many.add(c("s" + i, "r" + i, 0.1 * (i + 1)));
        p = picker.pick(many, 6);
        check("pick: at most 6 stations, loudest kept", p.size() == 6 && p.containsKey("s8") && !p.containsKey("s2"));

        // panning: 30 % directional, 70 % at the player
        float[] centre = Panner.gains(0, 0.3);
        float[] leftPan = Panner.gains(-1, 0.3);
        float[] rightPan = Panner.gains(1, 0.3);
        check("pan 0: equal, 1.0 per channel", near(centre[0], centre[1]) && Math.abs(centre[0] - 1.0) < 1e-6);
        check("pan -1: left > right, right >= 0.7 x centre", leftPan[0] > leftPan[1] && leftPan[1] >= 0.7 * centre[1] - 1e-6);
        check("pan +1: mirrored", near(rightPan[0], leftPan[1]) && near(rightPan[1], leftPan[0]));
        check("share 0: no direction at all", near(Panner.gains(-1, 0)[0], 1.0) && near(Panner.gains(-1, 0)[1], 1.0));
        // facing south (yaw 0): east (+x) is on the left, west on the right, north (-z) straight behind
        check("pan: east of a player facing south is left", near(Panner.pan(0, 10, 0), -1));
        check("pan: west of a player facing south is right", near(Panner.pan(0, -10, 0), 1));
        check("pan: facing north (yaw 180), east is right", near(Panner.pan(180, 10, 0), 1));
        float[] behind = Panner.gains(Panner.pan(0, 0, -10), 0.3);
        check("pan: straight behind sounds centred", near(Panner.pan(0, 0, -10), 0) && near(behind[0], behind[1]));

        System.out.println("SignalGraphTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static Amplifier amp(long id, int x, int z, int range) {
        return new Amplifier(id, x, 0, z, range);
    }

    private static Candidate c(String station, String source, double volume) {
        return new Candidate(station, source, volume);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++;
        else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }
}
