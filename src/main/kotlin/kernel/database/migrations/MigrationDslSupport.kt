package kernel.database.migrations

import kernel.database.support.SqlIdentifier

/**
 * Utilidades compartidas del DSL de migraciones.
 *
 * Se mantienen fuera de `Migration` para separar validacion y normalizacion de
 * la fachada publica del DSL.
 */
internal object MigrationDslSupport {
    fun tableName(name: String): String {
        return SqlIdentifier.requireQualified(name, "Nombre de tabla")
    }

    fun relationName(name: String): String {
        return SqlIdentifier.requireQualified(name, "Nombre de relacion")
    }

    fun schemaName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de schema")
    }

    fun extensionName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de extension")
    }

    fun constraintName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de constraint")
    }

    fun typeName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de tipo")
    }

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

    fun sqlFragment(value: String, label: String): String {
        val fragment = value.trim()

        require(fragment.isNotEmpty()) {
            "$label no puede estar vacio."
        }

        return fragment
    }

    fun enumValues(values: List<String>): List<String> {
        require(values.isNotEmpty()) {
            "Un ENUM debe tener al menos un valor."
        }

        val normalizedValues = values.map(::enumValue)

        require(normalizedValues.distinct().size == normalizedValues.size) {
            "Un ENUM no puede repetir valores."
        }

        return normalizedValues
    }

    fun enumValue(value: String): String {
        val normalizedValue = value.trim()

        require(normalizedValue.isNotEmpty()) {
            "Un valor ENUM no puede estar vacio."
        }

        return normalizedValue
    }

    fun triggerTiming(value: String): String {
        val timing = value.trim().uppercase()

        require(timing in setOf("BEFORE", "AFTER", "INSTEAD OF")) {
            "Timing de trigger no soportado: $value."
        }

        return timing
    }

    fun triggerEvents(values: List<String>): List<String> {
        require(values.isNotEmpty()) {
            "Debes indicar al menos un evento para el trigger."
        }

        return values.map { value ->
            val event = value.trim().uppercase()

            require(event in setOf("INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
                "Evento de trigger no soportado: $value."
            }

            event
        }
    }

    fun triggerForEach(value: String): String {
        val forEach = value.trim().uppercase()

        require(forEach in setOf("ROW", "STATEMENT")) {
            "FOR EACH debe ser ROW o STATEMENT."
        }

        return forEach
    }

    fun functionSignature(value: String): String {
        val signature = value.trim()

        require(signature.isNotEmpty()) {
            "La funcion del trigger no puede estar vacia."
        }

        return signature
    }
}
