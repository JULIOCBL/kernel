package kernel.command.commands

internal fun parseMigrationOnlyOption(value: String?): Set<String> {
    if (value == null) {
        return emptySet()
    }

    return value.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
        .also { names ->
            require(names.isNotEmpty()) {
                "La opcion `--only` debe indicar al menos una migracion."
            }
        }
}
