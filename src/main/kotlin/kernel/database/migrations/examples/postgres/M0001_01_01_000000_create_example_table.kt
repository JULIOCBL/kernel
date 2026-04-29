package kernel.database.migrations.examples.postgres

import kernel.database.migrations.Migration

/**
 * Migracion de ejemplo que crea y elimina `example_table`.
 */
class M0001_01_01_000000_create_example_table : Migration() {
    /**
     * Crea la tabla de ejemplo con llave primaria UUID, nombre y fecha de creacion.
     */
    override fun up() {
        create("example_table") {
            id().primaryKey()
            string("name", 100).notNull()
            timestamp("created_at").notNull()
        }
    }

    /**
     * Elimina la tabla de ejemplo si existe.
     */
    override fun down() {
        dropIfExists("example_table")
    }
}
