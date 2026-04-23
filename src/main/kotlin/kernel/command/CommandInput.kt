package kernel.command

/**
 * Representa el comando solicitado, sus argumentos posicionales y opciones.
 */
data class CommandInput(
    val name: String,
    val arguments: List<String>,
    val options: Map<String, String>,
    val workingDirectory: java.nio.file.Path
) {
    fun argument(index: Int): String? = arguments.getOrNull(index)

    fun option(name: String): String? = options[name]

    fun hasOption(name: String): Boolean = options.containsKey(name)
}
