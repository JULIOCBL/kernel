package kernel.database.schema

import kernel.database.support.SqlIdentifier

/**
 * Utilidades compartidas entre los builders de tablas del DSL.
 */
internal object TableDslSupport {
    fun columnName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de columna")
    }

    fun columnNames(columns: List<String>): List<String> {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna."
        }

        val normalizedColumns = columns.map(::columnName)

        require(normalizedColumns.distinct().size == normalizedColumns.size) {
            "No puedes repetir columnas."
        }

        return normalizedColumns
    }

    fun columnNamesOrEmpty(columns: List<String>): List<String> {
        return if (columns.isEmpty()) {
            emptyList()
        } else {
            columnNames(columns)
        }
    }

    fun primaryKeyColumns(columns: Array<out String>): List<String> {
        require(columns.isNotEmpty()) {
            "La llave primaria debe contener al menos una columna."
        }

        val normalizedColumns = columns.map { column ->
            SqlIdentifier.requireValid(column, "Columna de llave primaria")
        }

        require(normalizedColumns.distinct().size == normalizedColumns.size) {
            "La llave primaria no puede repetir columnas."
        }

        return normalizedColumns
    }

    fun defaultName(table: String, columns: List<String>, suffix: String): String {
        return "${table.substringAfterLast('.')}_${columns.joinToString("_")}_$suffix"
    }

    fun sqlFragment(value: String, label: String): String {
        val fragment = value.trim()

        require(fragment.isNotEmpty()) {
            "$label no puede estar vacio."
        }

        return fragment
    }
}
