package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult

data class MigrateFreshResult(
    val rolledBack: List<String>,
    val migrated: List<String>,
    val seeded: List<String> = emptyList()
)

class MigrateFreshCommand(
    private val runner: (database: String?, shouldSeed: Boolean, seederClass: String?) -> MigrateFreshResult
) : Command {
    override val name: String = "migrate:fresh"

    override fun execute(input: CommandInput): CommandResult {
        return try {
            val database = input.option("database")?.trim()?.takeIf(String::isNotEmpty)
            val shouldSeed = input.hasOption("seed") && input.option("seed") == "true"
            val seederClass = input.option("seeder")?.trim()?.takeIf(String::isNotEmpty)
                ?: input.option("class")?.trim()?.takeIf(String::isNotEmpty)

            val result = runner(database, shouldSeed, seederClass)
            CommandResult(
                exitCode = 0,
                message = buildOutput(result)
            )
        } catch (error: Throwable) {
            CommandResult(
                exitCode = 1,
                message = CommandOutputStyle.error(
                    "Fallo al ejecutar migrate:fresh: ${CommandOutputStyle.throwableChain(error)}"
                )
            )
        }
    }

    private fun buildOutput(result: MigrateFreshResult): String {
        return buildString {
            appendLine("migrate:fresh completado.")
            appendLine("Rollback: ${result.rolledBack.size} migracion(es).")
            appendLine("Migradas: ${result.migrated.size} migracion(es).")
            if (result.seeded.isNotEmpty()) {
                append("Seeders: ${result.seeded.joinToString(", ")}")
            }
        }.trimEnd()
    }
}
