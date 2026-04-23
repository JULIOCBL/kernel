package kernel.command

/**
 * Contrato base para comandos ejecutables desde la CLI del kernel.
 */
interface Command {
    val name: String

    fun execute(input: CommandInput): CommandResult
}
