package io.github.youndie.zavarnik

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class JibImageTest {
    @Test
    fun `the stand's docker arguments go between --rm and the user, before the image`() {
        val command =
            JibImage.runnerCommand(
                image = "app:latest",
                appRoot = "/app",
                out = File("/tmp/out"),
                command = "train",
                dockerRunArgs =
                    listOf(
                        "--network",
                        "stand_default",
                        "-e",
                        "DB_URL=jdbc:postgresql://postgres:5432/app",
                    ),
                hostUser = "1000:1000",
            )
        assertEquals(
            listOf(
                "run",
                "--rm",
                "--network",
                "stand_default",
                "-e",
                "DB_URL=jdbc:postgresql://postgres:5432/app",
                "--user",
                "1000:1000",
                "-v",
                "/tmp/out:/zavarnik-out",
                "--entrypoint",
                "java",
                "app:latest",
                "-cp",
                "/app/zavarnik/zavarnik-runner.jar",
                "io.github.youndie.zavarnik.runner.Main",
                "train",
                "/app",
                "--out",
                "/zavarnik-out",
            ),
            command,
        )
    }
}
