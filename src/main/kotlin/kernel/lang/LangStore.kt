package kernel.lang

import kernel.config.ConfigStore

/**
 * Almacen de traducciones en memoria basado en la misma semantica de dot
 * notation que usa `ConfigStore`.
 *
 * Las traducciones se cargan por locale y namespace para mantener el costo de
 * resolucion constante una vez materializadas al arranque.
 */
class LangStore {
    private val lock = Any()
    private val storesByLocale = linkedMapOf<String, ConfigStore>()

    fun load(file: LangFile): LangStore {
        merge(file.locale, file.namespace, file.load())
        return this
    }

    fun load(vararg files: LangFile): LangStore {
        files.forEach(::load)
        return this
    }

    fun merge(locale: String, namespace: String, values: Map<String, Any?>) {
        val normalizedLocale = normalizeLocale(locale)
        synchronized(lock) {
            storesByLocale
                .getOrPut(normalizedLocale) { ConfigStore() }
                .merge(namespace, values)
        }
    }

    fun has(locale: String, key: String): Boolean {
        val candidates = candidateLocales(locale)
        synchronized(lock) {
            return candidates.any { storesByLocale[it]?.has(key) == true }
        }
    }

    fun get(locale: String, key: String, default: Any? = null): Any? {
        val candidates = candidateLocales(locale)
        synchronized(lock) {
            candidates.forEach { candidate ->
                val store = storesByLocale[candidate] ?: return@forEach
                if (store.has(key)) {
                    return store.get(key)
                }
            }
        }

        return default
    }

    fun translate(
        key: String,
        locale: String,
        replacements: Map<String, Any?> = emptyMap(),
        fallbackLocale: String? = null,
        default: String? = null
    ): String {
        val searchLocales = buildList {
            addAll(candidateLocales(locale))
            if (!fallbackLocale.isNullOrBlank()) {
                addAll(candidateLocales(fallbackLocale))
            }
        }.distinct()

        val raw = synchronized(lock) {
            searchLocales.firstNotNullOfOrNull { candidate ->
                val store = storesByLocale[candidate] ?: return@firstNotNullOfOrNull null
                if (store.has(key)) {
                    store.get(key)?.toString()
                } else {
                    null
                }
            }
        }

        return applyReplacements(raw ?: default ?: key, replacements)
    }

    fun locales(): Set<String> = synchronized(lock) {
        storesByLocale.keys.toSet()
    }

    private fun normalizeLocale(locale: String): String {
        return locale.trim()
            .replace('_', '-')
            .lowercase()
            .ifBlank { "en" }
    }

    private fun candidateLocales(locale: String): List<String> {
        val normalized = normalizeLocale(locale)
        val base = normalized.substringBefore('-')

        return listOf(normalized, base).distinct()
    }

    private fun applyReplacements(
        template: String,
        replacements: Map<String, Any?>
    ): String {
        var translated = template

        replacements.forEach { (key, value) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isBlank()) {
                return@forEach
            }

            val replacement = value?.toString().orEmpty()
            val capitalizedReplacement = replacement.replaceFirstChar { it.uppercase() }

            translated = translated
                .replace(":$normalizedKey", replacement)
                .replace(":${normalizedKey.replaceFirstChar { it.uppercase() }}", capitalizedReplacement)
                .replace(":${normalizedKey.uppercase()}", replacement.uppercase())
        }

        return translated
    }
}
