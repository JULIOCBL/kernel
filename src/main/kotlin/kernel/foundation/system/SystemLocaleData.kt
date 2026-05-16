package kernel.foundation.system

/**
 * Contiene la información de configuración regional y de idioma actual del sistema.
 */
data class SystemLocaleData(
    val language: String,
    val country: String,
    val locale: String,
    val displayLanguage: String,
    val displayCountry: String,
    val timezone: String,
    val charset: String
)
