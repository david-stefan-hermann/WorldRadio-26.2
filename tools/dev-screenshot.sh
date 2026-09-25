#!/usr/bin/env bash
# Starts the dev server, runs the RCON setup commands, joins with the dev client (which saves
# run/screenshots/<shot>.png and <shot>-b.png and logs what it hears, see DevClientHooks), runs the "after join"
# commands, waits for the client to quit, then stops the server.
#
# Usage: tools/dev-screenshot.sh <shot> "<x,y,z,yaw,pitch>" <setup-file> [after-join-file]
# Env:   SCREEN=radio|browse|url|search|amplifier,x,y,z, creative or sounds   SEARCH=text (Search tab)   TICKS=<client ticks>   WALK=dx,dz (blocks per second)
#        STAR=1 (press the favourite star at tick 80)   POWER=1 (press Turn off/Turn on at tick 80)   COUNTRY=DE (browse: open that country, all regions)
#        TYPE=text (clear the focused text box, type text at tick 80)   KEY=e (press that key at tick 80)
#        USE=x,y,z,plain|sneak,count[,face] (right-click the block count times from tick 80, on that face, default south)
#        ORBIT=cx,cz,r (circle the block cx,cz at radius r, a quarter turn per second, looking north)
#        CLICK=N (click list row N at tick 80, log the marked rows)   RENAME=N (rename box of favorite N at tick 75)
#        ENTER=1 (press Enter at tick 85)   VOLUME=0.4 (move the volume slider at tick 80)
#        HOVER=1 (put the mouse on the screen's range line from tick 100, so its tooltip is in the shot)
#        HOLD=item_id (the server puts that item into the player's main hand on join, e.g. minecraft:stick)
# The command files hold one server command per line (without leading slash).
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'

SHOT="${1:-radio}"
VIEW="${2:-0.5,-60,6.5,180,15}"
SETUP="${3:-/dev/null}"
AFTER="${4:-/dev/null}"
EXTRA=()
[ -n "${SCREEN:-}" ] && EXTRA+=(-PdevScreen="$SCREEN")
[ -n "${TICKS:-}" ] && EXTRA+=(-PdevTicks="$TICKS")
[ -n "${WALK:-}" ] && EXTRA+=(-PdevWalk="$WALK")
[ -n "${STAR:-}" ] && EXTRA+=(-PdevStar=1)
[ -n "${POWER:-}" ] && EXTRA+=(-PdevPower=1)
[ -n "${CLICK:-}" ] && EXTRA+=(-PdevClick="$CLICK")
[ -n "${RENAME:-}" ] && EXTRA+=(-PdevRename="$RENAME")
[ -n "${ENTER:-}" ] && EXTRA+=(-PdevEnter=1)
[ -n "${VOLUME:-}" ] && EXTRA+=(-PdevVolume="$VOLUME")
[ -n "${COUNTRY:-}" ] && EXTRA+=(-PdevCountry="$COUNTRY")
[ -n "${TYPE:-}" ] && EXTRA+=(-PdevType="$TYPE")
[ -n "${KEY:-}" ] && EXTRA+=(-PdevKey="$KEY")
[ -n "${ORBIT:-}" ] && EXTRA+=(-PdevOrbit="$ORBIT")
[ -n "${SEARCH:-}" ] && EXTRA+=(-PdevSearch="$SEARCH")
[ -n "${USE:-}" ] && EXTRA+=(-PdevUse="$USE")
[ -n "${HOVER:-}" ] && EXTRA+=(-PdevHover=1)
SERVER_EXTRA=()
[ -n "${HOLD:-}" ] && SERVER_EXTRA+=(-PdevHold="$HOLD")
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 wr "$@"; }

mkdir -p run/screenshots
rm -f "run/screenshots/$SHOT.png" "run/screenshots/$SHOT-b.png"

( ./gradlew runServer -PdevScreenshot -PdevView="$VIEW" "${SERVER_EXTRA[@]}" --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do
  grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break
  sleep 3
done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

mapfile -t SETUP_CMDS < <(grep -v '^\s*$' "$SETUP")
if [ "${#SETUP_CMDS[@]}" -gt 0 ]; then rcon "${SETUP_CMDS[@]}"; fi

JOIN_LINES_BEFORE=$(grep -c 'joined the game' run/runServer.out)
( ./gradlew runClient -PdevScreenshot -PdevView="$VIEW" -PdevShot="$SHOT" "${EXTRA[@]}" --no-daemon -q > run/runClient.out 2>&1; echo "EXIT=$?" >> run/runClient.out ) &

for _ in $(seq 1 100); do
  [ "$(grep -c 'joined the game' run/runServer.out)" -gt "$JOIN_LINES_BEFORE" ] && break
  grep -q 'EXIT=' run/runClient.out 2>/dev/null && break
  sleep 3
done
sleep 7
mapfile -t AFTER_CMDS < <(grep -v '^\s*$' "$AFTER")
if [ "${#AFTER_CMDS[@]}" -gt 0 ]; then rcon "${AFTER_CMDS[@]}"; fi

for _ in $(seq 1 200); do
  grep -q 'EXIT=' run/runClient.out 2>/dev/null && break
  sleep 3
done
echo "client: $(grep 'EXIT=' run/runClient.out)"
ls -la run/screenshots | grep "$SHOT"
rcon "stop" | tail -1
wait
