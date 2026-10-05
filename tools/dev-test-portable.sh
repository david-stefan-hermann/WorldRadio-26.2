#!/usr/bin/env bash
# Server-only test of putting a portable radio down: starts the dev server with -PdevPortable (see DevHooks.portableTest:
# a fake player right-clicks, sneak-right-clicks, the block is broken), prints the PASS/FAIL lines and a total.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 wr "$@"; }

( ./gradlew runServer -PdevPortable --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do
  grep -q 'PORTABLE TEST done\|EXIT=' run/runServer.out 2>/dev/null && break
  sleep 3
done
grep -o 'PORTABLE \(PASS\|FAIL\).*' run/runServer.out
PASS=$(grep -c 'PORTABLE PASS' run/runServer.out)
FAIL=$(grep -c 'PORTABLE FAIL' run/runServer.out)
grep -q 'PORTABLE TEST done' run/runServer.out || { echo "the test did not run"; tail -30 run/runServer.out; FAIL=$((FAIL + 1)); }
grep -q 'EXIT=' run/runServer.out || rcon "stop" > /dev/null
wait
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
