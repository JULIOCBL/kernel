package kernel.command

import kernel.command.commands.MakeMigrationCommand
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KernelCliTest {
    private val registry = CommandRegistry(listOf(MakeMigrationCommand()))

    @Test
    fun `returns error for unknown command`() {
        val result = KernelCli.run(
            args = arrayOf("missing:command"),
            registry = registry,
            workingDirectory = Paths.get("/tmp/kernel")
        )

        assertEquals(1, result.exitCode)
        assertTrue(result.message.contains("Comando desconocido"))
    }
}
