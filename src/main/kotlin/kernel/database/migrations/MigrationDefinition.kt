package kernel.database.migrations

import kotlin.reflect.KClass

/**
 * Registro declarativo de una migracion conocida por la aplicacion.
 *
 * Mantiene el nombre estable de la migracion y una factory para construir una
 * instancia fresca cuando el migrator la necesite.
 */
class MigrationDefinition(
    val name: String,
    val type: KClass<out Migration>,
    private val creator: () -> Migration
) {
    fun create(): Migration {
        return creator()
    }

    operator fun invoke(): Migration {
        return create()
    }
}

inline fun <reified T : Migration> migrationFactory(
    noinline creator: () -> T
): MigrationDefinition {
    val name = T::class.simpleName
        ?: throw IllegalArgumentException(
            "No se pudo resolver el nombre simple de la migracion `${T::class}`."
        )

    return MigrationDefinition(
        name = name,
        type = T::class,
        creator = creator
    )
}
