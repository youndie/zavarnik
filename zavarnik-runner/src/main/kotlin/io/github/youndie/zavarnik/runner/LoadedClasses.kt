package io.github.youndie.zavarnik.runner

import java.io.File
import java.util.zip.ZipFile

/**
 * What `-Xlog:class+load=info` says about where the application's classes came from.
 *
 * "Application classes" are the classes inside the jars in `lib/` — enumerated from the jars, not
 * guessed from package names — so the share is *cached application classes / loaded application
 * classes*. Lambda proxies and other hidden classes are not in any jar and stay out of both
 * numbers.
 */
public data class LoadedClasses(
    public val total: Int,
    public val fromCache: Int,
    public val applicationTotal: Int,
    public val applicationFromCache: Int,
) {
    /** `applicationFromCache / applicationTotal`, or `0.0` when nothing of the application was loaded. */
    public val applicationShare: Double
        get() = if (applicationTotal == 0) 0.0 else applicationFromCache.toDouble() / applicationTotal

    public companion object {
        private const val SOURCE_MARKER = " source: "
        private const val CACHE_SOURCE = "shared objects file"
        private val loadLine = Regex("""\[class,load\s*\] (\S+) source: (.+)$""")

        /** Reads a `class+load` log against the classes found in the jars of [jarDirs]. */
        public fun of(
            log: File,
            jarDirs: List<File>,
        ): LoadedClasses {
            val application = classesIn(jarDirs)
            var total = 0
            var fromCache = 0
            var applicationTotal = 0
            var applicationFromCache = 0
            log.forEachLine { line ->
                if (SOURCE_MARKER !in line) return@forEachLine
                val match = loadLine.find(line) ?: return@forEachLine
                val (name, source) = match.destructured
                val cached = source.startsWith(CACHE_SOURCE)
                total++
                if (cached) fromCache++
                if (name in application) {
                    applicationTotal++
                    if (cached) applicationFromCache++
                }
            }
            return LoadedClasses(total, fromCache, applicationTotal, applicationFromCache)
        }

        /** Every class name inside the `*.jar` files of [jarDirs], multi-release entries folded onto their base name. */
        public fun classesIn(jarDirs: List<File>): Set<String> {
            val names = HashSet<String>()
            val jars =
                jarDirs.flatMap { dir ->
                    dir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }.orEmpty().toList()
                }
            for (jar in jars) {
                ZipFile(jar).use { zip ->
                    for (entry in zip.entries()) {
                        val path =
                            entry.name.removePrefix("META-INF/versions/").let {
                                if (it ==
                                    entry.name
                                ) {
                                    it
                                } else {
                                    it.substringAfter('/')
                                }
                            }
                        if (!path.endsWith(".class") || path == "module-info.class" ||
                            path.startsWith("META-INF/")
                        ) {
                            continue
                        }
                        names += path.removeSuffix(".class").replace('/', '.')
                    }
                }
            }
            return names
        }
    }
}
