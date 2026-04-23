package kernel.command

/**
 * Resultado estandar de un comando CLI.
 */
data class CommandResult(
    val exitCode: Int,
    val message: String
)
