package kernel.database.schema

/**
 * Agregador del DSL de columnas del kernel.
 *
 * Reune en una sola superficie los tipos comunes y las extensiones
 * vendor-specific, pero manteniendo cada familia en archivos separados para
 * que la arquitectura sea mas legible.
 */
abstract class SchemaColumnBlueprint internal constructor() :
    PortableColumnBlueprintSupport,
    PostgresColumnBlueprintSupport,
    MariaDbColumnBlueprintSupport {

    /**
     * Punto de extension que materializa una columna en el builder concreto.
     */
    abstract override fun addColumn(name: String, type: String): ColumnDefinition
}
