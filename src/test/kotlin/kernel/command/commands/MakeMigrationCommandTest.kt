package kernel.command.commands

import kernel.command.CommandInput
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MakeMigrationCommandTest {
    @Test
    fun `creates create table migration from create option`() {
        val workingDirectory = Files.createTempDirectory("kernel-make-migration-test")
        val command = MakeMigrationCommand()

        val result = command.execute(
            CommandInput(
                name = "make:migration",
                arguments = listOf("create_users_table"),
                options = mapOf("create" to "users"),
                workingDirectory = workingDirectory
            )
        )

        val migrationsDir = workingDirectory.resolve("src/main/kotlin/kernel/database/migrations")
        val generatedFile = Files.list(migrationsDir).use { files -> files.findFirst().orElseThrow() }
        val content = generatedFile.readText()

        assertEquals(0, result.exitCode)
        assertTrue(result.message.contains("Migracion creada"))
        assertTrue(content.contains("""create("users") {"""))
        assertTrue(content.contains("""dropIfExists("users")"""))
    }

    @Test
    fun `infers create table migration from migration name`() {
        val workingDirectory = Files.createTempDirectory("kernel-make-migration-infer-test")
        val command = MakeMigrationCommand()

        command.execute(
            CommandInput(
                name = "make:migration",
                arguments = listOf("create_posts_table"),
                options = emptyMap(),
                workingDirectory = workingDirectory
            )
        )

        val migrationsDir = workingDirectory.resolve("src/main/kotlin/kernel/database/migrations")
        val generatedFile = Files.list(migrationsDir).use { files -> files.findFirst().orElseThrow() }
        val content = generatedFile.readText()

        assertTrue(content.contains("""class """))
        assertTrue(content.contains("""create("posts") {"""))
    }
}
