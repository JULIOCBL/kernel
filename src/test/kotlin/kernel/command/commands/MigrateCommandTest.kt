package kernel.command.commands

import kernel.command.CommandInput
import kernel.database.migrations.MigrationRunOptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrateCommandTest {
    @Test
    fun `passes parsed database and only options to runner`() {
        var received: MigrationRunOptions? = null
        val command = MigrateCommand { options ->
            received = options
            listOf("M2026_04_23_214243_create_terminal_users_table")
        }

        val result = command.execute(
            CommandInput(
                name = "migrate",
                arguments = emptyList(),
                options = mapOf(
                    "database" to "logs",
                    "only" to "M2026_04_23_214243_create_terminal_users_table, M2026_04_23_214311_create_posts_table"
                ),
                workingDirectory = createTempDirectory("kernel-migrate-command-test")
            )
        )

        assertEquals(0, result.exitCode)
        val plain = result.message.withoutAnsi()
        assertTrue(plain.contains("Migration name"))
        assertTrue(plain.contains("Result"))
        assertTrue(plain.contains("M2026_04_23_214243_create_terminal_users_table"))
        assertTrue(plain.contains("Ran"))
        assertEquals(
            MigrationRunOptions(
                database = "logs",
                only = setOf(
                    "M2026_04_23_214243_create_terminal_users_table",
                    "M2026_04_23_214311_create_posts_table"
                )
            ),
            received
        )
    }

    @Test
    fun `returns friendly message when nothing was executed`() {
        val command = MigrateCommand { emptyList() }

        val result = command.execute(
            CommandInput(
                name = "migrate",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-migrate-command-empty-test")
            )
        )

        assertEquals(0, result.exitCode)
        assertEquals("[INFO] No hay migraciones pendientes.", result.message.withoutAnsi())
    }

    private fun String.withoutAnsi(): String {
        return replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
