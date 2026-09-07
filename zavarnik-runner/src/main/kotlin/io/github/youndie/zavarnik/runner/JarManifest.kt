package io.github.youndie.zavarnik.runner

import java.io.File
import java.security.MessageDigest

/**
 * `app.aot.jars`: the SHA-256 of every jar the cache was trained against, one per line,
 * `<hex>  <path>`, the path relative to the installation (`lib/x.jar`, `libs/x.jar`), sorted.
 *
 * The JVM's own check is mtime plus size, and on the JDK builds that carry JDK-8377932 there is
 * no check at all — a rebuilt jar of the same size passes the first and everything passes the
 * second. The manifest is what `aotVerify` compares, on every JDK.
 */
public object JarManifest {
    /** Hashes every `*.jar` directly inside each of [jarDirs] and writes the manifest to [target]. */
    public fun write(
        root: File,
        jarDirs: List<File>,
        target: File,
    ) {
        target.writeText(entries(root, jarDirs).joinToString("") { (name, hash) -> "$hash  $name\n" })
    }

    /**
     * Names the jars whose hash differs from [manifest], plus the ones present on one side only.
     * Empty when the [jarDirs] hold exactly what the manifest describes.
     */
    public fun differences(
        root: File,
        jarDirs: List<File>,
        manifest: File,
    ): List<String> {
        val recorded =
            manifest
                .readLines()
                .filter { it.isNotBlank() }
                .associate { line ->
                    val (hash, name) = line.split("  ", limit = 2)
                    name to hash
                }
        val actual = entries(root, jarDirs).toMap()
        return buildList {
            for ((name, hash) in actual) {
                when (recorded[name]) {
                    null -> add("$name: not in the manifest — added after aotTrain")
                    hash -> Unit
                    else -> add("$name: changed since aotTrain")
                }
            }
            for (name in recorded.keys - actual.keys) add("$name: in the manifest but missing")
        }
    }

    private fun entries(
        root: File,
        jarDirs: List<File>,
    ): List<Pair<String, String>> =
        jarDirs
            .flatMap { dir -> dir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }.orEmpty().toList() }
            .map { jar -> jar.relativeToOrSelf(root).path.replace(File.separatorChar, '/') to sha256(jar) }
            .sortedBy { it.first }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val BUFFER_SIZE = 64 * 1024
}
