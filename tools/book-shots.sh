#!/usr/bin/env bash
# Every page of the guide book in English and German: one dev server + client run per language
# (tools/dev-screenshot.sh with SCREEN=book, scene tools/scenes/docs.txt). The pages land in
# run/screenshots/book-<lang>-NN.png. Checks on the way, PASS/FAIL each: no translation key missing, no section taller
# than a page, the joining player holds exactly one book (the welcome gift; the same player joins both times, so two
# books would mean it is given again), the book's recipe exists.
# Sets the client language in run/options.txt and puts it back to en_us.
set -u
cd "$(dirname "$0")/.."
PASS=0
FAIL=0
check() { # name, 0 = passed
  if [ "$2" -eq 0 ]; then PASS=$((PASS + 1)); echo "PASS $1"; else FAIL=$((FAIL + 1)); echo "FAIL $1"; fi
}
for L in en_us de_de; do
  sed -i "s/^lang:.*/lang:$L/" run/options.txt
  rm -f run/screenshots/book-$L-*.png
  SCREEN=book TICKS=460 PLAYER=BookReader tools/dev-screenshot.sh "book-$L" "-0.5,-60,6.5,180,15" tools/scenes/docs.txt \
    tools/scenes/book-after.txt > "run/book-$L.out" 2>&1
  grep "BOOK PAGES" run/runClient.out
  grep "BOOK KEY MISSING\|BOOK OVERFLOW" run/runClient.out
  check "$L: all keys translated, nothing overflows" "$(grep -c 'BOOK KEY MISSING\|BOOK OVERFLOW' run/runClient.out)"
  grep -q "BOOK PAGES $L" run/runClient.out; check "$L: the book opened" $?
  grep -q "Found 1 matching" "run/book-$L.out"; check "$L: the player has exactly one book" $?
  grep -q "Unlocked 1 recipe" "run/book-$L.out"; check "$L: the book's recipe exists" $?
  echo "$L: $(ls run/screenshots | grep -c "^book-$L-[0-9]") page shots"
done
sed -i "s/^lang:.*/lang:en_us/" run/options.txt
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
