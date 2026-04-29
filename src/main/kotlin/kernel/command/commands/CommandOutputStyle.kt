package kernel.command.commands

internal object CommandOutputStyle {
    fun info(message: String): String {
        return "${AnsiColor.BLUE}[INFO]${AnsiColor.RESET} $message"
    }

    private object AnsiColor {
        const val RESET: String = "\u001B[0m"
        const val BLUE: String = "\u001B[1;34m"
    }
}
