package kernel.foundation.system

import java.nio.charset.Charset
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

typealias LocaleChangeListener = (oldLocale: SystemLocaleData, newLocale: SystemLocaleData) -> Unit

/**
 * Provider estático para obtener información regional y de idioma del sistema.
 * Observa automáticamente cambios en el sistema operativo en segundo plano.
 */
object SystemLocale {
    const val LANGUAGE = "language"
    const val COUNTRY = "country"
    const val LOCALE = "locale"
    const val DISPLAY_LANGUAGE = "display_language"
    const val DISPLAY_COUNTRY = "display_country"
    const val TIMEZONE = "timezone"
    const val CHARSET = "charset"

    private var cachedData: SystemLocaleData? = null
    private val listeners = CopyOnWriteArrayList<LocaleChangeListener>()
    private var monitorStarted = false

    /**
     * Devuelve toda la información en formato de data class.
     */
    @Synchronized
    fun get(): SystemLocaleData {
        if (cachedData == null) {
            cachedData = collectData()
        }
        startMonitorIfNeeded()
        return cachedData!!
    }

    /**
     * Registra un listener que se ejecuta cuando cambian las preferencias regionales.
     */
    fun addListener(listener: LocaleChangeListener) {
        listeners.add(listener)
        startMonitorIfNeeded()
    }

    /**
     * Alias semántico para addListener.
     */
    fun onChange(listener: LocaleChangeListener) {
        addListener(listener)
    }

    /**
     * Obtiene un valor específico por medio de las constantes de la clase.
     */
    fun get(key: String): String {
        val data = get()
        return when (key) {
            LANGUAGE -> data.language
            COUNTRY -> data.country
            LOCALE -> data.locale
            DISPLAY_LANGUAGE -> data.displayLanguage
            DISPLAY_COUNTRY -> data.displayCountry
            TIMEZONE -> data.timezone
            CHARSET -> data.charset
            else -> ""
        }
    }

    fun getLanguage(): String = get(LANGUAGE)
    fun getCountry(): String = get(COUNTRY)
    fun getLocale(): String = get(LOCALE)
    fun getDisplayLanguage(): String = get(DISPLAY_LANGUAGE)
    fun getDisplayCountry(): String = get(DISPLAY_COUNTRY)
    fun getTimezone(): String = get(TIMEZONE)
    fun getCharset(): String = get(CHARSET)

    /**
     * Exporta los datos a un Map plano.
     */
    fun toMap(): Map<String, String> {
        val data = get()
        return mapOf(
            LANGUAGE to data.language,
            COUNTRY to data.country,
            LOCALE to data.locale,
            DISPLAY_LANGUAGE to data.displayLanguage,
            DISPLAY_COUNTRY to data.displayCountry,
            TIMEZONE to data.timezone,
            CHARSET to data.charset
        )
    }

    /**
     * Exporta los datos a formato JSON estricto sin dependencias externas.
     */
    fun toJson(): String {
        val entries = toMap().entries.joinToString(",\n") { (key, value) ->
            "  \"$key\": \"${escapeJson(value)}\""
        }
        return "{\n$entries\n}"
    }

    private fun escapeJson(string: String): String {
        return string
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun collectData(): SystemLocaleData {
        val defaultLocale = Locale.getDefault()
        
        return SystemLocaleData(
            language = defaultLocale.language.safe(),
            country = defaultLocale.country.safe(),
            locale = defaultLocale.toString().safe(),
            displayLanguage = defaultLocale.displayLanguage.safe(),
            displayCountry = defaultLocale.displayCountry.safe(),
            timezone = TimeZone.getDefault().id.safe(),
            charset = Charset.defaultCharset().name().safe()
        )
    }

    @Synchronized
    private fun startMonitorIfNeeded() {
        if (monitorStarted) return
        monitorStarted = true

        Thread.ofVirtual().name("system-locale-monitor").start {
            while (true) {
                try {
                    Thread.sleep(5000)
                    checkAndNotify()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // Prevenir caída del monitor si falla la verificación
                }
            }
        }
    }

    @Synchronized
    private fun checkAndNotify() {
        val oldData = cachedData ?: return
        val newData = collectData()

        if (oldData != newData) {
            cachedData = newData
            val currentListeners = listeners.toList()
            
            if (currentListeners.isNotEmpty()) {
                Thread.ofVirtual().name("system-locale-notifier").start {
                    currentListeners.forEach { listener ->
                        try {
                            listener(oldData, newData)
                        } catch (e: Exception) {
                            // Aislar errores de los listeners
                        }
                    }
                }
            }
        }
    }

    private fun String?.safe(): String {
        return this?.trim() ?: ""
    }
}

