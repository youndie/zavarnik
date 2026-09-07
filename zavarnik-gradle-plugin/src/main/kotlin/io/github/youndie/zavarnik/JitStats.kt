package io.github.youndie.zavarnik

/**
 * What `-XX:+CITime` prints at JVM exit, reduced to the four numbers the report shows.
 *
 * The block looks like this (JDK 25):
 * ```
 *   C1 {speed: 114965.000 bytes/s; standard:  3.773 s, 433002 bytes, 3776 methods; osr: …}
 *   C2 {speed: 26257.415 bytes/s; standard: 30.479 s, 797258 bytes, 1309 methods; osr: …}
 *   …
 *   Total compilation time   :  24.354 s
 * ```
 * The numbers are right-aligned — `standard:  0.246 s` — so the parser allows any run of spaces.
 * Method counts are what to compare between a cold and a cached run: compile *time* moves with
 * the CPU frequency, counts do not — though both grow with how many requests the window served.
 */
public data class JitStats(
    val c1Methods: Int,
    val c1Seconds: Double,
    val c2Methods: Int,
    val c2Seconds: Double,
    val totalSeconds: Double,
) {
    public companion object {
        private val tier =
            Regex("""(C1|C2) \{speed: [^;]*; standard:\s+([0-9.]+) s, [0-9]+ bytes, ([0-9]+) methods""")
        private val total = Regex("""Total compilation time\s*:\s*([0-9.]+) s""")

        /** The stats, or `null` when the text has no `CITime` block — a JVM that was killed prints none. */
        public fun parse(text: String): JitStats? {
            val tiers =
                tier.findAll(text).associate {
                    it.groupValues[1] to
                        (it.groupValues[3].toInt() to it.groupValues[2].toDouble())
                }
            val c1 = tiers["C1"] ?: return null
            val c2 = tiers["C2"] ?: return null
            val all =
                total
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.toDouble() ?: return null
            return JitStats(c1.first, c1.second, c2.first, c2.second, all)
        }
    }
}
