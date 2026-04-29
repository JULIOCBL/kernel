package kernel.database.schema

import kernel.database.support.SqlIdentifier

/**
 * Helpers PostgreSQL para constraints declaradas al crear tablas.
 */
internal class PostgresTableDefinitionDsl(
    private val addConstraint: (TableConstraintDefinition) -> Unit
) {
    fun exclude(
        name: String,
        using: String,
        elements: Array<out String>,
        where: String?
    ) {
        require(elements.isNotEmpty()) {
            "Debes indicar al menos un elemento para EXCLUDE."
        }

        addConstraint(
            ExcludeConstraintDefinition(
                name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                using = SqlIdentifier.requireValid(using, "Metodo de indice"),
                elements = elements.map { element ->
                    TableDslSupport.sqlFragment(element, "Elemento EXCLUDE")
                },
                where = where?.let { expression ->
                    TableDslSupport.sqlFragment(expression, "Expresion WHERE")
                }
            )
        )
    }
}
