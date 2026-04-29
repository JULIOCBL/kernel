package kernel.database.migrations.examples.postgres

import kernel.database.migrations.Migration

/**
 * Migracion que crea la tabla `users`.
 */
class M2026_04_29_020109_create_users_table : Migration() {
    /**
     * Crea la tabla `users`.
     */
    override fun up() {
        create("users") {
            id().primaryKey()
            timestampsTz()
        }
    }

    /**
     * Elimina la tabla `users` si existe.
     */
    override fun down() {
        dropIfExists("users")
    }
}
