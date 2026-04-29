package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.database.migrations.MigrationState
import kernel.database.migrations.MigrationStatus
import kernel.database.migrations.MigrationStatusOptions

/**
 * Muestra el estado actual de las migraciones registradas.
 */
class MigrateStatusCommand(
    private val statusResolver: (MigrationStatusOptions) -> List<MigrationStatus>
) : Command {
    override val name: String = "migrate:status"

    override fun execute(input: CommandInput): CommandResult {
        val options = MigrationStatusOptions(
            database = input.option("database")?.trim()?.takeIf(String::isNotEmpty),
            only = parseMigrationOnlyOption(input.option("only"))
        )

        val statuses = statusResolver(options)
        if (statuses.isEmpty()) {
            return CommandResult(
                exitCode = 0,
                message = CommandOutputStyle.info("No hay migraciones registradas.")
            )
        }

        return CommandResult(
            exitCode = 0,
            message = renderStatuses(statuses)
        )
    }

    private fun renderStatuses(statuses: List<MigrationStatus>): String {
        val multipleConnections = statuses.map(MigrationStatus::connection).distinct().size > 1
        val rightHeader = if (multipleConnections) {
            "Batch / Status / Connection"
        } else {
            "Batch / Status"
        }
        val leftWidth = maxOf(
            MIN_LEFT_WIDTH,
            HEADER_LABEL.length + 2,
            statuses.maxOf { it.migration.length + 2 }
        )

        return buildString {
            appendLine(dottedRow(HEADER_LABEL, rightHeader, leftWidth))
            statuses.forEach { status ->
                appendLine(
                    dottedRow(
                        status.migration,
                        buildRightColumn(status, multipleConnections),
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

    private fun buildRightColumn(
        status: MigrationStatus,
        includeConnection: Boolean
    ): String {
        val batchAndStatus = status.batch?.let { batch ->
            "[$batch] ${colorizeStatus(status.status)}"
        } ?: colorizeStatus(status.status)

        return if (includeConnection) {
            "$batchAndStatus @ ${status.connection}"
        } else {
            batchAndStatus
        }
    }

    private fun statusLabel(state: MigrationState): String {
        return when (state) {
            MigrationState.RAN -> "Ran"
            MigrationState.PENDING -> "Pending"
        }
    }

    private fun colorizeStatus(state: MigrationState): String {
        return when (state) {
            MigrationState.RAN -> "${AnsiColor.YELLOW}${statusLabel(state)}${AnsiColor.RESET}"
            MigrationState.PENDING -> "${AnsiColor.GREEN}${statusLabel(state)}${AnsiColor.RESET}"
        }
    }

    private object AnsiColor {
        const val RESET: String = "\u001B[0m"
        const val GREEN: String = "\u001B[0;32m"
        const val YELLOW: String = "\u001B[1;33m"
    }

    companion object {
        private const val HEADER_LABEL: String = "Migration name"
        private const val MIN_LEFT_WIDTH: Int = 72
    }
}
