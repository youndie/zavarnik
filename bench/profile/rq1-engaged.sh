#!/usr/bin/env bash
# Did the RQ1 lever engage at all?
#
# §1.17 measured what raising FreqInlineSize BUYS (1.3 %, inside the ruler) but never checked that
# it CHANGED anything. Without that, a null result is the §1.3 failure again: a check that did not
# find its subject. FreqInlineSize is not the only gate - InlineSmallCode=2500, MaxInlineLevel=15
# and the node budget can each refuse a method the size dial has already let through.
#
# This does not time anything. It runs the same workload under -XX:+PrintInlining and counts the
# refusals, which is the only evidence that says whether the dial was connected.
set -uo pipefail
cd ~/zavarnik-jit/bench
export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
gen() { ssh -o BatchMode=yes -o StrictHostKeyChecking=no 10.0.0.3 "~/tools/oha $*"; }
for size in 325 2000; do
  if ss -ltn | grep -q :18100; then echo "FATAL: port busy"; exit 1; fi
  bash profile/reset-db.sh >/dev/null 2>&1
  JAVA_OPTS="-Dbench.engine=netty -Dbench.data=real -Dbench.repo=jdbc -Dbench.host=10.0.0.2 -Dbench.db.pool=32 -Dkotlinx.coroutines.io.parallelism=16 -Xms1g -Xmx1g -XX:+UseG1GC -XX:FreqInlineSize=$size -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining" \
    build/install/bench/bin/bench > "/tmp/rq1i-$size.log" 2>&1 &
  pid=$!
  for i in $(seq 150); do curl -s -o /dev/null http://10.0.0.2:18100/health 2>/dev/null && break; sleep 0.2; done
  kill -0 "$pid" 2>/dev/null || { echo "FATAL: size=$size died"; tail -3 "/tmp/rq1i-$size.log"; exit 1; }
  gen -z 45s -c 64 -q 2000 --no-tui "http://10.0.0.2:18100/db/items?limit=50" >/dev/null 2>&1
  kill -TERM "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  echo "== FreqInlineSize=$size"
  echo "   log lines:            $(grep -c "" /tmp/rq1i-$size.log)"
  echo "   too big:            $(grep -c "too big" /tmp/rq1i-$size.log)"
  echo "   hot method too big: $(grep -c "hot method too big" /tmp/rq1i-$size.log)"
  echo "   already compiled into a big method: $(grep -c "already compiled into a big method" /tmp/rq1i-$size.log)"
  echo "   inline attempts:      $(grep -cE "@ [0-9]+ " /tmp/rq1i-$size.log)"
  echo "   distinct methods refused as too big:"
  grep "too big" /tmp/rq1i-$size.log | sed -E "s/.*@ [0-9]+ +([^ ]+).*/\1/" | sort -u | wc -l
done
echo DONE
