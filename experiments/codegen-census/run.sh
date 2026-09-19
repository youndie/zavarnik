#!/usr/bin/env bash
# How often each Kotlin codegen pattern occurs on the request path, and in whose artifact.
#
# The second half of RQ5. `microbench/.../Codegen.kt` prices the patterns; this counts them, over
# the same pinned classpath the method-size scan reads (../stack.sh). A price without a count says
# nothing: a pattern that costs 3 ns and occurs twice per process is not a finding.
#
#   ./run.sh
#
# Static counts. An occurrence is not an execution - see the header of patterns.py.
set -euo pipefail
cd "$(dirname "$0")"
. "$(dirname "$0")/../stack.sh"
mkdir -p lib results
resolve_all

STAMP="$(date +%F-%H%M%S)-ktor-$KTOR-exposed-$EXPOSED"
OUT="results/$STAMP.log"
{
  echo "Kotlin codegen patterns on the request path, by artifact"
  echo "  ktor $KTOR, kotlin $KOTLIN, coroutines $KOTLINX_COR, serialization $KOTLINX_SER, exposed $EXPOSED"
  echo "  artifacts: ${#JARS[@]}"
  [ ${#MISSING[@]} -gt 0 ] && printf '  NOT RESOLVED: %s\n' "${MISSING[@]}"
  echo
} > "$OUT"
python3 patterns.py --json "results/$STAMP.json" "${JARS[@]}" >> "$OUT"
echo "written: $OUT"
cat "$OUT"
