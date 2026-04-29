package kernel.database.migrations.examples.postgres

import kernel.database.migrations.Migration

/**
 * Migracion que crea la tabla `posts`.
 */
class M2026_04_23_214311_create_posts_table : Migration() {
    /**
     * Crea la tabla `posts`.
     */
    override fun up() {
        create("posts") {
            id().primaryKey()
            timestampsTz()
        }
    }

    /**
     * Elimina la tabla `posts` si existe.
     */
    override fun down() {
        dropIfExists("posts")
    }
}
