package kernel.database.migrations.examples.mariadb

import kernel.database.migrations.Migration

/**
 * Migracion de ejemplo pensada para documentar una ruta MariaDB separada.
 */
class M2026_04_29_120000_create_mariadb_example_table : Migration() {
    override fun up() {
        create("mariadb_example_table") {
            id().primaryKey()
            string("label", 120).notNull()
            timestampsTz()
        }
    }

    override fun down() {
        dropIfExists("mariadb_example_table")
    }
}
