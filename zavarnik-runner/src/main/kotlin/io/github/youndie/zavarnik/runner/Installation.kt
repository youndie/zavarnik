package io.github.youndie.zavarnik.runner

import java.io.File

/**
 * Where the application is and how it starts — one of two layouts:
 *
 * - **a distribution** (`installDist`, `distTar` unpacked, a Dockerfile's `COPY`): `bin/<script>`
 *   and the jars under `lib/`, with the runner's files in `lib/` beside the jars;
 * - **a Jib image** in `packaged` mode: the project jar under `/app/classpath`, the dependency
 *   jars under `/app/libs`, the classpath in `/app/jib-classpath-file` and the main class in
 *   `/app/jib-main-class-file`, with the runner's files in `/app/zavarnik/`. Recognised by the
 *   classpath file.
 *
 * The cache, the manifest and the logs live in [runnerDir]. [jarDirs] are the directories whose
 * jars the manifest records and the class count is taken over.
 */
public class Installation private constructor(
    public val dir: File,
    public val runnerDir: File,
    public val jarDirs: List<File>,
    public val launch: Launch,
    /** `false` for a Jib image: its mtimes are Jib's constant, and the runner must not touch them. */
    public val pinsJarTimestamps: Boolean,
    /**
     * The JDK this installation is exercised with — the one running the runner. Kept because a
     * CRaC checkpoint is asked for through `jcmd` and a restore is started through `java`, and
     * both live here rather than on the application's own command line.
     */
    public val javaHome: File,
) {
    /** `<runnerDir>/zavarnik.properties`, written by the plugin. */
    public val config: File = File(runnerDir, RunnerConfig.FILE_NAME)

    public fun cache(config: RunnerConfig): File = File(runnerDir, config.cacheFileName)

    public fun manifest(config: RunnerConfig): File = File(runnerDir, config.manifestFileName)

    public companion object {
        /** A distribution at [dir]; [scriptName] names `bin/<script>`, or the only unix script is taken. */
        public fun distribution(
            dir: File,
            javaHome: File,
            scriptName: String? = null,
        ): Installation {
            val bin = File(dir, "bin")
            val script =
                if (scriptName != null) {
                    File(bin, scriptName)
                } else {
                    bin.listFiles { f -> f.isFile && !f.name.endsWith(".bat") }?.singleOrNull()
                        ?: throw RunnerException(
                            "zavarnik: cannot pick the start script in ${bin.path} — is this an installed distribution?",
                        )
                }
            val lib = File(dir, "lib")
            return Installation(
                dir,
                lib,
                listOf(lib),
                Launch.Script(script, javaHome),
                pinsJarTimestamps = true,
                javaHome = javaHome,
            )
        }

        /**
         * A Jib image rooted at [dir] (`/app`). Refuses a classpath with a directory on it — the
         * `exploded` layout — because the JVM writes no cache for one.
         */
        public fun jib(
            dir: File,
            javaHome: File,
            baseJvmArgs: List<String>,
            runnerDir: File = File(dir, JIB_RUNNER_DIR),
        ): Installation {
            val classpathFile = File(dir, JIB_CLASSPATH_FILE)
            val entries =
                classpathFile
                    .readText()
                    .trim()
                    .split(':')
                    .filter { it.isNotEmpty() }
            val jarDirs = LinkedHashSet<File>()
            for (entry in entries) {
                when {
                    entry.endsWith("/*") -> {
                        jarDirs += File(entry.removeSuffix("/*"))
                    }

                    entry.endsWith(".jar") -> {
                        jarDirs += File(entry).parentFile
                    }

                    else -> {
                        throw RunnerException(
                            "zavarnik: the Jib classpath has a directory on it ($entry), and the JVM writes " +
                                "no AOT cache for a classpath that is not jars only. " +
                                "Set `jib { containerizingMode = \"packaged\" }`.",
                        )
                    }
                }
            }
            val mainClass =
                File(dir, JIB_MAIN_CLASS_FILE).takeIf { it.isFile }?.readText()?.trim()
                    ?: throw RunnerException("zavarnik: no $JIB_MAIN_CLASS_FILE in ${dir.path} — not a Jib image?")
            val launch =
                Launch.Command(
                    java = File(javaHome, "bin/java"),
                    baseJvmArgs = baseJvmArgs,
                    classpathArgument = "@${classpathFile.absolutePath}",
                    mainClass = mainClass,
                    directory = dir,
                )
            return Installation(
                dir,
                runnerDir,
                jarDirs.toList(),
                launch,
                pinsJarTimestamps = false,
                javaHome = javaHome,
            )
        }

        /** Distribution or Jib image, by what is at [dir]. */
        public fun detect(
            dir: File,
            javaHome: File,
            baseJvmArgs: List<String>,
        ): Installation =
            if (File(dir, JIB_CLASSPATH_FILE).isFile) jib(dir, javaHome, baseJvmArgs) else distribution(dir, javaHome)

        /** Where a Jib image keeps the runner's files: `/app/zavarnik`. */
        public const val JIB_RUNNER_DIR: String = "zavarnik"
        private const val JIB_CLASSPATH_FILE = "jib-classpath-file"
        private const val JIB_MAIN_CLASS_FILE = "jib-main-class-file"
    }
}
