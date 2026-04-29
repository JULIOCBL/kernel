package kernel.command.commands

import kernel.command.CommandInput
import kernel.database.migrations.MigrationState
import kernel.database.migrations.MigrationStatus
import kernel.database.migrations.MigrationStatusOptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrateStatusCommandTest {
    @Test
    fun `passes parsed database and only options to status resolver`() {
        var received: MigrationStatusOptions? = null
        val command = MigrateStatusCommand { options ->
            received = options
            listOf(
                MigrationStatus(
                    migration = "M2026_04_23_214243_create_terminal_users_table",
                    status = MigrationState.RAN,
                    batch = 3,
                    connection = "logs"
                )
            )
        }

        val result = command.execute(
            CommandInput(
                name = "migrate:status",
                arguments = emptyList(),
                options = mapOf(
                    "database" to "logs",
                    "only" to "M2026_04_23_214243_create_terminal_users_table"
                ),
                workingDirectory = createTempDirectory("kernel-migrate-status-command-test")
            )
        )

        assertEquals(0, result.exitCode)
        val plain = result.message.withoutAnsi()
        assertTrue(plain.contains("Migration name"))
        assertTrue(plain.contains("Batch / Status"))
        assertTrue(plain.contains("M2026_04_23_214243_create_terminal_users_table"))
        assertTrue(plain.contains("[3] Ran"))
        assertEquals(
            MigrationStatusOptions(
                database = "logs",
                only = setOf("M2026_04_23_214243_create_terminal_users_table")
            ),
            received
        )
    }

    @Test
    fun `returns friendly message when registry is empty`() {
        val command = MigrateStatusCommand { emptyList() }

        val result = command.execute(
            CommandInput(
                name = "migrate:status",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-migrate-status-empty-test")
            )
        )

        assertEquals(0, result.exitCode)
        assertEquals("[INFO] No hay migraciones registradas.", result.message.withoutAnsi())
    }

    @Test
    fun `shows connection column when statuses belong to different connections`() {
        val command = MigrateStatusCommand {
            listOf(
                MigrationStatus(
                    migration = "CreateUsers",
                    status = MigrationState.RAN,
                    batch = 1,
                    connection = "main"
                ),
                MigrationStatus(
                    migration = "CreateAuditLogs",
                    status = MigrationState.PENDING,
                    batch = null,
                    connection = "logs"
                )
            )
        }

        val result = command.execute(
            CommandInput(
                name = "migrate:status",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-migrate-status-multi-connection-test")
            )
        )

        val plain = result.message.withoutAnsi()
        assertTrue(plain.contains("Batch / Status / Connection"))
        assertTrue(plain.contains("[1] Ran @ main"))
        assertTrue(plain.contains("Pending @ logs"))
    }

    private fun String.withoutAnsi(): String {
        return replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
