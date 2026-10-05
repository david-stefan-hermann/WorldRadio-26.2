#!/usr/bin/env bash
# All README pictures in one go: one dev server + client run per shot (tools/dev-screenshot.sh, scene
# tools/scenes/docs.txt, docs-off.txt for the switched-off transmitter). The shots land in
# run/screenshots/docs-<name>.png and -b.png (a little later); copy the good ones to docs/ by hand.
# Usage: tools/docs-shots.sh [name ...]   (no names = all)
cd "$(dirname "$0")/.."
VIEW="-0.5,-60,6.5,180,15"
T="-4,-60,0"
shot() { # name, scene, then env assignments
  local name="$1" scene="tools/scenes/$2.txt"; shift 2
  [ -n "$ONLY" ] && [[ " $ONLY " != *" $name "* ]] && return
  echo "== $name"
  env "$@" tools/dev-screenshot.sh "docs-$name" "$VIEW" "$scene" | tail -4
}
ONLY="$*"
shot blocks docs TICKS=260
shot favourites docs SCREEN=radio,$T
shot range-hint docs SCREEN=radio,$T HOVER=1
shot browse docs SCREEN=browse,$T COUNTRY=DE
shot url docs SCREEN=url,$T
shot search docs SCREEN=search,$T SEARCH=jazz
shot radio-off docs-off SCREEN=radio,$T
shot amplifier-screen docs SCREEN=amplifier,-1,-60,0
shot tuner docs SCREEN=tuner,3,-60,0
shot portable docs SCREEN=portable HOLD=worldradio:portable_radio CLICK=1
shot creative-tab docs SCREEN=creative
