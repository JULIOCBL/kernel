package kernel.command.commands

import kernel.command.CommandInput
import kernel.database.migrations.MigrationRollbackOptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrateRollbackCommandTest {
    @Test
    fun `passes parsed database and step options to runner`() {
        var received: MigrationRollbackOptions? = null
        val command = MigrateRollbackCommand { options ->
            received = options
            listOf("M2026_04_28_121500_create_playground_runtime_probe_two_table")
        }

        val result = command.execute(
            CommandInput(
                name = "migrate:rollback",
                arguments = emptyList(),
                options = mapOf(
                    "database" to "logs",
                    "step" to "1"
                ),
                workingDirectory = createTempDirectory("kernel-migrate-rollback-command-test")
            )
        )

        val plain = result.message.withoutAnsi()
        assertEquals(0, result.exitCode)
        assertTrue(plain.contains("Migration name"))
        assertTrue(plain.contains("Result"))
        assertTrue(plain.contains("Rolled back"))
        assertEquals(
            MigrationRollbackOptions(
                database = "logs",
                steps = 1
            ),
            received
        )
    }

    @Test
    fun `returns friendly message when nothing was rolled back`() {
        val command = MigrateRollbackCommand { emptyList() }

        val result = command.execute(
            CommandInput(
                name = "migrate:rollback",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-migrate-rollback-empty-test")
            )
        )

        assertEquals(0, result.exitCode)
        assertEquals("[INFO] No hay migraciones para rollback.", result.message.withoutAnsi())
    }

    @Test
    fun `fails when step is not a positive integer`() {
        val command = MigrateRollbackCommand { emptyList() }

        val error = assertFailsWith<IllegalArgumentException> {
            command.execute(
                CommandInput(
                    name = "migrate:rollback",
                    arguments = emptyList(),
                    options = mapOf("step" to "0"),
                    workingDirectory = createTempDirectory("kernel-migrate-rollback-invalid-step-test")
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("--step"))
    }

    private fun String.withoutAnsi(): String {
        return replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
