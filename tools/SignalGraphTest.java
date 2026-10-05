import worldradio.signal.Antenna;
import worldradio.signal.Reception;
import worldradio.signal.Reception.Emitter;
import worldradio.signal.Reception.Heard;
import worldradio.signal.Reception.Station;
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
        check("direct: hops 1, distance 20", r.get(10L).get(0).hops() == 1 && r.get(10L).get(0).distance() == 20);

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
        check("two radios: two signals", r.get(40L).size() == 2);

        // out of range
        r = SignalGraph.compute(List.of(kiss), List.of(amp(50, 33, 0, 64)));
        check("out of range: 33 > 32", r.get(50L).isEmpty());
        r = SignalGraph.compute(List.of(kiss), List.of(amp(50, 32, 0, 64)));
        check("in range: exactly 32", r.get(50L).size() == 1);

        // range 0 is a receiver (the radio block): it gets the signal and passes nothing on
        r = SignalGraph.compute(List.of(kiss), List.of(amp(60, 30, 0, 0), amp(61, 50, 0, 64)));
        check("receiver: receives", r.get(60L).size() == 1 && r.get(60L).get(0).hops() == 1);
        check("receiver: does not relay", r.get(61L).isEmpty());
        r = SignalGraph.compute(List.of(kiss), List.of(amp(60, 30, 0, 64), amp(61, 90, 0, 0), amp(64, 120, 0, 0)));
        check("receiver: hears an amplifier (hops 2)", r.get(61L).size() == 1 && r.get(61L).get(0).hops() == 2);
        check("receiver: out of the amplifier's range hears nothing", r.get(64L).isEmpty());
        r = SignalGraph.compute(List.of(new Radio(1, 0, 0, 0, 64, "http://kiss", "Kiss"), new Radio(2, 100, 0, 0, 64,
                "http://dismuke", "Dismuke")), List.of(amp(65, 50, 0, 0)));
        check("receiver: two transmitters, two signals to pick from", r.get(65L).size() == 2);
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

        // a radio's hearing range: the same curve with a short fade (a quarter of the range, at least 2 blocks)
        check("hearing 4: 1.0 at 0, 0.6 at 2, 0.2 at 4", near(VolumeCurve.hearing(0, 4), 1.0) && near(VolumeCurve.hearing(2, 4), 0.6)
                && near(VolumeCurve.hearing(4, 4), 0.2));
        check("hearing 4: 0.1 at 5, silent from 6", near(VolumeCurve.hearing(5, 4), 0.1) && near(VolumeCurve.hearing(6, 4), 0.0));
        check("hearing 16: silent from 20, hearing 64: from 80", VolumeCurve.hearing(19.9, 16) > 0 && near(VolumeCurve.hearing(20, 16), 0.0)
                && VolumeCurve.hearing(79.9, 64) > 0 && near(VolumeCurve.hearing(80, 64), 0.0));

        // reception of a signal: full inside the range, fading over the tail
        check("reception: full at 0 and at R", near(VolumeCurve.reception(0, 32), 1.0) && near(VolumeCurve.reception(32, 32), 1.0));
        check("reception: half at R+5, none at R+10", near(VolumeCurve.reception(37, 32), 0.5) && near(VolumeCurve.reception(42, 32), 0.0));
        check("reception: range 0 sends nothing", near(VolumeCurve.reception(0, 0), 0.0));

        // what a portable radio picks up between two transmitters (block centres at x+0.5)
        Station kissStation = new Station("http://kiss", "Kiss FM");
        Station dismukeStation = new Station("http://dismuke", "Dismuke");
        List<Emitter> emitters = List.of(new Emitter(0, 0, 0, 64, List.of(kissStation)),
                new Emitter(100, 0, 0, 32, List.of(dismukeStation)),
                new Emitter(60, 0, 0, 32, List.of(kissStation, dismukeStation)));
        List<Heard> heard = Reception.at(10.5, 0.5, 0.5, emitters);
        check("portable: near the first transmitter only its station", heard.size() == 1
                && heard.get(0).url().equals("http://kiss") && near(heard.get(0).strength(), 1.0));
        heard = Reception.at(50.5, 0.5, 0.5, emitters);
        check("portable: in the overlap both stations, each once", heard.size() == 2);
        check("portable: amplifier carries both at full strength", near(heard.get(0).strength(), 1.0) && near(heard.get(1).strength(), 1.0));
        check("portable: tail of the far transmitter fades (R+5 of 32 = 0.5)",
                near(Reception.strength(137.5, 0.5, 0.5, "http://dismuke", emitters), 0.5));
        check("portable: the best emitter counts, not the first", near(Reception.strength(70.5, 0.5, 0.5, "http://kiss", emitters), 1.0));
        check("portable: out of every range nothing", Reception.at(300, 0, 0, emitters).isEmpty()
                && Reception.strength(300, 0, 0, "http://kiss", emitters) == 0.0);

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
