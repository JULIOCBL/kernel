package kernel.command

/**
 * Contrato base para comandos ejecutables desde la CLI del kernel.
 */
interface Command {
    val name: String
    val description: String
        get() = ""
    val usage: String
        get() = name

    fun execute(input: CommandInput): CommandResult
}
