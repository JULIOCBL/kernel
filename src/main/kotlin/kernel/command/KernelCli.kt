package kernel.command

import kernel.command.commands.MakeMigrationCommand
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Punto de entrada de la CLI del kernel.
 */
object KernelCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val registry = CommandRegistry(
            listOf(
                MakeMigrationCommand()
            )
        )

        val result = run(args, registry, Paths.get("").toAbsolutePath().normalize())

        if (result.message.isNotBlank()) {
            println(result.message)
        }

        exitProcess(result.exitCode)
    }

    internal fun run(
        args: Array<String>,
        registry: CommandRegistry,
        workingDirectory: java.nio.file.Path
    ): CommandResult {
        return try {
            val input = CommandParser().parse(args, workingDirectory)
            val command = registry.find(input.name)
                ?: return CommandResult(
                    exitCode = 1,
                    message = buildUnknownCommandMessage(input.name, registry)
                )

            command.execute(input)
        } catch (error: IllegalArgumentException) {
            CommandResult(exitCode = 1, message = error.message ?: "Comando invalido.")
        }
    }

    private fun buildUnknownCommandMessage(
        name: String,
        registry: CommandRegistry
    ): String {
        val available = registry.availableCommands().joinToString(", ")

        return "Comando desconocido `$name`. Disponibles: $available"
    }
}
