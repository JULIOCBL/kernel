package kernel.database.schema

/**
 * Contrato para constraints de tabla que pueden renderizarse dentro de SQL.
 */
internal interface TableConstraintDefinition {
    /**
     * Convierte la constraint a un fragmento SQL sin punto y coma final.
     */
    fun toSql(): String
}
