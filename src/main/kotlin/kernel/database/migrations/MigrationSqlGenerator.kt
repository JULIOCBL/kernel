package kernel.database.migrations

import kernel.database.migrations.mariadb.MariaDbMigrationSqlGenerator
import kernel.database.migrations.postgresql.PostgreSqlMigrationSqlGenerator
import kernel.database.pdo.drivers.DatabaseDriver
import kernel.database.pdo.drivers.PostgreSqlDriver

/**
 * Fachada por defecto para generar SQL segun el driver de la conexion.
 *
 * Para conservar compatibilidad hacia atras, cuando no se especifica driver se
 * sigue usando PostgreSQL como dialecto por defecto.
 */
class MigrationSqlGenerator(
    private val postgreSql: PostgreSqlMigrationSqlGenerator = PostgreSqlMigrationSqlGenerator(),
    private val mariaDb: MariaDbMigrationSqlGenerator = MariaDbMigrationSqlGenerator()
) {
    /**
     * Genera el SQL completo de `up` separado por lineas en blanco.
     */
    fun generateUp(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): String {
        return generatorFor(driver).generateUp(migration)
    }

    /**
     * Genera el SQL completo de `down` separado por lineas en blanco.
     */
    fun generateDown(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): String {
        return generatorFor(driver).generateDown(migration)
    }

    /**
     * Genera las sentencias SQL de `up` conservando el orden de declaracion.
     */
    fun generateUpStatements(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): List<String> {
        return generatorFor(driver).generateUpStatements(migration)
    }

    /**
     * Genera las sentencias SQL de `down` conservando el orden de declaracion.
     */
    fun generateDownStatements(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): List<String> {
        return generatorFor(driver).generateDownStatements(migration)
    }

    private fun generatorFor(driver: DatabaseDriver): MigrationDialectGenerator {
        return when (driver.id) {
            "pgsql" -> postgreSql
            "mariadb" -> mariaDb
            else -> throw IllegalArgumentException(
                "No existe un generador de migraciones para el driver `${driver.id}`."
            )
        }
    }
}
