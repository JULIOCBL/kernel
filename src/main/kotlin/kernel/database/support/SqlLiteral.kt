package kernel.database.migrations.support

/**
 * Utilidades para convertir valores Kotlin en literales SQL seguros.
 */
internal object SqlLiteral {
    /**
     * Escapa comillas simples y devuelve un literal SQL de texto.
     */
    fun string(value: String): String {
        return "'${value.replace("'", "''")}'"
    }
}
