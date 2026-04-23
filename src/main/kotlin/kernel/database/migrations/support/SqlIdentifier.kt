package kernel.database.migrations.support

/**
 * Validador centralizado para identificadores PostgreSQL usados por el DSL.
 */
internal object SqlIdentifier {
    private val validIdentifier = Regex("[a-z_][a-z0-9_]*")

    /**
     * Valida un identificador simple en snake_case y dentro del limite PostgreSQL.
     */
    fun requireValid(value: String, label: String): String {
        val identifier = value.trim()

        require(identifier.isNotEmpty()) { "$label no puede estar vacio." }
        require(identifier.length <= 63) {
            "$label '$identifier' supera el limite de 63 caracteres de PostgreSQL."
        }
        require(validIdentifier.matches(identifier)) {
            "$label '$identifier' no es valido para PostgreSQL. Usa snake_case con letras minusculas, numeros y guion bajo; no puede iniciar con numero."
        }

        return identifier
    }

    /**
     * Valida un identificador calificado separando cada segmento por punto.
     */
    fun requireQualified(value: String, label: String): String {
        val parts = value.trim().split('.')

        require(parts.all { part -> part.isNotBlank() }) {
            "$label '$value' no es valido."
        }

        return parts.joinToString(".") { part -> requireValid(part, label) }
    }
}
