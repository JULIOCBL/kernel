package kernel.command.commands

import kernel.command.CommandInput
import kernel.database.migrations.MigrationStatusOptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrateStatusCommandTest {
    @Test
    fun `returns chained error message when status lookup fails`() {
        val command = MigrateStatusCommand { _: MigrationStatusOptions ->
            throw IllegalStateException(
                "No se pudo consultar migrations",
                IllegalArgumentException("password authentication failed for user \"root\"")
            )
        }

        val result = command.execute(
            CommandInput(
                name = "migrate:status",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-migrate-status-command-failure-test")
            )
        )

        assertEquals(1, result.exitCode)
        val plain = result.message.withoutAnsi()
        assertTrue(plain.contains("[ERROR] Fallo al consultar estado de migraciones:"))
        assertTrue(plain.contains("No se pudo consultar migrations"))
        assertTrue(plain.contains("password authentication failed for user \"root\""))
    }

    private fun String.withoutAnsi(): String {
        return replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
