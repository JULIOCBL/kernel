package kernel.command

import kernel.command.commands.MakeMigrationCommand
import kernel.command.commands.SecureStatusCommand
import kernel.debug.DumpAndDieSignal
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Punto de entrada de la CLI del kernel.
 */
object KernelCli {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val result = bootAndRun(args, Paths.get("").toAbsolutePath().normalize())

            if (result.message.isNotBlank()) {
                println(result.message)
            }

            exitProcess(result.exitCode)
        } catch (_: DumpAndDieSignal) {
            return
        }
    }

    internal fun bootAndRun(
        args: Array<String>,
        workingDirectory: java.nio.file.Path,
        registryBuilder: () -> CommandRegistry = {
            CommandRegistry(
                listOf(
                    MakeMigrationCommand(),
                    SecureStatusCommand()
                )
            )
        }
    ): CommandResult {
        return try {
            val registry = registryBuilder()
            run(args, registry, workingDirectory)
        } catch (_: DumpAndDieSignal) {
            CommandResult(exitCode = 0, message = "")
        } catch (error: IllegalArgumentException) {
            CommandResult(exitCode = 1, message = error.message ?: "Comando invalido.")
        }
    }

    internal fun run(
        args: Array<String>,
        registry: CommandRegistry,
        workingDirectory: java.nio.file.Path
    ): CommandResult {
        if (args.isEmpty() || args.first() in setOf("--help", "-h")) {
            return CommandResult(
                exitCode = 0,
                message = CliHelpFormatter.renderGlobalHelp(registry, "./kernel")
            )
        }

        if (args.first() == "help") {
            val target = args.getOrNull(1)
            return if (target.isNullOrBlank()) {
                CommandResult(
                    exitCode = 0,
                    message = CliHelpFormatter.renderGlobalHelp(registry, "./kernel")
                )
            } else {
                val command = registry.find(target)
                    ?: return CommandResult(
                        exitCode = 1,
                        message = buildUnknownCommandMessage(target, registry)
                    )

                CommandResult(
                    exitCode = 0,
                    message = CliHelpFormatter.renderCommandHelp(command, "./kernel")
                )
            }
        }

        return try {
            val input = CommandParser().parse(args, workingDirectory)
            if (input.hasOption("help")) {
                val command = registry.find(input.name)
                    ?: return CommandResult(
                        exitCode = 1,
                        message = buildUnknownCommandMessage(input.name, registry)
                    )

                return CommandResult(
                    exitCode = 0,
                    message = CliHelpFormatter.renderCommandHelp(command, "./kernel")
                )
            }

            val command = registry.find(input.name)
                ?: return CommandResult(
                    exitCode = 1,
                    message = buildUnknownCommandMessage(input.name, registry)
                )

            command.execute(input)
        } catch (_: DumpAndDieSignal) {
            CommandResult(exitCode = 0, message = "")
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
