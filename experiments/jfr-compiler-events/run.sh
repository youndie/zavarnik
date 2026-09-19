#!/usr/bin/env bash
# Does JFR report what the compiler did, on the settings it ships with?
#
# The fifth phase's brief starts measuring "after JFR shows no C2 compilations for 60 seconds" and
# puts JFR down for "compiler behaviour on the live service without diagnostic flags". Both are
# claims about an instrument, and an instrument is checked against a second one: the same run also
# prints every compilation with -XX:+PrintCompilation, which is a count JFR can be held against.
#
# Three arms, one program:
#   shipped   settings=profile as the JDK ships it
#   forced    settings=profile with the compilation threshold at 0 and the inlining event enabled
#   bare      settings=none with every event enabled explicitly, so no .jfc control can override
#
#   JAVA_HOME=/path/to/jdk25 ./run.sh
#
# Writes results/<stamp>.log with the counts, and results/<stamp>.<arm>.printcompilation.log with
# the raw output of the second instrument, so that the counts can be recomputed without a JDK 25.
# The .jfr files stay out of the tree: this script regenerates them, and what the research quotes is
# the count.
set -euo pipefail

cd "$(dirname "$0")"
JAVA=${JAVA_HOME:?set JAVA_HOME to the pinned JDK}/bin/java
JAVAC=${JAVA_HOME}/bin/javac
JFR=${JAVA_HOME}/bin/jfr

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

ver=$("$JAVA" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')
STAMP="$(date +%F-%H%M%S)-$(uname -s | tr 'A-Z' 'a-z')-$(uname -m)-openjdk-${ver}"
OUT="results/$STAMP.log"

cat > "$WORK/Hot.java" <<'JAVA'
public class Hot {
    static long f(long x) { return x * 31 + (x >>> 3) ^ (x << 1); }
    public static void main(String[] a) {
        long s = 0;
        for (long i = 0; i < 400_000_000L; i++) s += f(i);
        System.out.println(s);
    }
}
JAVA
"$JAVAC" -d "$WORK" "$WORK/Hot.java"

# $1 label, $2 the StartFlightRecording argument after filename=
arm() {
  local label=$1 rec=$2
  "$JAVA" -XX:StartFlightRecording="filename=$WORK/$label.jfr,$rec" -XX:+PrintCompilation \
          -cp "$WORK" Hot > "results/$STAMP.$label.printcompilation.log" 2>&1

  # A compile task is a line whose first two fields are numbers. The `n` in the flags column is a
  # native-method wrapper and not a JIT compilation, so it is counted separately rather than folded
  # into the total: JFR does not report those either, and a total that mixes them makes the two
  # instruments look further apart than they are.
  local tasks native
  tasks=$(awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ && $3!="n" {print $2}' "results/$STAMP.$label.printcompilation.log" | sort -nu | wc -l)
  native=$(awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ && $3=="n"' "results/$STAMP.$label.printcompilation.log" | wc -l)

  echo "== $label" >> "$OUT"
  echo "   recording: $rec" >> "$OUT"
  echo "   PrintCompilation: $tasks compile tasks (+ $native native wrappers)" >> "$OUT"
  "$JFR" summary "$WORK/$label.jfr" | grep -iE "jdk.Compilation|jdk.CompilerInlining|jdk.Deoptimization" \
    | sed 's/^/   JFR /' >> "$OUT"

  local ids
  ids=$("$JFR" print --events jdk.Compilation "$WORK/$label.jfr" | awk '/compileId/{print $3}' | sort -n)
  if [ -n "$ids" ]; then
    local lo hi inrange
    lo=$(echo "$ids" | head -1); hi=$(echo "$ids" | tail -1)
    inrange=$(awk -v lo="$lo" -v hi="$hi" '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ && $3!="n" && $2>=lo && $2<=hi {print $2}' \
              "results/$STAMP.$label.printcompilation.log" | sort -nu | wc -l)
    echo "   JFR compileId range: $lo..$hi; PrintCompilation tasks in that range: $inrange" >> "$OUT"
    echo "   JFR event time span: $("$JFR" print --events jdk.Compilation "$WORK/$label.jfr" \
          | awk '/startTime/{print $3}' | sed -n '1p;$p' | paste -sd' ' -)" >> "$OUT"
  fi
  echo >> "$OUT"
}

{
  echo "JFR compiler events against -XX:+PrintCompilation"
  "$JAVA" -version 2>&1 | sed 's/^/  /'
  echo "  host: $(uname -srm)"
  echo
  echo "profile.jfc, as shipped:"
  awk '/<event name="jdk.(Compilation|CompilerInlining|CompilationFailure|Deoptimization)">/,/<\/event>/' \
      "${JAVA_HOME}/lib/jfr/profile.jfc" | sed 's/^/  /'
  echo
} > "$OUT"

arm shipped "settings=profile"
arm forced  "settings=profile,jdk.Compilation#threshold=0ms,+jdk.CompilerInlining#enabled=true"
arm bare    "settings=none,+jdk.Compilation#enabled=true,+jdk.Compilation#threshold=0ms,+jdk.CompilerInlining#enabled=true,+jdk.Deoptimization#enabled=true"

echo "written: $OUT"
cat "$OUT"
