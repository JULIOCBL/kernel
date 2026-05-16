package kernel.foundation

import kernel.http.HttpRequestRuntime
import kernel.session.Session
import java.nio.file.Path

/**
 * Devuelve la aplicacion global del proceso.
 */
fun app(): Application = ApplicationRuntime.current()

/**
 * Resuelve una ruta relativa desde la raiz de la aplicacion global.
 */
fun basePath(relativePath: String = ""): Path = app().path(relativePath)

/**
 * Traduce una clave usando el locale activo del request actual cuando existe,
 * o `app.locale` como fallback del proceso.
 */
fun lang(
    key: String,
    replacements: Map<String, Any?> = emptyMap(),
    locale: String? = null,
    default: String? = null
): String {
    val application = app()
    val effectiveLocale = locale
        ?: currentRequestLocale()
        ?: currentSessionLocale()
        ?: application.config.string("app.locale", "en")
    val fallbackLocale = application.config.string("app.fallback_locale", "en")

    return application.lang.translate(
        key = key,
        locale = effectiveLocale,
        replacements = replacements,
        fallbackLocale = fallbackLocale,
        default = default
    )
}

fun trans(
    key: String,
    replacements: Map<String, Any?> = emptyMap(),
    locale: String? = null,
    default: String? = null
): String {
    return lang(key, replacements, locale, default)
}

fun `__`(
    key: String,
    replacements: Map<String, Any?> = emptyMap(),
    locale: String? = null,
    default: String? = null
): String {
    return lang(key, replacements, locale, default)
}

private fun currentRequestLocale(): String? {
    return runCatching { HttpRequestRuntime.current().locale() }.getOrNull()
}

private fun currentSessionLocale(): String? {
    return runCatching { Session.get<String>("locale") }.getOrNull()
}
