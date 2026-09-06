#!/usr/bin/env bash
# AOT-cache validation experiments for zavarnik (docs/research/research-architecture.md §1).
#
# Usage:  JAVA_HOME=/path/to/jdk25 ./run.sh [workdir]
#
# Every case prints one line per observation and ends with a `shared=N file=M` count: `shared`
# is the number of classes the JVM reports as loaded from the AOT cache, `file` the number it
# loaded from jars. A case where the cache was accepted has shared>0 and file=0 for the app class.
# Nothing here asserts; the log is the result and is committed under results/.
set -u
WORK=${1:-$(mktemp -d)}
JAVA=${JAVA_HOME:?set JAVA_HOME}/bin/java
JAVAC=$JAVA_HOME/bin/javac
JAR=$JAVA_HOME/bin/jar
echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ) $(uname -sm) $(hostname)"
$JAVA -version 2>&1 | sed 's/^/# /'
rm -rf "$WORK"/{src,out,a,b,abs1,abs2,n1,n2,h,dk}; mkdir -p "$WORK"/{src,a/lib,b/lib,h}; cd "$WORK"

cat > src/App.java <<'J'
import java.util.*; import java.util.function.*; import java.util.stream.*;
public class App { public static void main(String[] a) throws Exception {
  List<Integer> xs = IntStream.range(0,1000).boxed().collect(Collectors.toList());
  Function<Integer,Integer> f = x -> x*2; Map<String,Integer> m = new HashMap<>();
  for (int i=0;i<2000;i++){ m.put("k"+i, f.apply(i)); }
  System.out.println("sum=" + xs.stream().map(f).mapToInt(Integer::intValue).sum() + " map=" + m.size());
}}
J
cat > src/H.java <<'J'
public class H { public static void main(String[] a){ System.out.println("hi"); Runtime.getRuntime().halt(0);} }
J
cat > src/L.java <<'J'
public class L { public static void main(String[] a) throws Exception { System.out.println("loop"); Thread.sleep(60000);} }
J
cat > src/Ag.java <<'J'
public class Ag { public static void premain(String a){ System.out.println("agent"); } }
J
$JAVAC -d out src/*.java
$JAR --create --file a/lib/app.jar -C out App.class
$JAR --create --file b/lib/other.jar -C out H.class
$JAR --create --file h/l.jar -C out L.class
printf 'Premain-Class: Ag\n' > h/m.txt; $JAR --create --file h/ag.jar --manifest h/m.txt -C out Ag.class
cp -p a/lib/app.jar b/lib/app.jar

run()  { $JAVA "$@" -Xlog:class+load=info -Xlog:aot=info -Xlog:class+path=info App 2>&1; }
summ() { R="$(cat)"; echo "$R" | grep -E "App source:|Longest common prefix|app classpath validation|does not match|not the one|Unable to use|Mismatched values|cannot be used|non-empty directory|CDS will be disabled" | sed 's/^\[[0-9.]*s\]//' | head -8; echo "shared=$(echo "$R" | grep -c 'source: shared objects file') file=$(echo "$R" | grep -c 'App source: file:')"; }
case_() { echo; echo "=== $1"; }

case_ "T0 train in a/ (relative classpath), one-step workflow"
(cd a && $JAVA -XX:AOTCacheOutput=lib/app.aot -Xlog:class+path=info -cp lib/app.jar App 2>&1 | grep -E "Writing classpath|\(app|complete" | sed 's/^\[[0-9.]*s\]//')
cp -p a/lib/app.aot b/lib/app.aot

cd b
case_ "R1 relocated to b/, relative classpath";            run -XX:AOTCache=lib/app.aot -cp lib/app.jar | summ
case_ "R2 relocated to b/, absolute classpath";            run -XX:AOTCache=lib/app.aot -cp "$WORK/b/lib/app.jar" | summ
case_ "R3 jar touched (mtime changed, size same)";         echo "mtime before=$(stat -c %Y lib/app.jar 2>/dev/null || stat -f %m lib/app.jar)"; sleep 1.1; touch lib/app.jar; echo "mtime after =$(stat -c %Y lib/app.jar 2>/dev/null || stat -f %m lib/app.jar)"; run -XX:AOTCache=lib/app.aot -cp lib/app.jar | summ; cp -p ../a/lib/app.jar lib/app.jar
case_ "R4 jar replaced by a different jar, same name";     cp lib/other.jar lib/app.jar; run -XX:AOTCache=lib/app.aot -cp lib/app.jar | summ; cp -p ../a/lib/app.jar lib/app.jar
case_ "R5 extra jar appended";                             run -XX:AOTCache=lib/app.aot -cp lib/app.jar:lib/other.jar | summ
case_ "R6 extra jar prepended";                            run -XX:AOTCache=lib/app.aot -cp lib/other.jar:lib/app.jar | summ
case_ "R7 directory appended";                             mkdir -p conf; run -XX:AOTCache=lib/app.aot -cp lib/app.jar:conf | summ
case_ "R8 --add-modules jdk.httpserver";                   run -XX:AOTCache=lib/app.aot --add-modules jdk.httpserver -cp lib/app.jar | summ
case_ "R9 -javaagent";                                     run -XX:AOTCache=lib/app.aot -javaagent:../h/ag.jar -cp lib/app.jar | summ
case_ "R10 SerialGC at runtime (trained with the default GC)"; run -XX:AOTCache=lib/app.aot -XX:+UseSerialGC -cp lib/app.jar | summ
case_ "R11 ZGC at runtime";                                run -XX:AOTCache=lib/app.aot -XX:+UseZGC -cp lib/app.jar | summ
case_ "R12 -Xmx changed at runtime";                       run -XX:AOTCache=lib/app.aot -Xmx1g -cp lib/app.jar | summ
case_ "R13 -XX:AOTMode=on with ZGC: exit code";            $JAVA -XX:AOTMode=on -XX:AOTCache=lib/app.aot -XX:+UseZGC -cp lib/app.jar App >/dev/null 2>&1; echo "exit=$?"
case_ "R14 -XX:AOTMode=on with a replaced jar: exit code"; cp lib/other.jar lib/app.jar; $JAVA -XX:AOTMode=on -XX:AOTCache=lib/app.aot -cp lib/app.jar App >/dev/null 2>&1; echo "exit=$?"; cp -p ../a/lib/app.jar lib/app.jar
case_ "R15 -XX:AOTMode=on with a missing cache file: exit code"; $JAVA -XX:AOTMode=on -XX:AOTCache=lib/nope.aot -cp lib/app.jar App >/dev/null 2>&1; echo "exit=$?"
case_ "R16 default mode with a missing cache file: exit code and stderr"; OUT=$($JAVA -XX:AOTCache=lib/nope.aot -cp lib/app.jar App 2>&1); echo "exit=$?"; echo "$OUT" | grep -c "\[error\]" | sed 's/^/error lines=/'
cd ..

case_ "D1 train with ABSOLUTE paths in abs1/, run in abs2/ with abs1/ deleted (the container case)"
mkdir -p abs1/lib abs2/lib; cp -p a/lib/app.jar abs1/lib/; cp -p a/lib/app.jar abs2/lib/
(cd abs1 && $JAVA -XX:AOTCacheOutput="$WORK/abs1/lib/app.aot" -cp "$WORK/abs1/lib/app.jar" App >/dev/null 2>&1); cp -p abs1/lib/app.aot abs2/lib/; rm -rf abs1
(cd / && run -XX:AOTCache="$WORK/abs2/lib/app.aot" -cp "$WORK/abs2/lib/app.jar" | summ)

case_ "D2 jar mtime normalised to a constant BEFORE training, copied with mtime preserved, training dir deleted"
mkdir -p n1/lib n2/lib; cp a/lib/app.jar n1/lib/; touch -t 200001010000 n1/lib/app.jar
(cd n1 && $JAVA -XX:AOTCacheOutput="$WORK/n1/lib/app.aot" -cp "$WORK/n1/lib/app.jar" App >/dev/null 2>&1); cp -p n1/lib/app.jar n1/lib/app.aot n2/lib/; rm -rf n1
(cd / && run -XX:AOTCache="$WORK/n2/lib/app.aot" -cp "$WORK/n2/lib/app.jar" | summ)

case_ "D3 docker COPY preserves mtime?"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  mkdir -p dk; cp -p n2/lib/app.jar dk/; printf 'FROM busybox\nCOPY app.jar /app/app.jar\n' > dk/Dockerfile
  echo "host mtime  : $(stat -c %Y dk/app.jar 2>/dev/null || stat -f %m dk/app.jar)"
  (cd dk && docker build -q -t zavarnik-mtime . >/dev/null 2>&1 && echo "image mtime : $(docker run --rm zavarnik-mtime stat -c %Y /app/app.jar)")
else echo "docker not available here"; fi

case_ "E1 training run ends with Runtime.halt(0): is the cache written?"
(cd h && $JAVA -XX:AOTCacheOutput=h.aot -cp ../b/lib/other.jar H >/dev/null 2>&1; ls h.aot >/dev/null 2>&1 && echo "written $(wc -c < h.aot) bytes" || echo "not written")
case_ "E2 training run killed with SIGTERM"
(cd h && ($JAVA -XX:AOTCacheOutput=l.aot -cp l.jar L >/dev/null 2>&1 & echo $! > pid); sleep 2; kill -TERM "$(cat pid)"; sleep 4; ls l.aot >/dev/null 2>&1 && echo "written $(wc -c < l.aot) bytes" || echo "not written")
case_ "E3 training run killed with SIGKILL"
(cd h && ($JAVA -XX:AOTCacheOutput=k.aot -cp l.jar L >/dev/null 2>&1 & echo $! > pid); sleep 2; kill -KILL "$(cat pid)"; sleep 3; ls k.aot >/dev/null 2>&1 && echo "written $(wc -c < k.aot) bytes" || echo "not written")
case_ "E4 training with a DIRECTORY on the classpath (what 'gradle run' does)"
(cd a && $JAVA -XX:AOTCacheOutput=dir.aot -cp ../out App 2>&1 | grep -E "non-empty|Cannot" | head -2; ls dir.aot >/dev/null 2>&1 && echo "written" || echo "not written")

case_ "M1 wall-clock ms of 20 runs, sorted: without cache (-XX:AOTMode=off) then with cache"
t() { s=$(date +%s%N); $JAVA "$@" App >/dev/null 2>&1; e=$(date +%s%N); echo $(( (e-s)/1000000 )); }
(cd b; for i in $(seq 20); do t -XX:AOTMode=off -cp lib/app.jar; done | sort -n | tr '\n' ' '; echo; for i in $(seq 20); do t -XX:AOTCache=lib/app.aot -cp lib/app.jar; done | sort -n | tr '\n' ' '; echo)
case_ "M2 cache size and one-step creation time"
echo "size=$(wc -c < a/lib/app.aot) bytes"; s=$(date +%s%N); (cd a && $JAVA -XX:AOTCacheOutput=x.aot -cp lib/app.jar App >/dev/null 2>&1); e=$(date +%s%N); echo "create ms=$(( (e-s)/1000000 ))"
