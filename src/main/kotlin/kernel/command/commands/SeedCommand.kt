package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult

class SeedCommand(
    private val runner: (String?) -> List<String>
) : Command {
    override val name: String = "db:seed"
    override val description: String =
        "Ejecuta seeders registrados. Ejemplo: ./kernel db:seed --class=LabUserSeeder"
    override val usage: String = "db:seed [--class=ClassName]"

    override fun execute(input: CommandInput): CommandResult {
        return try {
            val seederClass = input.option("class")?.trim()?.takeIf(String::isNotEmpty)
            val executed = runner(seederClass)

            if (executed.isEmpty()) {
                return CommandResult(
                    exitCode = 0,
                    message = CommandOutputStyle.info("No se ejecutó ningun seeder.")
                )
            }

            CommandResult(
                exitCode = 0,
                message = executed.joinToString(
                    separator = "\n",
                    prefix = "Seeders ejecutados:\n"
                ) { seeder -> "- $seeder" }
            )
        } catch (error: Throwable) {
            CommandResult(
                exitCode = 1,
                message = CommandOutputStyle.error(
                    "Fallo al ejecutar seeders: ${CommandOutputStyle.throwableChain(error)}"
                )
            )
        }
    }
}
