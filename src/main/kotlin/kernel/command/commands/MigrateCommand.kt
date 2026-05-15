package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.database.migrations.MigrationRunOptions

/**
 * Ejecuta las migraciones pendientes registradas por la aplicacion.
 */
class MigrateCommand(
    private val runner: (MigrationRunOptions) -> List<String>
) : Command {
    override val name: String = "migrate"
    override val description: String =
        "Ejecuta las migraciones pendientes. Ejemplo: ./kernel migrate --database=main"
    override val usage: String = "migrate [--database=name] [--only=name]"

    override fun execute(input: CommandInput): CommandResult {
        return try {
            val options = MigrationRunOptions(
                database = input.option("database")?.trim()?.takeIf(String::isNotEmpty),
                only = parseMigrationOnlyOption(input.option("only"))
            )

            val executed = runner(options)
            if (executed.isEmpty()) {
                return CommandResult(
                    exitCode = 0,
                    message = CommandOutputStyle.info("No hay migraciones pendientes.")
                )
            }

            CommandResult(
                exitCode = 0,
                message = buildSuccessOutput(executed)
            )
        } catch (error: Throwable) {
            CommandResult(
                exitCode = 1,
                message = CommandOutputStyle.error(
                    "Fallo al ejecutar migraciones: ${CommandOutputStyle.throwableChain(error)}"
                )
            )
        }
    }

    private fun buildSuccessOutput(executed: List<String>): String {
        val leftWidth = maxOf(
            MIN_LEFT_WIDTH,
            HEADER_LABEL.length + 2,
            executed.maxOf { it.length + 2 }
        )

        return buildString {
            appendLine(dottedRow(HEADER_LABEL, RESULT_HEADER, leftWidth))
            executed.forEach { migrationName ->
                appendLine(
                    dottedRow(
                        migrationName,
                        "${AnsiColor.YELLOW}Ran${AnsiColor.RESET}",
                        leftWidth
                    )
                )
            }
        }.trimEnd()
    }

    private fun dottedRow(left: String, right: String, leftWidth: Int): String {
        val dotCount = maxOf(2, leftWidth - left.length)
        return buildString {
            append(left)
            repeat(dotCount) {
                append('.')
            }
            append(' ')
            append(right)
        }
    }

    private object AnsiColor {
        const val RESET: String = "\u001B[0m"
        const val YELLOW: String = "\u001B[1;33m"
    }

    companion object {
        private const val HEADER_LABEL: String = "Migration name"
        private const val RESULT_HEADER: String = "Result"
        private const val MIN_LEFT_WIDTH: Int = 72
    }
}
