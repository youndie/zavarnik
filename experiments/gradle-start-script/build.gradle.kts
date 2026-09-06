plugins { application }
repositories { mavenCentral() }
dependencies { implementation("com.google.code.gson:gson:2.11.0") }
application { mainClass = "demo.App" }
// The anchor the research names: the only way to reference APP_HOME from DEFAULT_JVM_OPTS is to
// post-process the script (unixStartScript.txt, lines 243-244). The guard adds -XX:AOTCache only
// when the cache file exists, so the same script is the training launcher before the cache exists.
tasks.startScripts {
    doLast {
        val guard = """
            |if [ -f "${'$'}APP_HOME/lib/app.aot" ]; then
            |    DEFAULT_JVM_OPTS="${'$'}DEFAULT_JVM_OPTS \"-XX:AOTCache=${'$'}APP_HOME/lib/app.aot\""
            |fi
            |
            |# Collect all arguments for the java command:""".trimMargin()
        unixScript.writeText(unixScript.readText().replace("# Collect all arguments for the java command:", guard))
    }
}
