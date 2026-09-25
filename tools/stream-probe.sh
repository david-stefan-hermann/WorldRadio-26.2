#!/usr/bin/env bash
# Compiles the Minecraft-free audio classes with JLayer and runs StreamProbe (args: [--pan -1|0|1] [stream addresses]).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
jdk() { "$JAVA_HOME/bin/$1" "${@:2}"; }
JLAYER=$(cygpath -w "$(find ~/.gradle/caches/modules-2/files-2.1/javazoom/jlayer -name "jlayer-*.jar" | grep -v sources | head -1)")
OUT=build/stream-probe
rm -rf "$OUT" && mkdir -p "$OUT"
A=src/client/java/worldradio/client/audio
jdk javac -cp "$JLAYER" -d "$OUT" $A/StationStream.java $A/PcmBuffer.java $A/IcyInputStream.java $A/Mp3Decoder.java \
  $A/StereoMixer.java src/main/java/worldradio/signal/Panner.java tools/StreamProbe.java || exit 1
jdk java -cp "$OUT;$JLAYER" StreamProbe "$@"
