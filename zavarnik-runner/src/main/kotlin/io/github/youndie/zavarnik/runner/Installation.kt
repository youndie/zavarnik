package io.github.youndie.zavarnik.runner

import java.io.File

/** An installed distribution — `bin/<script>` and `lib/` — and where the runner's files are in it. */
public class Installation(
    public val dir: File,
    scriptName: String? = null,
) {
    public val lib: File = File(dir, "lib")
    public val bin: File = File(dir, "bin")

    /** The start script: the one named, or the only unix script in `bin/`. */
    public val script: File =
        if (scriptName != null) {
            File(bin, scriptName)
        } else {
            bin.listFiles { f -> f.isFile && !f.name.endsWith(".bat") }?.singleOrNull()
                ?: throw RunnerException(
                    "zavarnik: cannot pick the start script in ${bin.path} — is this an installed distribution?",
                )
        }

    /** `lib/zavarnik.properties`, written by the plugin. */
    public val config: File = File(lib, RunnerConfig.FILE_NAME)

    public fun cache(config: RunnerConfig): File = File(lib, config.cacheFileName)

    public fun manifest(config: RunnerConfig): File = File(lib, config.manifestFileName)
}
