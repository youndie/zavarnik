#!/usr/bin/env bash
# The R8 baseline of RQ0: the whole runtime classpath of the installed service through R8 with
# profile/r8.pro, into one jar. Prints what R8 kept and removed; run.sh measures the result with
#     START="java -cp <out>/bench-r8.jar bench.MainKt" LABEL=r8 profile/run.sh
# Usage:  JAVA_HOME=… [R8_JAR=~/tools/r8-9.4.17.jar] [OUT=~/bench-results/r8-jar] profile/r8.sh
set -u
cd "$(dirname "$0")/.."
JAVA=${JAVA_HOME:?set JAVA_HOME}/bin/java; R8_JAR=${R8_JAR:-$HOME/tools/r8-9.4.17.jar}; OUT=${OUT:-$HOME/bench-results/r8-jar}
LIB=build/install/bench/lib; rm -rf "$OUT"; mkdir -p "$OUT"
echo "# input: $(ls $LIB/*.jar | wc -l) jars, $(du -ch $LIB/*.jar | tail -1 | cut -f1)"
start=$(date +%s)
"$JAVA" -Xmx3g -cp "$R8_JAR" com.android.tools.r8.R8 --release --classfile --lib "$JAVA_HOME" \
  --pg-conf profile/r8.pro --pg-map-output "$OUT/bench-r8.map" --output "$OUT/bench-r8.jar" $LIB/*.jar 2>&1 | tail -20
echo "# R8 took $(( $(date +%s) - start )) s; output $(du -h "$OUT/bench-r8.jar" | cut -f1); classes $(unzip -l "$OUT/bench-r8.jar" | grep -c '\.class$') (input: $(for j in $LIB/*.jar; do unzip -l "$j" | grep -c '\.class$'; done | paste -sd+ | bc))"
echo "# Intrinsics.check* call sites left in bench classes: $("$JAVA" -cp "$OUT/bench-r8.jar" -version >/dev/null 2>&1; unzip -o -q "$OUT/bench-r8.jar" 'bench/*' -d "$OUT/x" && grep -l -a 'Intrinsics' "$OUT"/x/bench/*.class | wc -l) of $(ls "$OUT"/x/bench/*.class | wc -l) classes"
