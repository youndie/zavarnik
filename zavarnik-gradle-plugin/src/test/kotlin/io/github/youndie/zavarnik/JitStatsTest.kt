package io.github.youndie.zavarnik

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JitStatsTest {
    /** Verbatim lines from a JDK 25.0.4 `-XX:+CITime` block (experiments/jit-warmup). */
    private val block =
        """
        Accumulated compiler times
        ----------------------------------------------------------
          Total compilation time   :  24.354 s
            Standard compilation   :  22.992 s, Average : 0.005 s
          C1 {speed: 114965.000 bytes/s; standard: 3.773 s, 433002 bytes, 3776 methods; osr: 0.003 s, 1083 bytes, 3 methods; nmethods_size: 10424720 bytes; nmethods_code_size: 7742584 bytes}
          C2 {speed: 26257.415 bytes/s; standard: 30.479 s, 797258 bytes, 1309 methods; osr: 0.116 s, 6105 bytes, 8 methods; nmethods_size: 4310872 bytes; nmethods_code_size: 2619336 bytes}
        """.trimIndent()

    @Test
    fun `reads method counts and times per tier and the total`() {
        assertEquals(JitStats(3776, 3.773, 1309, 30.479, 24.354), JitStats.parse(block))
    }

    @Test
    fun `reads the right-aligned short-run form too`() {
        val short =
            "  C1 {speed: 310404.776 bytes/s; standard:  0.246 s, 75043 bytes, 1057 methods; osr:  0.005 s}\n" +
                "  C2 {speed: 106194.674 bytes/s; standard:  0.750 s, 79856 bytes, 249 methods; osr:  0.014 s}\n" +
                "  Total compilation time   :   1.190 s\n"
        assertEquals(JitStats(1057, 0.246, 249, 0.750, 1.190), JitStats.parse(short))
    }

    @Test
    fun `a log without the block gives null rather than zeros`() {
        assertNull(JitStats.parse("[info][aot] Opened AOT cache lib/app.aot.\n"))
    }
}
