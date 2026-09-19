import re, subprocess, collections
J = "/usr/lib/jvm/java-25-openjdk-amd64/bin/jfr"
for ep, reqs in (("dbitem", 861840), ("dblist", 535320), ("dbpost", 318240)):
    raw = subprocess.run([J, "print", "--events", "jdk.JavaExceptionThrow", "--stack-depth", "3",
                          "/root/bench-results/rq7/%s.jfr" % ep], capture_output=True, text=True).stdout
    blocks = raw.split("jdk.JavaExceptionThrow {")[1:]
    kinds = collections.Counter(); tops = collections.Counter()
    for b in blocks:
        m = re.search(r"thrownClass = (.+)", b)
        if m: kinds[m.group(1).strip()] += 1
        st = re.search(r"stackTrace = \[\s*\n\s*(.+)", b)
        if st: tops[st.group(1).strip()] += 1
    print("== %s  %d throws over %d requests = %.3f per request" % (ep, len(blocks), reqs, len(blocks)/reqs))
    for k, c in kinds.most_common(4):
        print("   %7d  %s" % (c, k))
    for k, c in tops.most_common(3):
        print("   top frame  %7d  %s" % (c, k))
    print()
