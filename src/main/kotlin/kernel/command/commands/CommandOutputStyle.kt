package kernel.command.commands

internal object CommandOutputStyle {
    fun info(message: String): String {
        return "${AnsiColor.BLUE}[INFO]${AnsiColor.RESET} $message"
    }

    fun error(message: String): String {
        return "${AnsiColor.RED}[ERROR]${AnsiColor.RESET} $message"
    }

    fun throwableChain(error: Throwable): String {
        return generateSequence(error) { current -> current.cause }
            .mapNotNull { current ->
                current.message?.trim()?.takeIf(String::isNotEmpty)
            }
            .distinct()
            .joinToString(" -> ")
            .ifBlank { error::class.simpleName ?: "Error desconocido" }
    }

    private object AnsiColor {
        const val RESET: String = "\u001B[0m"
        const val BLUE: String = "\u001B[1;34m"
        const val RED: String = "\u001B[1;31m"
    }
}
