package kernel.command

import java.nio.file.Path

/**
 * Parser simple para comandos estilo `make:migration nombre --create=users`.
 */
class CommandParser {
    fun parse(args: Array<String>, workingDirectory: Path): CommandInput {
        require(args.isNotEmpty()) { "Debes indicar un comando." }

        val name = args.first()
        val arguments = mutableListOf<String>()
        val options = linkedMapOf<String, String>()

        for (token in args.drop(1)) {
            if (token == "-h") {
                options["help"] = "true"
            } else if (token.startsWith("--")) {
                val option = token.removePrefix("--")
                val separatorIndex = option.indexOf('=')
                if (separatorIndex > 0) {
                    val optionName = option.substring(0, separatorIndex).trim()
                    val optionValue = option.substring(separatorIndex + 1).trim()

                    require(optionName.isNotEmpty()) {
                        "La opcion `$token` no tiene nombre valido."
                    }

                    require(optionValue.isNotEmpty()) {
                        "La opcion `$token` no tiene valor valido."
                    }

                    options[optionName] = optionValue
                } else {
                    val optionName = option.trim()
                    require(optionName.isNotEmpty()) {
                        "La opcion `$token` no tiene nombre valido."
                    }
                    options[optionName] = "true"
                }
            } else {
                arguments += token
            }
        }

        return CommandInput(
            name = name,
            arguments = arguments,
            options = options,
            workingDirectory = workingDirectory
        )
    }
}
