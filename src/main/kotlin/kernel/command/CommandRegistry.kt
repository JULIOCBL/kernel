package kernel.command

/**
 * Registro inmutable de comandos disponibles.
 */
class CommandRegistry(commands: List<Command>) {
    private val commandsByName = commands.associateBy(Command::name)

    fun find(name: String): Command? = commandsByName[name]

    fun availableCommands(): List<String> = commandsByName.keys.sorted()
}
