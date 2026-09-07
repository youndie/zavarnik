package io.github.youndie.zavarnik

import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import javax.tools.ToolProvider

/**
 * A TestKit project: the `application` plugin, a JDK 25 toolchain, and a main class that serves
 * `/health` on the JDK's own HTTP server and keeps running until it is stopped. What the plugin
 * trains against in every functional test.
 */
internal object Fixture {
    const val PORT = 18765

    /** A do-nothing `-javaagent` jar, compiled with the JDK running the tests. */
    fun agentJar(dir: File): File {
        val src = File(dir, "agent/Agent.java")
        src.parentFile.mkdirs()
        src.writeText("public class Agent { public static void premain(String args) { } }\n")
        val compiler = ToolProvider.getSystemJavaCompiler()
        check(
            compiler.run(null, null, null, "-d", src.parentFile.absolutePath, src.absolutePath) == 0,
        ) { "javac failed" }
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name("Premain-Class")] = "Agent"
        val jar = File(dir, "agent/agent.jar")
        JarOutputStream(jar.outputStream(), manifest).use { out ->
            out.putNextEntry(ZipEntry("Agent.class"))
            out.write(File(src.parentFile, "Agent.class").readBytes())
            out.closeEntry()
        }
        return jar
    }

    fun write(
        dir: File,
        extension: String = "",
        dependencies: String = "",
    ) {
        File(dir, "settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        File(dir, "build.gradle.kts").writeText(
            """
            import java.time.Duration

            plugins {
                application
                id("io.github.youndie.zavarnik")
            }
            java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
            application { mainClass = "fixture.App" }
            $dependencies
            $extension
            """.trimIndent(),
        )
        val source = File(dir, "src/main/java/fixture/App.java")
        source.parentFile.mkdirs()
        source.writeText(
            """
            package fixture;

            import com.sun.net.httpserver.HttpServer;
            import java.net.InetSocketAddress;
            import java.util.HashMap;
            import java.util.Map;

            public class App {
                public static void main(String[] args) throws Exception {
                    HttpServer server = HttpServer.create(new InetSocketAddress($PORT), 0);
                    server.createContext("/health", exchange -> {
                        byte[] body = "ok".getBytes();
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
                    server.createContext("/work", exchange -> {
                        Map<String, Integer> map = new HashMap<>();
                        for (int i = 0; i < 1000; i++) map.put("k" + i, i);
                        byte[] body = String.valueOf(map.size()).getBytes();
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
                    server.createContext("/login", exchange -> {
                        byte[] body = "{\"accessToken\": \"t-123\", \"user\": {\"id\": 7, \"roles\": [\"admin\"]}}".getBytes();
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
                    server.createContext("/private", exchange -> {
                        String auth = exchange.getRequestHeaders().getFirst("Authorization");
                        String user = exchange.getRequestHeaders().getFirst("X-User");
                        boolean ok = "Bearer t-123".equals(auth) && "7".equals(user);
                        byte[] body = (ok ? "welcome" : "no").getBytes();
                        exchange.sendResponseHeaders(ok ? 200 : 401, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
                    server.start();
                    Thread.currentThread().join();
                }
            }
            """.trimIndent(),
        )
    }
}
