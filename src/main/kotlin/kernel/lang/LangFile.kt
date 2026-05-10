package kernel.lang

/**
 * Contrato para archivos de traduccion definidos como codigo Kotlin.
 *
 * Cada implementacion representa un namespace de idioma materializado al
 * arranque, por ejemplo `validation` o `auth`, dentro de un locale concreto
 * como `es` o `en`.
 */
interface LangFile {
    val locale: String
    val namespace: String

    fun load(): Map<String, Any?>
}
