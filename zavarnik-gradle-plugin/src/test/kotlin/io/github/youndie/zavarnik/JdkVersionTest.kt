package io.github.youndie.zavarnik

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdkVersionTest {
    @Test
    fun `parses what java -version reports`() {
        assertEquals(JdkVersion(25, 0, 2), JdkVersion.parse("25.0.2+10-69"))
        assertEquals(JdkVersion(25, 0, 4), JdkVersion.parse("25.0.4+7-1-24.04-Ubuntu"))
        assertEquals(JdkVersion(21, 0, 5), JdkVersion.parse("21.0.5+11-LTS"))
        assertEquals(JdkVersion(26, 0, 0), JdkVersion.parse("26"))
        assertEquals(JdkVersion(27, 0, 0), JdkVersion.parse("27-ea"))
        assertNull(JdkVersion.parse("unknown"))
    }

    @Test
    fun `JDK-8377932 is carried by 25 before 25_0_4 and 26 before 26_0_2`() {
        assertTrue(JdkVersion(25, 0, 0).skipsJarValidation)
        assertTrue(JdkVersion(25, 0, 3).skipsJarValidation)
        assertFalse(JdkVersion(25, 0, 4).skipsJarValidation)
        assertTrue(JdkVersion(26, 0, 1).skipsJarValidation)
        assertFalse(JdkVersion(26, 0, 2).skipsJarValidation)
        assertFalse(JdkVersion(27, 0, 0).skipsJarValidation)
        assertFalse(JdkVersion(24, 0, 2).skipsJarValidation)
    }

    @Test
    fun `feature gates follow the JEPs`() {
        assertFalse(JdkVersion(24, 0, 2).supportsOneStepWorkflow)
        assertTrue(JdkVersion(25, 0, 0).supportsOneStepWorkflow)
        assertFalse(JdkVersion(25, 0, 4).supportsZgcWithAotCache)
        assertTrue(JdkVersion(26, 0, 0).supportsZgcWithAotCache)
    }
}
