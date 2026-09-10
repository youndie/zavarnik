package io.github.youndie.zavarnik.runner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CracPoliciesTest {
    @Test
    fun `a server with no outgoing connections gets the listening rule alone`() {
        val text = CracPolicies.text(emptyList())
        assertTrue(text.contains("listening: true"), text)
        assertTrue(text.contains("action: reopen"), text)
        assertEquals(0, text.split("\n---\n").size - 1, "no separator without a second rule:\n$text")
    }

    @Test
    fun `every ignored port becomes its own rule, in order`() {
        val text = CracPolicies.text(listOf(5432, 9092))
        val rules = text.split("\n---\n")
        assertEquals(3, rules.size, text)
        assertTrue(rules[1].contains("remotePort: 5432") && rules[1].contains("action: ignore"), text)
        assertTrue(rules[2].contains("remotePort: 9092"), text)
    }

    @Test
    fun `the file is written where it was asked for`(
        @TempDir dir: File,
    ) {
        val file = CracPolicies.write(File(dir, "lib/${CracPolicies.FILE_NAME}"), listOf(5432))
        assertTrue(file.isFile)
        assertEquals(CracPolicies.text(listOf(5432)), file.readText())
    }

    @Test
    fun `close is never emitted — it loses a race with the pool that owns the socket`() {
        assertTrue("action: close" !in CracPolicies.text(listOf(5432, 9092)))
    }
}
