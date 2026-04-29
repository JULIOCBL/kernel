package kernel.database.collector

import kernel.database.statements.SqlStatement

/**
 * Acumula las operaciones declaradas por una migracion en el orden exacto.
 */
internal class MigrationCollector {
    private val statements = mutableListOf<SqlStatement>()

    /**
     * Registra una nueva operacion SQL.
     */
    fun add(statement: SqlStatement) {
        statements += statement
    }

    /**
     * Renderiza todas las operaciones acumuladas como sentencias SQL.
     */
    fun toSql(): List<String> {
        return statements.map { statement -> statement.toSql() }
    }
}
