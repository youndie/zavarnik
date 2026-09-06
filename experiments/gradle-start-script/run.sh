#!/usr/bin/env bash
# The start-script experiment behind §1.4 and D1–D2 of docs/research/research-architecture.md:
# can the script Gradle's `application` plugin generates be both the training launcher and the
# production launcher? `build.gradle.kts` post-processes the script with a guard that adds
# -XX:AOTCache only when lib/app.aot exists, so before training the flag is absent and JAVA_OPTS
# may carry -XX:AOTCacheOutput; after training the same script picks the cache up.
#
# Usage:  JAVA_HOME=/path/to/jdk-25 GRADLE=/path/to/gradle ./run.sh
# Needs network for the first dependency resolution. Asserts nothing; the log is the result.
set -u
cd "$(dirname "$0")"
GRADLE=${GRADLE:-gradle}
echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ) $(uname -sm) $(hostname)"
"${JAVA_HOME:?set JAVA_HOME}/bin/java" -version 2>&1 | sed 's/^/# /'
"$GRADLE" --version 2>/dev/null | grep -E "^Gradle" | sed 's/^/# /'
rm -rf build; "$GRADLE" -q installDist 2>&1 | tail -3
S=build/install/demo/bin/demo; HERE=$(pwd)
echo; echo "=== script: classpath line and the guard"; grep -n "^CLASSPATH=\|^DEFAULT_JVM_OPTS=\|app.aot" "$S"
echo; echo "=== G1 training through the real script: JAVA_OPTS carries -XX:AOTCacheOutput, no cache exists yet"
JAVA_OPTS="-XX:AOTCacheOutput=$HERE/build/install/demo/lib/app.aot" "$S" 2>&1 | grep -E "complete|Only one" | sed "s#$HERE#<here>#"
ls -la build/install/demo/lib/ | awk 'NR>3 {print $5, $9}'
echo; echo "=== G2 production run through the same script, cache exists now"
JAVA_OPTS="-Xlog:class+load -Xlog:aot" "$S" 2>&1 | grep -E "demo.App source|com.google.gson.Gson source|Opened AOT" | sed "s#$HERE#<here>#; s/^\[[0-9.]*s\]//"
echo; echo "=== G3 verify mode: -XX:AOTMode=on from JAVA_OPTS on top of the script's -XX:AOTCache"
JAVA_OPTS="-XX:AOTMode=on" "$S" >/dev/null 2>&1; echo "exit=$?"
echo; echo "=== G4 the whole install dir relocated, training dir deleted, -XX:AOTMode=on"
REL=$(mktemp -d)/demo; cp -pR build/install/demo "$REL"; rm -rf build/install
JAVA_OPTS="-XX:AOTMode=on -Xlog:class+load -Xlog:class+path=info" "$REL/bin/demo" 2>&1 | grep -E "demo.App source|Longest common prefix:|app classpath validation" | sed "s#$REL#<relocated>#; s/^\[[0-9.]*s\]//"
JAVA_OPTS="-XX:AOTMode=on" "$REL/bin/demo" >/dev/null 2>&1; echo "exit=$?"
echo; echo "=== G5 retraining while a cache exists: the script adds -XX:AOTCache, JAVA_OPTS adds -XX:AOTCacheOutput"
JAVA_OPTS="-XX:AOTCacheOutput=$REL/lib/new.aot" "$REL/bin/demo" 2>&1 | head -2
echo; echo "=== G6 the same pair with -XX:AOTMode=off"
"$JAVA_HOME/bin/java" -XX:AOTCache="$REL/lib/app.aot" -XX:AOTMode=off -XX:AOTCacheOutput="$REL/lib/new.aot" -cp "$REL/lib/demo.jar" demo.App 2>&1 | head -2
rm -rf "$(dirname "$REL")"
