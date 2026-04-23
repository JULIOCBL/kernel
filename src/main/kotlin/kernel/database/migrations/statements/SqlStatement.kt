package kernel.database.migrations.statements

/**
 * Operacion interna capaz de renderizar una sentencia PostgreSQL completa.
 */
internal interface SqlStatement {
    /**
     * Convierte la operacion a SQL final, normalmente con punto y coma.
     */
    fun toSql(): String
}
