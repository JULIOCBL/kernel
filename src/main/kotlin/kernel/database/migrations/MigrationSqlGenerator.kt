package kernel.database.migrations

import kernel.database.migrations.postgresql.PostgreSqlMigrationSqlGenerator

/**
 * Fachada por defecto para generar SQL PostgreSQL.
 */
class MigrationSqlGenerator(
    private val postgreSql: PostgreSqlMigrationSqlGenerator = PostgreSqlMigrationSqlGenerator()
) {
    /**
     * Genera el SQL completo de `up` separado por lineas en blanco.
     */
    fun generateUp(migration: Migration): String {
        return postgreSql.generateUp(migration)
    }

    /**
     * Genera el SQL completo de `down` separado por lineas en blanco.
     */
    fun generateDown(migration: Migration): String {
        return postgreSql.generateDown(migration)
    }

    /**
     * Genera las sentencias SQL de `up` conservando el orden de declaracion.
     */
    fun generateUpStatements(migration: Migration): List<String> {
        return postgreSql.generateUpStatements(migration)
    }

    /**
     * Genera las sentencias SQL de `down` conservando el orden de declaracion.
     */
    fun generateDownStatements(migration: Migration): List<String> {
        return postgreSql.generateDownStatements(migration)
    }
}
