#!/usr/bin/env bash
# Compiles the Minecraft-free signal package with plain javac and runs SignalGraphTest.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
jdk() { "$JAVA_HOME/bin/$1" "${@:2}"; }
OUT=build/signal-test
rm -rf "$OUT" && mkdir -p "$OUT"
jdk javac -d "$OUT" src/main/java/worldradio/signal/*.java tools/SignalGraphTest.java || exit 1
jdk java -cp "$OUT" SignalGraphTest
