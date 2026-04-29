package kernel.database.grammars

import kernel.database.migrations.Migration
import kernel.database.pdo.drivers.DatabaseDriver
import kernel.database.pdo.drivers.PostgreSqlDriver

/**
 * Fachada por defecto para generar SQL segun el driver de la conexion activa.
 *
 * Conserva la misma API del generador previo, pero deja claro que esta clase
 * solo selecciona la gramatica correcta y delega la traduccion real.
 */
class SchemaSqlGenerator(
    private val postgres: PostgresGrammar = PostgresGrammar(),
    private val mariaDb: MariaDbGrammar = MariaDbGrammar()
) {
    fun generateUp(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): String {
        return grammarFor(driver).generateUp(migration)
    }

    fun generateDown(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): String {
        return grammarFor(driver).generateDown(migration)
    }

    fun generateUpStatements(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): List<String> {
        return grammarFor(driver).generateUpStatements(migration)
    }

    fun generateDownStatements(
        migration: Migration,
        driver: DatabaseDriver = PostgreSqlDriver
    ): List<String> {
        return grammarFor(driver).generateDownStatements(migration)
    }

    private fun grammarFor(driver: DatabaseDriver): SchemaGrammar {
        return when (driver.id) {
            "pgsql" -> postgres
            "mariadb" -> mariaDb
            else -> throw IllegalArgumentException(
                "No existe una gramatica de schema para el driver `${driver.id}`."
            )
        }
    }
}
