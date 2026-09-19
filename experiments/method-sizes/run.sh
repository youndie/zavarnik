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
# into lib/, which is not committed. The pinned stack itself is ../stack.sh.
set -euo pipefail
cd "$(dirname "$0")"

# The pinned stack and how the jars are fetched live one level up, because the codegen census
# reads the same classpath and a second copy of the list bumps in one scan and not the other.
. "$(dirname "$0")/../stack.sh"
mkdir -p lib results
resolve_all

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
