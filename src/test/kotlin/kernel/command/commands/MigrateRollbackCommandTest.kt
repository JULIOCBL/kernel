package kernel.command.commands

import kernel.command.CommandInput
import kernel.database.migrations.MigrationRollbackOptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrateRollbackCommandTest {
    @Test
    fun `returns chained error message when rollback fails`() {
        val command = MigrateRollbackCommand { _: MigrationRollbackOptions ->
            throw IllegalStateException(
                "No se pudo revertir la migracion",
                IllegalArgumentException("column \"legacy_code\" does not exist")
            )
        }

        val result = command.execute(
            CommandInput(
                name = "migrate:rollback",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-migrate-rollback-command-failure-test")
            )
        )

        assertEquals(1, result.exitCode)
        val plain = result.message.withoutAnsi()
        assertTrue(plain.contains("[ERROR] Fallo al hacer rollback de migraciones:"))
        assertTrue(plain.contains("No se pudo revertir la migracion"))
        assertTrue(plain.contains("column \"legacy_code\" does not exist"))
    }

    private fun String.withoutAnsi(): String {
        return replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
