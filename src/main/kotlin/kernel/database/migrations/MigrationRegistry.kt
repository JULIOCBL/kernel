package kernel.database.migrations

/**
 * Catalogo explicito de migraciones disponibles para una aplicacion.
 *
 * En Kotlin no descubrimos clases de migracion desde archivos `.php`, asi que
 * este registro hace explicita la lista oficial que el migrator puede ejecutar
 * desde terminal o tooling interno.
 */
class MigrationRegistry(
    migrations: Iterable<MigrationDefinition>
) {
    private val definitionsByName: Map<String, MigrationDefinition> = buildMap {
        migrations.forEach { definition ->
            require(!containsKey(definition.name)) {
                "La migracion `${definition.name}` esta registrada mas de una vez."
            }

            put(definition.name, definition)
        }
    }

    fun all(): List<MigrationDefinition> {
        return definitionsByName.values.sortedBy(MigrationDefinition::name)
    }

    fun find(name: String): MigrationDefinition? {
        return definitionsByName[name]
    }

    fun isEmpty(): Boolean = definitionsByName.isEmpty()

    fun contains(name: String): Boolean = definitionsByName.containsKey(name)
}
