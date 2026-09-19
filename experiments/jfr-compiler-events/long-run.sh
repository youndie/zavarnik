#!/usr/bin/env bash
# The control for run.sh: the same three questions, on a workload that keeps compiling.
#
# run.sh uses a single hot loop, and on it JFR's compiler events covered a window of some twenty
# milliseconds. That read as "JFR is not a census". It was the program: a one-method loop finishes
# compiling before the recording is live, so what looked like a truncated instrument was a
# truncated subject. This script removes that explanation — 3000 methods, each made hot in turn, so
# compilation runs for the whole of the program rather than for its first second.
#
#   JAVA_HOME=/path/to/jdk25 ./long-run.sh
#
# Three arms for coverage and, separately, the price of each instrument, three interleaved repeats:
#
#   shipped   settings=profile as the JDK ships it
#   bare      settings=none with the compiler events enabled explicitly
#   cost      none / JFR-with-jdk.Compilation / -XX:+PrintCompilation, wall clock
set -euo pipefail

cd "$(dirname "$0")"
JAVA=${JAVA_HOME:?set JAVA_HOME to the pinned JDK}/bin/java
JAVAC=${JAVA_HOME}/bin/javac
JFR=${JAVA_HOME}/bin/jfr
N=${N:-3000}          # methods, one made hot after another
ITER=${ITER:-60000}   # iterations per method: enough to tier up to C2

WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
ver=$("$JAVA" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')
STAMP="$(date +%F-%H%M%S)-$(uname -s | tr 'A-Z' 'a-z')-$(uname -m)-openjdk-${ver}"
OUT="results/$STAMP.long.log"

python3 - "$N" "$ITER" > "$WORK/Many.java" <<'PY'
import sys
n, it = int(sys.argv[1]), int(sys.argv[2])
p = ["public class Many {"]
for k in range(n):
    p.append("    static long m%d(long x) { return x * %d + (x >>> 3) ^ (x << 1); }" % (k, k + 7))
    p.append("    static long r%d() { long s = 0; for (long i = 0; i < %dL; i++) s += m%d(i); return s; }" % (k, it, k))
# main is deliberately one straight-line call per method: it exceeds the huge-method limit and stays
# interpreted, which is what we want - it must not itself become a compilation of interest.
p.append("    public static void main(String[] a) {")
p.append("        long s = 0;")
p += ["        s += r%d();" % k for k in range(n)]
p.append("        System.out.println(s);")
p.append("    }")
p.append("}")
print("\n".join(p))
PY
"$JAVAC" -d "$WORK" "$WORK/Many.java"

EV="+jdk.Compilation#enabled=true,+jdk.Compilation#threshold=0ms,+jdk.CompilerInlining#enabled=true,+jdk.Deoptimization#enabled=true"

# $1 label, $2 recording arguments
arm() {
  local label=$1 rec=$2
  local pc="results/$STAMP.long-$label.printcompilation.log"
  "$JAVA" -XX:StartFlightRecording="filename=$WORK/$label.jfr,$rec" -XX:+PrintCompilation -cp "$WORK" Many > "$pc" 2>&1

  local tasks ids lo hi inrange before
  tasks=$(awk '$1~/^[0-9]+$/&&$2~/^[0-9]+$/&&$3!="n"{print $2}' "$pc" | sort -nu | wc -l | tr -d ' ')
  # via a file, not a pipe: `echo "$ids" | head -1` over six thousand lines closes the pipe
  # under the writer, and pipefail turns that SIGPIPE into a dead script.
  "$JFR" print --events jdk.Compilation "$WORK/$label.jfr" | awk '/compileId/{print $3}' | sort -n > "$WORK/$label.ids"

  echo "== $label" >> "$OUT"
  echo "   recording: $rec" >> "$OUT"
  echo "   PrintCompilation: $tasks non-native compile tasks, last at $(awk '$1~/^[0-9]+$/&&$2~/^[0-9]+$/{print $1}' "$pc" | tail -1) ms" >> "$OUT"
  "$JFR" summary "$WORK/$label.jfr" | grep -iE "jdk.Compilation |jdk.CompilerInlining|jdk.Deoptimization" | sed 's/^/   JFR /' >> "$OUT"
  if [ -s "$WORK/$label.ids" ]; then
    lo=$(head -1 "$WORK/$label.ids"); hi=$(tail -1 "$WORK/$label.ids")
    inrange=$(awk -v lo="$lo" -v hi="$hi" '$1~/^[0-9]+$/&&$2~/^[0-9]+$/&&$3!="n"&&$2>=lo&&$2<=hi{print $2}' "$pc" | sort -nu | wc -l | tr -d ' ')
    before=$(awk -v lo="$lo" '$1~/^[0-9]+$/&&$2~/^[0-9]+$/&&$3!="n"&&$2<lo{print $2}' "$pc" | sort -nu | wc -l | tr -d ' ')
    echo "   jdk.Compilation ids $lo..$hi: $(wc -l < "$WORK/$label.ids" | tr -d ' ') events against $inrange tasks in that range; $before tasks compiled before the recording was live" >> "$OUT"
    echo "   jdk.CompilerInlining ids: $("$JFR" print --events jdk.CompilerInlining "$WORK/$label.jfr" | awk '/compileId/{print $3}' | sort -n | sed -n '1p;$p' | paste -sd'-' -)" >> "$OUT"
  fi
  echo >> "$OUT"
}

{
  echo "JFR compiler events on a workload that keeps compiling ($N methods x $ITER iterations)"
  "$JAVA" -version 2>&1 | sed 's/^/  /'
  echo "  host: $(uname -srm)"
  echo
} > "$OUT"

arm shipped "settings=profile"
arm bare    "settings=none,$EV"

# The price of watching. Interleaved, three repeats, medians read by a person: an absolute second on
# this machine says nothing, the ratio between arms of one sweep says what an instrument costs.
echo "== cost, wall seconds, three interleaved repeats" >> "$OUT"
one() { { /usr/bin/time -p "$@" >/dev/null; } 2>&1 | awk '/^real/{print $2}'; }
for i in 1 2 3; do
  a=$(one "$JAVA" -cp "$WORK" Many)
  b=$(one "$JAVA" -XX:StartFlightRecording="filename=$WORK/cost$i.jfr,settings=none,+jdk.Compilation#enabled=true,+jdk.Compilation#threshold=0ms" -cp "$WORK" Many)
  c=$(one "$JAVA" -XX:+PrintCompilation -cp "$WORK" Many)
  echo "   repeat $i: none $a   jfr(jdk.Compilation) $b   -XX:+PrintCompilation $c" >> "$OUT"
done

echo "written: $OUT"
cat "$OUT"
