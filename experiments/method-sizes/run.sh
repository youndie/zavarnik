#!/usr/bin/env bash
# Bytecode size of every method on the request path, grouped by the artifact it came from.
#
# Phase 1 of the brief - the static scan whose output is the shortlist for RQ1. The second phase
# scanned application code only and found 10 of 283 methods over FreqInlineSize. That was the right
# scope for a plugin over application code; it is the wrong scope here, where application code owns
# 0.9-4.0 % of a real service's CPU and the request path is mostly framework.
#
#   ./run.sh
#
# Artifacts come from the Gradle cache when they are there and from Maven Central when they are not,
# into lib/, which is not committed. The coordinates below ARE the pinned stack - editing them is
# how the scan follows a version bump.
set -euo pipefail
cd "$(dirname "$0")"

CACHE=${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}
CENTRAL=${MAVEN_CENTRAL:-https://repo1.maven.org/maven2}
mkdir -p lib results

KTOR=${KTOR_VERSION:-3.5.2}
KOTLIN=${KOTLIN_VERSION:-2.4.20}
KOTLINX_SER=${KOTLINX_SER_VERSION:-1.11.0}
KOTLINX_COR=${KOTLINX_COR_VERSION:-1.11.0}
EXPOSED=${EXPOSED_VERSION:-1.4.0}
NETTY=${NETTY_VERSION:-4.2.16.Final}
HIKARI=${HIKARI_VERSION:-7.0.2}
PGJDBC=${PGJDBC_VERSION:-42.7.13}

COORDS="
io.ktor:ktor-server-core-jvm:$KTOR
io.ktor:ktor-server-netty-jvm:$KTOR
io.ktor:ktor-server-content-negotiation-jvm:$KTOR
io.ktor:ktor-serialization-jvm:$KTOR
io.ktor:ktor-serialization-kotlinx-jvm:$KTOR
io.ktor:ktor-serialization-kotlinx-json-jvm:$KTOR
io.ktor:ktor-http-jvm:$KTOR
io.ktor:ktor-utils-jvm:$KTOR
io.ktor:ktor-io-jvm:$KTOR
io.ktor:ktor-events-jvm:$KTOR
org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$KOTLINX_COR
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$KOTLINX_SER
org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$KOTLINX_SER
org.jetbrains.exposed:exposed-core:$EXPOSED
org.jetbrains.exposed:exposed-jdbc:$EXPOSED
com.zaxxer:HikariCP:$HIKARI
org.postgresql:postgresql:$PGJDBC
io.netty:netty-common:$NETTY
io.netty:netty-buffer:$NETTY
io.netty:netty-transport:$NETTY
io.netty:netty-codec:$NETTY
io.netty:netty-codec-base:$NETTY
io.netty:netty-codec-http:$NETTY
io.netty:netty-handler:$NETTY
io.netty:netty-resolver:$NETTY
"

resolve() { # group:artifact:version -> path on stdout
  local g=${1%%:*} rest=${1#*:} a v f
  a=${rest%%:*}; v=${rest#*:}
  f=$(find "$CACHE/$g/$a/$v" -name "$a-$v.jar" ! -name "*sources*" 2>/dev/null | head -1)
  if [ -z "$f" ]; then
    f="lib/$a-$v.jar"
    if [ ! -s "$f" ]; then
      # `${g//./\/}` looks right and yields io\/ktor - a literal backslash, and a 404 that
      # reads like a missing artifact rather than a broken URL.
      local path; path=$(printf %s "$g" | tr . /)
      curl -fsSL -o "$f" "$CENTRAL/$path/$a/$v/$a-$v.jar" || { rm -f "$f"; echo "could not fetch $1" >&2; return 1; }
    fi
  fi
  echo "$f"
}

JARS=(); MISSING=()
for c in $COORDS; do
  if p=$(resolve "$c"); then JARS+=("$p"); else MISSING+=("$c"); fi
done

STAMP="$(date +%F-%H%M%S)-ktor-$KTOR-exposed-$EXPOSED"
OUT="results/$STAMP.log"

# The threshold is read from the JVM rather than written here: FreqInlineSize is a platform-
# dependent flag that happens to agree on two platforms today, and a number typed into a script
# outlives the fact that made it true.
THRESH=325
if [ -n "${JAVA_HOME:-}" ]; then
  THRESH=$("$JAVA_HOME/bin/java" -XX:+PrintFlagsFinal -version 2>/dev/null \
           | awk '/FreqInlineSize/{print $4}') || THRESH=325
fi

{
  echo "Method sizes on the request path, by artifact"
  echo "  FreqInlineSize read from the JVM: $THRESH"
  [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/  /'
  echo "  artifacts: ${#JARS[@]}"
  [ ${#MISSING[@]} -gt 0 ] && printf '  NOT RESOLVED: %s\n' "${MISSING[@]}"
  echo
} > "$OUT"

python3 scan.py --threshold "$THRESH" --top 25 --json "results/$STAMP.json" "${JARS[@]}" >> "$OUT"
echo "written: $OUT"
cat "$OUT"
