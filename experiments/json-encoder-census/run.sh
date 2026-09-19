#!/usr/bin/env bash
# How many Encoder implementations does a JSON-only service actually load?
#
# RQ6 of the fifth phase asks whether the Encoder and Decoder calls inside a generated serialiser
# stay monomorphic and inline. The jar ships six encoder implementations, so the answer is a
# question about which ones the process loads, not about how many exist. That is a runtime fact and
# this is the cheapest way to get it: a program shaped like the list endpoint, run under
# -verbose:class, with and without one call that goes through the tree.
#
#   JAVA_HOME=/path/to/jdk25 ./run.sh
#
# Needs the pinned Kotlin compiler and the serialization plugin in the Gradle cache - the same
# compiler the stand pins, so that the generated serialiser is the one the service would run. It
# compiles a single file and runs it twice; there is no Gradle build and no daemon.
set -euo pipefail
cd "$(dirname "$0")"

JAVA=${JAVA_HOME:?set JAVA_HOME to the pinned JDK}/bin/java
CACHE=${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}
KOTLIN=${KOTLIN_VERSION:-2.4.20}
SER=${SERIALIZATION_VERSION:-1.11.0}

# One jar per coordinate, found rather than spelled: the cache puts a hash directory in the middle,
# and a path with a hash in it is a path that rots on the next dependency bump.
jar() { # group artifact version
  local f
  f=$(find "$CACHE/$1/$2/$3" -name "$2-$3.jar" ! -name "*sources*" 2>/dev/null | head -1)
  [ -n "$f" ] || { echo "not in the Gradle cache: $1:$2:$3" >&2; exit 1; }
  echo "$f"
}

STD=$(jar org.jetbrains.kotlin kotlin-stdlib "$KOTLIN")
KC=$(jar org.jetbrains.kotlin kotlin-compiler-embeddable "$KOTLIN")
KSER=$(jar org.jetbrains.kotlin kotlin-serialization-compiler-plugin-embeddable "$KOTLIN")
SRT=$(jar org.jetbrains.kotlin kotlin-script-runtime "$KOTLIN")
SCORE=$(jar org.jetbrains.kotlinx kotlinx-serialization-core-jvm "$SER")
SJSON=$(jar org.jetbrains.kotlinx kotlinx-serialization-json-jvm "$SER")
# The embeddable compiler does not carry these and fails deep in codegen without them, with an
# error that names the constructor it was generating rather than the missing class.
REF=$(find "$CACHE/org.jetbrains.kotlin/kotlin-reflect" -name "kotlin-reflect-2.*.jar" ! -name "*sources*" | sort -V | tail -1)
COR=$(find "$CACHE/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" -name "*.jar" ! -name "*sources*" | sort -V | tail -1)
ANN=$(find "$CACHE/org.jetbrains/annotations" -name "annotations-*.jar" ! -name "*sources*" | sort -V | tail -1)

WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
RUNTIME="$STD:$SCORE:$SJSON"
STAMP="$(date +%F-%H%M%S)-kotlin-$KOTLIN-serialization-$SER"
OUT="results/$STAMP.log"

cp Probe.kt "$WORK/"
"$JAVA" -cp "$KC:$STD:$SRT:$REF:$COR:$ANN" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$KSER" -no-stdlib -cp "$RUNTIME" -d "$WORK/out" "$WORK/Probe.kt" 2>&1 \
  | grep -viE "^warning:|is deprecated" || true

{
  echo "Encoder implementations loaded by a JSON-only program"
  "$JAVA" -version 2>&1 | sed 's/^/  /'
  echo "  kotlin $KOTLIN, kotlinx.serialization $SER"
  echo
} > "$OUT"

for arm in string element; do
  "$JAVA" -cp "$WORK/out:$RUNTIME" -verbose:class ProbeKt "$arm" 2>&1 \
    | grep -oE "kotlinx\.serialization\.[A-Za-z0-9_.$]*(Encoder|Decoder)[A-Za-z0-9_.$]*" \
    | sort -u > "$WORK/$arm.txt"
  {
    echo "== arm $arm: $(wc -l < "$WORK/$arm.txt" | tr -d ' ') loaded classes named Encoder or Decoder"
    sed 's/^/   /' "$WORK/$arm.txt"
    echo
  } >> "$OUT"
done

{
  echo "== what one encodeToJsonElement adds"
  comm -13 "$WORK/string.txt" "$WORK/element.txt" | sed 's/^/   /'
  echo
} >> "$OUT"

echo "written: $OUT"
cat "$OUT"
