package io.github.youndie.zavarnik

/**
 * A JDK runtime version, `25.0.2+10-69` → `25.0.2`, as far as the plugin has to reason about it.
 *
 * Two questions are asked of it: is the one-step AOT workflow there (JDK 25, JEP 514), and does
 * this exact build validate the application jars against the cache. The second is
 * [JDK-8377932](https://bugs.openjdk.org/browse/JDK-8377932): on 25.0.0–25.0.3 and 26.0.0–26.0.1 a
 * cache produced with `-XX:AOTCacheOutput` never checks the app classpath, so a stale cache is
 * used silently, with exit code 0 even under `-XX:AOTMode=on`. Fixed in 25.0.4 and 26.0.2.
 */
public data class JdkVersion(
    val feature: Int,
    val interim: Int,
    val update: Int,
) : Comparable<JdkVersion> {
    /** `true` when the JVM does not validate application jars against an AOT cache (JDK-8377932). */
    public val skipsJarValidation: Boolean
        get() =
            when (feature) {
                25 -> this < JdkVersion(25, 0, 4)
                26 -> this < JdkVersion(26, 0, 2)
                else -> false
            }

    /** `true` from JDK 26 on: JEP 516 lets the cache be used under any collector, ZGC included. */
    public val supportsZgcWithAotCache: Boolean
        get() = feature >= 26

    /** `true` from JDK 25 on: the one-step `-XX:AOTCacheOutput` workflow (JEP 514). */
    public val supportsOneStepWorkflow: Boolean
        get() = feature >= 25

    override fun compareTo(other: JdkVersion): Int =
        compareValuesBy(this, other, JdkVersion::feature, JdkVersion::interim, JdkVersion::update)

    override fun toString(): String = "$feature.$interim.$update"

    public companion object {
        /**
         * Parses what `java -version` and `JavaInstallationMetadata.javaRuntimeVersion` report:
         * `25`, `25.0.2`, `25.0.2+10-69`, `25-ea`, `21.0.5+11-LTS`. Returns `null` for anything
         * that does not start with a number.
         */
        public fun parse(runtimeVersion: String): JdkVersion? {
            val numbers =
                runtimeVersion
                    .takeWhile { it.isDigit() || it == '.' }
                    .split('.')
                    .filter { it.isNotEmpty() }
            val feature = numbers.getOrNull(0)?.toIntOrNull() ?: return null
            return JdkVersion(
                feature = feature,
                interim = numbers.getOrNull(1)?.toIntOrNull() ?: 0,
                update = numbers.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }
    }
}
