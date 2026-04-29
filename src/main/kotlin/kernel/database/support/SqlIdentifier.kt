package kernel.database.support

/**
 * Validador centralizado para identificadores SQL usados por el DSL.
 *
 * Mantiene una politica conservadora para que el mismo blueprint siga siendo
 * portable entre los motores soportados hoy por el kernel.
 */
internal object SqlIdentifier {
    private val validIdentifier = Regex("[a-z_][a-z0-9_]*")

    /**
     * Valida un identificador simple en snake_case y dentro del limite actual
     * del DSL.
     */
    fun requireValid(value: String, label: String): String {
        val identifier = value.trim()

        require(identifier.isNotEmpty()) { "$label no puede estar vacio." }
        require(identifier.length <= 63) {
            "$label '$identifier' supera el limite actual de 63 caracteres del DSL."
        }
        require(validIdentifier.matches(identifier)) {
            "$label '$identifier' no es valido. Usa snake_case con letras minusculas, numeros y guion bajo; no puede iniciar con numero."
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
