package kernel.command

object CliHelpFormatter {
    fun renderGlobalHelp(
        registry: CommandRegistry,
        executableName: String
    ): String {
        val commands = registry.all()
        val width = maxOf(
            "help".length,
            commands.maxOfOrNull { it.name.length } ?: 0
        )

        return buildString {
            appendLine("Uso: $executableName <comando> [argumentos] [--opciones]")
            appendLine()
            appendLine("Comandos disponibles:")
            appendLine("  ${"help".padEnd(width)}  Muestra ayuda global o de un comando concreto.")
            commands.forEach { command ->
                val description = command.description.ifBlank { "Sin descripcion." }
                appendLine("  ${command.name.padEnd(width)}  $description")
            }
            appendLine()
            appendLine("Ayuda:")
            appendLine("  $executableName --help")
            appendLine("  $executableName help <comando>")
            append("  $executableName <comando> --help")
        }
    }

    fun renderCommandHelp(
        command: Command,
        executableName: String
    ): String {
        val description = command.description.ifBlank { "Sin descripcion." }

        return buildString {
            appendLine("Comando: ${command.name}")
            appendLine("Descripcion: $description")
            appendLine("Uso: $executableName ${command.usage}")
            appendLine()
            append("Ayuda relacionada: $executableName help ${command.name}")
        }
    }
}
