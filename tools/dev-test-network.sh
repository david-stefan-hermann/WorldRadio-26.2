#!/usr/bin/env bash
# Server-only RCON test of the signal network: places radios, amplifiers and antenna columns, tunes the radios, switches
# them on and off and checks `/worldradio status` after each change. Starts from an old config (a 0.2 `ranges` list and
# the 0.3 step of 8, no version) to check that it still loads and is migrated. Prints PASS/FAIL per check and a total.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 wr "$@"; }
PASS=0
FAIL=0
check() { # name, command output, extended regex that must match
  if echo "$2" | grep -Eq "$3"; then PASS=$((PASS + 1)); echo "PASS $1"; else FAIL=$((FAIL + 1)); echo "FAIL $1"; echo "     got: $2"; fi
}
status() { rcon "worldradio status $1" | grep -v '^>'; }
# change, wait (ms, inside one RCON connection so the start is exact), status of pos
after() { rcon "$1" "sleep:$2" "worldradio status $3" | grep -v '^>' | tail -1; }
KISS=http://stream.kissfm.de/kissfm/mp3-128/internetradio/
DISMUKE=http://stream2.early1900s.org:8000/
CLEAR="fill -10 -60 -4 130 -55 8 air"

mkdir -p run/config
printf '{\n  "ranges": [\n    32,\n    64\n  ],\n  "antennaStep": 8,\n  "maxStations": 6,\n  "directionalShare": 0.3\n}' > run/config/worldradio.json

start() {
  ( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
  sleep 20
  for _ in $(seq 1 100); do
    grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break
    sleep 3
  done
  if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi
}
stop() {
  rcon "stop" > /dev/null
  wait
}

start

check "old config loads, step 8 migrated to 32" "$(grep 'World Radio ready' run/runServer.out)" "range 32 \+ 32 per antenna block, up to 32 blocks"
check "config written back with version, without ranges" "$(tr -d '\n ' < run/config/worldradio.json)" '^\{"configVersion":4,"baseRange":32,"antennaStep":32,"maxAntenna":32,"maxStations":6,"directionalShare":0.3\}$'
check "recipes and advancements parse" "$(grep -iE "couldn't parse|failed to parse|couldn't load|error.*worldradio" run/runServer.out | head -3)" '^$'

# whatever the last run left (an older world still has power=... blocks there): information only
echo "INFO before clearing: $(status '0 -60 0') / $(status '20 -60 0')"

# leftovers of an earlier run far away (the network remembers unloaded blocks): load, clear, let it notice, unload
rcon "forceload add 288 -16 431 16" "fill 290 -60 -4 420 -55 8 air" > /dev/null
sleep 3
rcon "forceload remove 288 -16 431 16" > /dev/null

# --- chain without antennas, then a second radio with four bars
rcon "forceload add -16 -16 144 16" "$CLEAR" "fill 0 -59 0 0 -20 0 air" \
  "setblock 0 -60 0 worldradio:radio" "setblock 20 -60 0 worldradio:amplifier" > /dev/null
sleep 3
check "untuned radio: level 0, sends nothing" "$(status '0 -60 0')" "radio range=32 antenna=0 level=0 enabled=true volume=100 url= "
check "amp without signal: level 0" "$(status '20 -60 0')" "amplifier range=32 antenna=0 level=0 signals=0"
rcon "worldradio tune 0 -60 0 \"$KISS\" Kiss FM" > /dev/null
sleep 3
check "tuned radio: range 32, level 1" "$(status '0 -60 0')" "radio range=32 antenna=0 level=1 enabled=true volume=100 url=$KISS name=Kiss FM"
check "direct: one signal, hops 1, dist 20, 100 %, level 1" "$(status '20 -60 0')" "level=1 signals=1 \| $KISS from 0,-60,0 hops=1 dist=20.0 factor=1.00"

# --- on/off keeps the station
rcon "worldradio enable 0 -60 0 false" > /dev/null
sleep 2
check "switched off: level 0, station kept" "$(status '0 -60 0')" "radio range=32 antenna=0 level=0 enabled=false volume=100 url=$KISS name=Kiss FM"
check "switched off: amp at 20 loses the signal" "$(status '20 -60 0')" "level=0 signals=0"
rcon "worldradio enable 0 -60 0 true" > /dev/null
sleep 2
check "switched on: level 1 again" "$(status '0 -60 0')" "radio range=32 antenna=0 level=1 enabled=true volume=100 url=$KISS"
check "switched on: amp at 20 has it back" "$(status '20 -60 0')" "level=1 signals=1 \| $KISS from 0,-60,0 hops=1"

# --- the radio's own volume
rcon "worldradio volume 0 -60 0 40" > /dev/null
sleep 2
check "volume 40 %, station and signal unchanged" "$(status '0 -60 0') $(status '20 -60 0')" "enabled=true volume=40 url=$KISS .*signals=1"
rcon "worldradio volume 0 -60 0 100" > /dev/null

rcon "setblock 50 -60 0 worldradio:amplifier" > /dev/null
sleep 3
check "chain: second amp gets it over 2 hops, distance to the radio" "$(status '50 -60 0')" "signals=1 \| $KISS from 0,-60,0 hops=2 dist=50.0 factor=1.00"

rcon "setblock 80 -60 0 worldradio:radio" "fill 80 -59 0 80 -56 0 iron_bars" "worldradio tune 80 -60 0 \"$DISMUKE\" Radio Dismuke" > /dev/null
sleep 3
check "four bars: range 160, level 3" "$(status '80 -60 0')" "radio range=160 antenna=4 level=3"
S=$(status '20 -60 0')
check "two radios on amp A: both at 50 %" "$S" "signals=2 .*factor=0.50.*factor=0.50"
check "amp A: dismuke direct (60 <= 160)" "$S" "$DISMUKE from 80,-60,0 hops=1 dist=60.0"
check "amp B: two signals" "$(status '50 -60 0')" "signals=2"

rcon "setblock 20 -60 0 air" > /dev/null
sleep 3
check "amp A gone breaks the relay: amp B only dismuke at 100 %" "$(status '50 -60 0')" "signals=1 \| $DISMUKE from 80,-60,0 hops=1 dist=30.0 factor=1.00"
rcon "setblock 20 -60 0 worldradio:amplifier" "setblock 80 -60 0 air" > /dev/null
sleep 3
check "radio broken: amp B back to kiss over 2 hops" "$(status '50 -60 0')" "signals=1 \| $KISS from 0,-60,0 hops=2"
check "network counts" "$(status '0 -60 0')" "network: 1 radios, 2 amplifiers"
rcon "worldradio tune 0 -60 0 \"ftp://nope\"" > /dev/null
check "non-http url refused, station kept" "$(status '0 -60 0')" "url=$KISS"

# --- three iron bars: 128 blocks reach an amplifier at 110; the top bar broken -> 96, found by the 1 s rescan
rcon "$CLEAR" "setblock 0 -60 0 worldradio:radio" "worldradio tune 0 -60 0 \"$KISS\" Kiss FM" \
  "setblock 110 -60 0 worldradio:amplifier" "fill 0 -59 0 0 -57 0 iron_bars" > /dev/null
sleep 3
check "three bars: range 128, antenna 3, level 2" "$(status '0 -60 0')" "radio range=128 antenna=3 level=2"
check "amp at 110 receives it" "$(status '110 -60 0')" "signals=1 \| $KISS from 0,-60,0 hops=1 dist=110.0"
check "top bar broken: range 96 within 25 ticks" "$(after 'setblock 0 -57 0 air' 1250 '0 -60 0')" "radio range=96 antenna=2 level=2"
check "amp at 110 lost it" "$(status '110 -60 0')" "level=0 signals=0"

# --- the first antenna block counts at once (neighbour update), three times so a lucky rescan cannot fake it
rcon "fill 0 -59 0 0 -58 0 air" > /dev/null
sleep 2
check "first bar placed: counted within 3 ticks (1)" "$(after 'setblock 0 -59 0 iron_bars' 150 '0 -60 0')" "range=64 antenna=1"
check "first bar removed: within 3 ticks" "$(after 'setblock 0 -59 0 air' 150 '0 -60 0')" "range=32 antenna=0 level=1"
check "first bar placed: counted within 3 ticks (2)" "$(after 'setblock 0 -59 0 end_rod' 150 '0 -60 0')" "range=64 antenna=1"

# --- mixed column, a stone block in it, and 40 lightning rods (cap)
rcon "setblock 0 -59 0 lightning_rod" "setblock 0 -58 0 waxed_oxidized_copper_chain" "setblock 0 -57 0 end_rod" \
  "setblock 0 -56 0 exposed_copper_bars" > /dev/null
sleep 3
check "mixed column (rod, waxed oxidized chain, end rod, exposed copper bars): 160" "$(status '0 -60 0')" "radio range=160 antenna=4 level=3"
rcon "setblock 0 -58 0 stone" > /dev/null
sleep 3
check "stone in the column stops the count" "$(status '0 -60 0')" "radio range=64 antenna=1 level=2"
rcon "fill 0 -59 0 0 -20 0 lightning_rod" > /dev/null
sleep 3
check "40 lightning rods: 1056 (cap), antenna=40" "$(status '0 -60 0')" "radio range=1056 antenna=40 level=3"
check "1056 reaches the amp at 110" "$(status '110 -60 0')" "level=1 signals=1"

# --- amplifiers take antennas too: an amp at 30 with two chains relays 80 blocks further
rcon "fill 0 -59 0 0 -20 0 air" "$CLEAR" "setblock 0 -60 0 worldradio:radio" "worldradio tune 0 -60 0 \"$KISS\" Kiss FM" \
  "setblock 30 -60 0 worldradio:amplifier" "setblock 110 -60 0 worldradio:amplifier" > /dev/null
sleep 3
check "amp at 110: out of reach of radio (32) and amp 30 (32)" "$(status '110 -60 0')" "signals=0"
rcon "fill 30 -59 0 30 -58 0 iron_chain" > /dev/null
sleep 3
check "amp 30 with two chains: range 96, level 2" "$(status '30 -60 0')" "amplifier range=96 antenna=2 level=2 signals=1"
check "amp at 110 gets it over 2 hops" "$(status '110 -60 0')" "signals=1 \| $KISS from 0,-60,0 hops=2 dist=110.0"
rcon "worldradio tune 0 -60 0 \"\"" > /dev/null
sleep 3
check "station cleared: radio level 0, amp level 0" "$(status '0 -60 0') $(status '30 -60 0')" "radio range=32 antenna=0 level=0 enabled=true volume=100 url= .*amplifier range=96 antenna=2 level=0 signals=0"

# --- an unloaded radio keeps sending: radio at 300 (3 bars, 128) far from the spawn chunks, amplifier at 410;
# then only the amplifier's chunk stays loaded, across a server restart too
rcon "forceload add 288 -16 431 16" "fill 290 -60 -4 420 -55 8 air" "setblock 300 -60 0 worldradio:radio" \
  "worldradio tune 300 -60 0 \"$KISS\" Kiss FM" "fill 300 -59 0 300 -57 0 iron_bars" "setblock 410 -60 0 worldradio:amplifier" > /dev/null
sleep 3
check "far pair: amp at 410 receives the radio at 300" "$(status '410 -60 0')" "signals=1 \| $KISS from 300,-60,0 hops=1 dist=110.0"
rcon "forceload remove 288 -16 431 16" "forceload add 400 -16 415 16" > /dev/null
sleep 10
check "radio chunk unloaded" "$(rcon 'worldradio status 300 -60 0')" "not loaded"
check "unloaded radio still reaches the amp" "$(status '410 -60 0')" "signals=1 \| $KISS from 300,-60,0 hops=1"
stop
start
sleep 3
check "after a restart the unloaded radio is still known" "$(rcon 'worldradio status 300 -60 0') $(status '410 -60 0')" "not loaded.*signals=1 \| $KISS from 300,-60,0"
rcon "forceload add 288 -16 303 16" "setblock 300 -60 0 air" "forceload remove 288 -16 303 16" > /dev/null
sleep 3
check "radio broken: the amp at 410 loses it" "$(status '410 -60 0')" "level=0 signals=0"
rcon "setblock 410 -60 0 air" "forceload remove 400 -16 415 16" > /dev/null

echo "dev-test-network: $PASS passed, $FAIL failed"
stop
