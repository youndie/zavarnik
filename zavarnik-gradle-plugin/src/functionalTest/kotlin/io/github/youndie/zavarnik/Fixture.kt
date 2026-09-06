package io.github.youndie.zavarnik

import java.io.File

/**
 * A TestKit project: the `application` plugin, a JDK 25 toolchain, and a main class that serves
 * `/health` on the JDK's own HTTP server and keeps running until it is stopped. What the plugin
 * trains against in every functional test.
 */
internal object Fixture {
    const val PORT = 18765

    fun write(
        dir: File,
        extension: String = "",
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
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
                    server.start();
                    Thread.currentThread().join();
                }
            }
            """.trimIndent(),
        )
    }
}
