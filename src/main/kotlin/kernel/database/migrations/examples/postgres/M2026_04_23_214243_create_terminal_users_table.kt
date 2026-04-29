package kernel.database.migrations.examples.postgres

import kernel.database.migrations.Migration

/**
 * Migracion que crea la tabla `terminal_users`.
 */
class M2026_04_23_214243_create_terminal_users_table : Migration() {
    /**
     * Crea la tabla `terminal_users`.
     */
    override fun up() {
        create("terminal_users") {
            id().primaryKey()
            timestampsTz()
        }
    }

    /**
     * Elimina la tabla `terminal_users` si existe.
     */
    override fun down() {
        dropIfExists("terminal_users")
    }
}
