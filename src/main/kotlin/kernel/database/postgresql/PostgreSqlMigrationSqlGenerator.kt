package kernel.database.migrations.postgresql

import kernel.database.migrations.Migration
import kernel.database.migrations.MigrationDialectGenerator

/**
 * Genera SQL compatible con PostgreSQL desde una migracion.
 */
class PostgreSqlMigrationSqlGenerator : MigrationDialectGenerator {
    /**
     * Genera un script SQL para aplicar la migracion.
     */
    override fun generateUp(migration: Migration): String {
        return migration.upSql().joinToString("\n\n")
    }

    /**
     * Genera un script SQL para revertir la migracion.
     */
    override fun generateDown(migration: Migration): String {
        return migration.downSql().joinToString("\n\n")
    }

    /**
     * Genera las sentencias individuales que aplican la migracion.
     */
    override fun generateUpStatements(migration: Migration): List<String> {
        return migration.upSql()
    }

    /**
     * Genera las sentencias individuales que revierten la migracion.
     */
    override fun generateDownStatements(migration: Migration): List<String> {
        return migration.downSql()
    }
}
