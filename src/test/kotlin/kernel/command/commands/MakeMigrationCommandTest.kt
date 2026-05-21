package kernel.command.commands

import kernel.command.CommandInput
import kernel.foundation.Application
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

    @Test
    fun `uses application config to resolve package and directory`() {
        val workingDirectory = Files.createTempDirectory("kernel-make-migration-config-test")
        val application = Application(basePath = workingDirectory).apply {
            config.set("app.generators.migrations.package", "demo.database.migrations")
            config.set("app.generators.migrations.directory", "src/main/kotlin/demo/database/migrations")
        }
        val command = MakeMigrationCommand(application = application)

        val result = command.execute(
            CommandInput(
                name = "make:migration",
                arguments = listOf("create_audit_logs_table"),
                options = mapOf(
                    "create" to "audit_logs",
                    "database" to "logs"
                ),
                workingDirectory = workingDirectory
            )
        )

        val migrationsDir = workingDirectory.resolve("src/main/kotlin/demo/database/migrations")
        val generatedFile = Files.list(migrationsDir).use { files -> files.findFirst().orElseThrow() }
        val content = generatedFile.readText()

        assertEquals(0, result.exitCode)
        assertTrue(result.message.contains("Conexion fijada en la clase: `logs`"))
        assertTrue(content.contains("package demo.database.migrations"))
        assertTrue(content.contains("override val connectionName: String = \"logs\""))
    }
}
