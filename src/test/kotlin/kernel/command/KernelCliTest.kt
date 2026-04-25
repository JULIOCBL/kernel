package kernel.command

import kernel.command.commands.MakeMigrationCommand
import kernel.debug.dd
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

    @Test
    fun `dd stops command without surfacing stack trace`() {
        var printed = ""
        val ddRegistry = CommandRegistry(
            listOf(
                object : Command {
                    override val name: String = "debug:dd"

                    override fun execute(input: CommandInput): CommandResult {
                        dd("kernel-stop") { output ->
                            printed = output
                        }
                    }
                }
            )
        )

        val result = KernelCli.run(
            args = arrayOf("debug:dd"),
            registry = ddRegistry,
            workingDirectory = Paths.get("/tmp/kernel")
        )

        assertEquals(0, result.exitCode)
        assertEquals("", result.message)
        assertTrue(printed.contains("kernel-stop"))
    }

    @Test
    fun `dd during registry bootstrap does not fail the cli`() {
        var printed = ""

        val result = KernelCli.bootAndRun(
            args = arrayOf("make:migration", "create_users_table"),
            workingDirectory = Paths.get("/tmp/kernel"),
            registryBuilder = {
                dd("kernel-bootstrap-stop") { output ->
                    printed = output
                }
            }
        )

        assertEquals(0, result.exitCode)
        assertEquals("", result.message)
        assertTrue(printed.contains("kernel-bootstrap-stop"))
    }
}
