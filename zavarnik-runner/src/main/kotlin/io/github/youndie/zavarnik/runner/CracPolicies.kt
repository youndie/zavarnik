package io.github.youndie.zavarnik.runner

import java.io.File

/**
 * The file-descriptor policy a checkpoint needs, as CRaC's own `jdk.crac.resource-policies` format.
 *
 * A JVM refuses to checkpoint while anything holds an open socket, and a server holds two kinds.
 * The listening socket is `reopen`: CRaC closes it before the checkpoint and binds the same local
 * address again on restore, which is why a Ktor CIO server needs no code of its own. Everything
 * outgoing — a connection pool, a broker client — is `ignore`, and that choice is the one worth
 * explaining, because `close` reads like the careful option and is the broken one: closing a pool's
 * connections makes the pool open replacements while the checkpoint is being taken, and the
 * checkpoint then fails on a socket that did not exist when it started. `ignore` leaves them in the
 * image; on restore each becomes `/dev/null`, the pool's own validation discards them and a client
 * that reconnects, reconnects. What that assumes is stated in [WARNING]: a library that hands out
 * connections without checking them gets a dead one, silently.
 *
 * Measured on konekt — Ktor CIO, HikariCP, Exposed, Postgres and a broker — in
 * `docs/research/research-crac.md` §1.7.
 */
public object CracPolicies {
    /** The file the plugin writes into the distribution and the runner points the JVM at. */
    public const val FILE_NAME: String = "zavarnik-crac-policies.yaml"

    /**
     * What `ignore` cannot promise, in one line the caller can print. Not an exception: the
     * alternative to ignoring is not checkpointing at all.
     */
    public const val WARNING: String =
        "zavarnik: outgoing connections are left to the restore to notice. A pool that validates " +
            "on borrow (HikariCP does) and a client that reconnects will recover; one that does " +
            "neither will use a dead connection. Verify with a request that reaches the database."

    /**
     * The policy text: one `reopen` rule for listening sockets, then one `ignore` rule per port in
     * [ignoredRemotePorts], in the order given. An empty list is legitimate — an application with
     * no outgoing connections needs only the first rule.
     */
    public fun text(ignoredRemotePorts: List<Int>): String {
        val rules =
            mutableListOf(
                """
            |# The server's own socket: closed before the checkpoint, bound again on restore.
            |type: SOCKET
            |listening: true
            |action: reopen
                """.trimMargin(),
            )
        for (port in ignoredRemotePorts) {
            rules +=
                """
                |# Outgoing to $port: left in the image, dead on restore, replaced by whatever owns it.
                |type: SOCKET
                |remotePort: $port
                |action: ignore
                """.trimMargin()
        }
        return rules.joinToString("\n---\n", postfix = "\n")
    }

    /** Writes [text] to [file], creating the directory. Returns the file. */
    public fun write(
        file: File,
        ignoredRemotePorts: List<Int>,
    ): File {
        file.parentFile?.mkdirs()
        file.writeText(text(ignoredRemotePorts))
        return file
    }
}
