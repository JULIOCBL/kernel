package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.database.migrations.MigrationRollbackOptions

/**
 * Revierte migraciones ya ejecutadas.
 */
class MigrateRollbackCommand(
    private val runner: (MigrationRollbackOptions) -> List<String>
) : Command {
    override val name: String = "migrate:rollback"
    override val description: String =
        "Revierte migraciones ya ejecutadas. Ejemplo: ./kernel migrate:rollback --step=1"
    override val usage: String = "migrate:rollback [--database=name] [--step=N]"

    override fun execute(input: CommandInput): CommandResult {
        return try {
            val options = MigrationRollbackOptions(
                database = input.option("database")?.trim()?.takeIf(String::isNotEmpty),
                steps = parseStep(input.option("step"))
            )

            val rolledBack = runner(options)
            if (rolledBack.isEmpty()) {
                return CommandResult(
                    exitCode = 0,
                    message = CommandOutputStyle.info("No hay migraciones para rollback.")
                )
            }

            CommandResult(
                exitCode = 0,
                message = buildSuccessOutput(rolledBack)
            )
        } catch (error: Throwable) {
            CommandResult(
                exitCode = 1,
                message = CommandOutputStyle.error(
                    "Fallo al hacer rollback de migraciones: ${CommandOutputStyle.throwableChain(error)}"
                )
            )
        }
    }

    private fun parseStep(value: String?): Int {
        if (value == null) {
            return 0
        }

        val parsed = value.trim().toIntOrNull()
            ?: throw IllegalArgumentException("La opcion `--step` debe ser un numero entero positivo.")

        require(parsed > 0) {
            "La opcion `--step` debe ser mayor a cero."
        }

        return parsed
    }

    private fun buildSuccessOutput(rolledBack: List<String>): String {
        val leftWidth = maxOf(
            MIN_LEFT_WIDTH,
            HEADER_LABEL.length + 2,
            rolledBack.maxOf { it.length + 2 }
        )

        return buildString {
            appendLine(dottedRow(HEADER_LABEL, RESULT_HEADER, leftWidth))
            rolledBack.forEach { migrationName ->
                appendLine(
                    dottedRow(
                        migrationName,
                        "${AnsiColor.YELLOW}Rolled back${AnsiColor.RESET}",
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
