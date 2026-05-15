package kernel.command.commands

internal object CommandOutputStyle {
    fun info(message: String): String {
        return "${AnsiColor.BLUE}[INFO]${AnsiColor.RESET} $message"
    }

    fun error(message: String): String {
        return "${AnsiColor.RED}[ERROR]${AnsiColor.RESET} $message"
    }

    fun throwableChain(error: Throwable): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = error

        while (current != null) {
            val msg = current.message?.trim()?.takeIf { it.isNotEmpty() }
            if (msg != null) {
                // Solo añadimos el mensaje si NO está ya contenido en alguno de los mensajes ya registrados.
                // Esto evita redundancias como "Error: Connection refused" -> "Connection refused".
                if (messages.none { it.contains(msg) }) {
                    messages.add(msg)
                }
            }
            current = current.cause
        }

        return messages.joinToString(" -> ")
            .ifBlank { error::class.simpleName ?: "Error desconocido" }
    }

    private object AnsiColor {
        const val RESET: String = "\u001B[0m"
        const val BLUE: String = "\u001B[1;34m"
        const val RED: String = "\u001B[1;31m"
    }
}
