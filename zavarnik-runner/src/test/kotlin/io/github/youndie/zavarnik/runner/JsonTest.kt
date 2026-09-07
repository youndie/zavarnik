package io.github.youndie.zavarnik.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {
    private val body =
        """{"accessToken": "t-1\n2", "user": {"id": 7, "score": 1.5, "roles": ["admin", "ops"]}, "ok": true, "none": null}"""

    @Test
    fun `extracts strings, numbers, booleans and null by dot path, arrays by index`() {
        val json = Json.parse(body)
        assertEquals("t-1\n2", Json.extract(json, "accessToken"))
        assertEquals("7", Json.extract(json, "user.id"))
        assertEquals("1.5", Json.extract(json, "user.score"))
        assertEquals("ops", Json.extract(json, "user.roles.1"))
        assertEquals("true", Json.extract(json, "ok"))
        assertEquals("null", Json.extract(json, "none"))
    }

    @Test
    fun `a missing segment names itself and the keys that exist`() {
        val failure = assertFailsWith<RunnerException> { Json.extract(Json.parse(body), "user.token") }
        assertEquals(true, failure.message?.contains("no `token`"))
        assertEquals(true, failure.message?.contains("[id, score, roles]"))
    }

    @Test
    fun `an object is not a scalar and says so`() {
        assertFailsWith<RunnerException> { Json.extract(Json.parse(body), "user") }
    }

    @Test
    fun `a non-JSON body is refused rather than read as text`() {
        assertFailsWith<RunnerException> { Json.parse("welcome") }
        assertFailsWith<RunnerException> { Json.parse("""{"a": 1} trailing""") }
    }
}
